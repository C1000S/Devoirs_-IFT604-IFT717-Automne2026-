const RepairRequest = require("../models/RepairRequest");
const RepairEvent = require("../models/RepairEvent");
const { createNotification } = require("./notifications");
const { realtime } = require("./realtime");
const ApiError = require("../utils/ApiError");
const {
  REQUEST_STATUSES,
  STATUS_TRANSITIONS,
  TERMINAL_STATUSES,
  ESTIMATE_STATUSES,
  EVENT_TYPES,
  NOTIFICATION_TYPES,
} = require("../constants");

// Personnes directement concernées par une demande (pour le ciblage temps réel).
// Les coordonnateurs et administrateurs reçoivent tout via RealtimeBus.shouldReceive.
function requestAudience(request) {
  return [request.requester, request.assignedTo].filter(Boolean);
}

// Inscrit une entrée d'historique, met à jour l'activité et pousse l'événement temps réel.
async function recordEvent(request, type, message, actor, meta = {}) {
  const event = await RepairEvent.create({
    request: request._id,
    type,
    message,
    actor: actor ? actor.id : null,
    meta,
  });
  request.lastActivityAt = new Date();
  realtime.publish("repair-event", event.toJSON(), requestAudience(request));
  return event;
}

// Pousse l'état courant de la demande aux clients temps réel concernés.
function publishRequestUpdate(request) {
  realtime.publish("repair-request", request.toJSON(), requestAudience(request));
}

// Vérifie qu'une transition d'état est permise par le graphe du cycle de vie.
function assertTransitionAllowed(from, to) {
  if (from === to) {
    throw ApiError.badRequest(`La demande est déjà à l'état « ${to} ».`);
  }
  const allowed = STATUS_TRANSITIONS[from] || [];
  if (!allowed.includes(to)) {
    throw ApiError.conflict(
      `Transition d'état invalide : « ${from} » → « ${to} ».`,
      { from, to, allowed }
    );
  }
}

// Affecte (ou réaffecte) une demande à une personne réparatrice.
async function assignRequest(request, repairer, actor, { slot = null } = {}) {
  request.assignedTo = repairer.id;
  if (slot) request.slot = slot;
  if (request.status === REQUEST_STATUSES.TO_TRIAGE) {
    request.status = REQUEST_STATUSES.ASSIGNED;
  }
  await request.save();

  await recordEvent(
    request,
    EVENT_TYPES.ASSIGNED,
    `Demande assignée à ${repairer.name}.`,
    actor,
    { repairerId: repairer.id.toString() }
  );
  await createNotification({
    user: repairer.id,
    request: request._id,
    type: NOTIFICATION_TYPES.ASSIGNED,
    title: "Nouvelle demande assignée",
    message: `La demande « ${request.device.type} » vous a été assignée.`,
  });
  publishRequestUpdate(request);
  return request;
}

// Change l'état d'une demande en respectant le graphe de transitions.
async function changeStatus(request, newStatus, actor, { reason = null } = {}) {
  const previous = request.status;
  assertTransitionAllowed(previous, newStatus);

  request.status = newStatus;
  if (TERMINAL_STATUSES.includes(newStatus) || newStatus === REQUEST_STATUSES.NOT_REPAIRABLE) {
    request.closureReason = reason || request.closureReason;
  }
  await request.save();

  await recordEvent(
    request,
    EVENT_TYPES.STATUS_CHANGED,
    `État modifié : « ${previous} » → « ${newStatus} »${reason ? ` (${reason})` : ""}.`,
    actor,
    { from: previous, to: newStatus, reason }
  );

  // Notifier le demandeur des changements d'état.
  const isPickup = newStatus === REQUEST_STATUSES.READY_FOR_PICKUP;
  await createNotification({
    user: request.requester,
    request: request._id,
    type: isPickup ? NOTIFICATION_TYPES.READY_FOR_PICKUP : NOTIFICATION_TYPES.STATUS_CHANGED,
    title: isPickup ? "Objet prêt à récupérer" : "État de votre demande mis à jour",
    message: isPickup
      ? `Votre « ${request.device.type} » est prêt à être récupéré.`
      : `Votre demande est maintenant à l'état « ${newStatus} ».`,
  });

  publishRequestUpdate(request);
  return request;
}

// Consigne un diagnostic. Fait passer une demande assignée en diagnostic.
async function addDiagnosis(request, { notes }, actor) {
  request.diagnosis = { notes, createdBy: actor.id, createdAt: new Date() };
  if (request.status === REQUEST_STATUSES.ASSIGNED) {
    request.status = REQUEST_STATUSES.IN_DIAGNOSIS;
  }
  await request.save();

  await recordEvent(request, EVENT_TYPES.DIAGNOSIS_ADDED, "Diagnostic consigné.", actor);
  await createNotification({
    user: request.requester,
    request: request._id,
    type: NOTIFICATION_TYPES.STATUS_CHANGED,
    title: "Diagnostic disponible",
    message: "Un diagnostic a été consigné pour votre demande.",
  });
  publishRequestUpdate(request);
  return request;
}

// Ajoute une estimation en attente de réponse et passe la demande en attente.
async function addEstimate(request, { amount, currency, details }, actor) {
  request.estimate = {
    amount,
    currency: currency || "CAD",
    details: details || "",
    status: ESTIMATE_STATUSES.PENDING,
    createdBy: actor.id,
    createdAt: new Date(),
    respondedAt: null,
  };
  // L'application attend désormais une réponse du demandeur.
  if (
    request.status === REQUEST_STATUSES.IN_DIAGNOSIS ||
    request.status === REQUEST_STATUSES.ASSIGNED
  ) {
    request.status = REQUEST_STATUSES.WAITING;
  }
  await request.save();

  await recordEvent(
    request,
    EVENT_TYPES.ESTIMATE_ADDED,
    `Estimation soumise : ${amount} ${request.estimate.currency}.`,
    actor
  );
  await createNotification({
    user: request.requester,
    request: request._id,
    type: NOTIFICATION_TYPES.ESTIMATE_READY,
    title: "Estimation à approuver",
    message: `Une estimation de ${amount} ${request.estimate.currency} attend votre réponse.`,
  });
  publishRequestUpdate(request);
  return request;
}

// Réponse du demandeur à une estimation : accepted | refused.
async function answerEstimate(request, decision, actor) {
  if (!request.estimate) {
    throw ApiError.badRequest("Aucune estimation à approuver pour cette demande.");
  }
  if (request.estimate.status !== ESTIMATE_STATUSES.PENDING) {
    throw ApiError.conflict("Cette estimation a déjà reçu une réponse.");
  }
  if (![ESTIMATE_STATUSES.ACCEPTED, ESTIMATE_STATUSES.REFUSED].includes(decision)) {
    throw ApiError.badRequest("La décision doit être « accepted » ou « refused ».");
  }

  request.estimate.status = decision;
  request.estimate.respondedAt = new Date();

  // Acceptée → on peut réparer; refusée → la demande n'est pas réparée (clôturée).
  if (decision === ESTIMATE_STATUSES.ACCEPTED) {
    request.status = REQUEST_STATUSES.IN_REPAIR;
  } else {
    request.status = REQUEST_STATUSES.CLOSED;
    request.closureReason = "Estimation refusée par le demandeur.";
  }
  await request.save();

  await recordEvent(
    request,
    EVENT_TYPES.ESTIMATE_ANSWERED,
    `Estimation ${decision === ESTIMATE_STATUSES.ACCEPTED ? "acceptée" : "refusée"} par le demandeur.`,
    actor
  );
  // Prévenir la personne réparatrice assignée.
  if (request.assignedTo) {
    await createNotification({
      user: request.assignedTo,
      request: request._id,
      type: NOTIFICATION_TYPES.ESTIMATE_ANSWERED,
      title: "Réponse à l'estimation",
      message: `Le demandeur a ${decision === ESTIMATE_STATUSES.ACCEPTED ? "accepté" : "refusé"} l'estimation.`,
    });
  }
  publishRequestUpdate(request);
  return request;
}

// Ajoute une intervention réalisée.
async function addIntervention(request, data, actor) {
  request.interventions.push({
    notes: data.notes,
    minutesSpent: data.minutesSpent || 0,
    performedBy: actor.id,
    performedAt: new Date(),
  });
  if (request.status === REQUEST_STATUSES.WAITING) {
    request.status = REQUEST_STATUSES.IN_REPAIR;
  }
  await request.save();
  await recordEvent(request, EVENT_TYPES.INTERVENTION_ADDED, "Intervention consignée.", actor);
  publishRequestUpdate(request);
  return request;
}

// Ajoute une pièce nécessaire.
async function addPart(request, data, actor) {
  request.parts.push({
    name: data.name,
    quantity: data.quantity || 1,
    unitCost: data.unitCost || 0,
    status: data.status || "needed",
    addedBy: actor.id,
    addedAt: new Date(),
  });
  await request.save();
  await recordEvent(request, EVENT_TYPES.PART_ADDED, `Pièce ajoutée : ${data.name}.`, actor);
  publishRequestUpdate(request);
  return request;
}

module.exports = {
  requestAudience,
  recordEvent,
  publishRequestUpdate,
  assertTransitionAllowed,
  assignRequest,
  changeStatus,
  addDiagnosis,
  addEstimate,
  answerEstimate,
  addIntervention,
  addPart,
};
