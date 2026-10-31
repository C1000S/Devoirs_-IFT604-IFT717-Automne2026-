const { Router } = require("express");
const RepairRequest = require("../models/RepairRequest");
const RepairEvent = require("../models/RepairEvent");
const Slot = require("../models/Slot");
const Part = require("../models/Part");
const { requireAuth, requireRole } = require("../middleware/auth");
const { addEvent, notify } = require("../services/notificationService");
const { bus } = require("../services/notificationService");
const { ROLES, REQUEST_STATUSES, ALLOWED_TRANSITIONS, PRIORITIES } = require("../constants");

const router = Router();

// Une référence peut être un ObjectId ou un document peuplé (populate) : on compare toujours l'_id.
const refId = (ref) => (ref?._id ?? ref)?.toString();

function canView(user, request) {
  if ([ROLES.ADMIN, ROLES.COORDINATOR].includes(user.role)) return true;
  if (user.role === ROLES.REQUESTER) return refId(request.requester) === user._id.toString();
  if (user.role === ROLES.REPAIRER) {
    return !request.assignedTo || refId(request.assignedTo) === user._id.toString();
  }
  return false;
}

// GET /api/repair-requests - liste filtrée selon le rôle et les paramètres de recherche.
router.get("/", requireAuth, async (req, res) => {
  const { status, category, priority, assignedTo, requester, hasEstimate, from, to } = req.query;
  const filter = {};
  if (status) filter.status = status;
  if (category) filter.category = category;
  if (priority) filter.priority = priority;
  if (assignedTo) filter.assignedTo = assignedTo;
  if (requester) filter.requester = requester;
  if (hasEstimate === "true") filter["estimate.cost"] = { $exists: true };
  if (hasEstimate === "false") filter["estimate.cost"] = { $exists: false };
  if (from || to) {
    filter.createdAt = {};
    if (from) filter.createdAt.$gte = new Date(from);
    if (to) filter.createdAt.$lte = new Date(to);
  }

  // Restriction de visibilité selon le rôle connecté.
  if (req.user.role === ROLES.REQUESTER) {
    filter.requester = req.user._id;
  } else if (req.user.role === ROLES.REPAIRER) {
    filter.$or = [{ assignedTo: req.user._id }, { assignedTo: null }];
  }

  const requests = await RepairRequest.find(filter)
    .populate("requester", "name email")
    .populate("assignedTo", "name email skills")
    .sort({ createdAt: -1 });
  res.json(requests);
});

router.get("/:id", requireAuth, async (req, res) => {
  const request = await RepairRequest.findById(req.params.id)
    .populate("requester", "name email")
    .populate("assignedTo", "name email skills")
    .populate("interventions.performedBy", "name")
    .populate("diagnosis.partsNeeded.part")
    .populate("interventions.partsUsed.part");
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (!canView(req.user, request)) return res.status(403).json({ error: "Accès refusé à cette demande." });

  const events = await RepairEvent.find({ request: request._id }).populate("actor", "name role").sort({ createdAt: 1 });
  res.json({ ...request.toObject(), events });
});

router.post("/", requireAuth, requireRole(ROLES.REQUESTER, ROLES.ADMIN), async (req, res) => {
  const { category, device, problemDescription, photos, contactMethod, priority } = req.body;
  if (!category || !device?.type || !problemDescription) {
    return res.status(400).json({ error: "category, device.type et problemDescription sont requis." });
  }
  if (priority && !PRIORITIES.includes(priority)) return res.status(400).json({ error: "Priorité invalide." });

  const request = await RepairRequest.create({
    requester: req.user._id,
    category,
    device,
    problemDescription,
    photos: photos || [],
    contactMethod: contactMethod || "",
    priority: priority || "normal",
    status: REQUEST_STATUSES.TO_TRIAGE,
  });
  await addEvent({ requestId: request._id, type: "request.created", message: "Demande de réparation créée.", actorId: req.user._id });
  bus.emit("repair-request.created", request);
  res.status(201).json(request);
});

// Tri et affectation (coordination / administration).
router.patch("/:id/assign", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { assignedTo } = req.body;
  if (!assignedTo) return res.status(400).json({ error: "assignedTo est requis." });
  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });

  request.assignedTo = assignedTo;
  request.status = REQUEST_STATUSES.ASSIGNED;
  await request.save();
  await addEvent({ requestId: request._id, type: "request.assigned", message: "Demande affectée à une personne réparatrice.", actorId: req.user._id });
  await notify({ userId: assignedTo, title: "Nouvelle demande affectée", message: `Une demande vous a été affectée : ${request.problemDescription}`, requestId: request._id });
  bus.emit("repair-request.updated", request);
  res.json(request);
});

// Planification sur un créneau (coordination / administration) : vérifie la
// capacité du créneau et, le cas échéant, la disponibilité de la personne
// réparatrice déjà affectée à la demande.
router.patch("/:id/schedule", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { slotId } = req.body;
  if (!slotId) return res.status(400).json({ error: "slotId est requis." });

  const [request, slot] = await Promise.all([
    RepairRequest.findById(req.params.id),
    Slot.findById(slotId),
  ]);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (!slot) return res.status(404).json({ error: "Créneau introuvable." });

  if (request.assignedTo && slot.availableRepairers.length > 0) {
    const repairerAvailable = slot.availableRepairers.some((id) => id.toString() === request.assignedTo.toString());
    if (!repairerAvailable) {
      return res.status(400).json({ error: "La personne réparatrice assignée n'est pas disponible sur ce créneau." });
    }
  }

  const alreadyScheduled = await RepairRequest.countDocuments({ scheduledSlot: slot._id });
  if (alreadyScheduled >= slot.capacity) {
    return res.status(400).json({ error: "Ce créneau a atteint sa capacité maximale." });
  }

  request.scheduledSlot = slot._id;
  await request.save();
  await addEvent({ requestId: request._id, type: "request.scheduled", message: `Demande planifiée sur le créneau du ${slot.date.toISOString().slice(0, 10)} (${slot.startTime}-${slot.endTime}).`, actorId: req.user._id });
  bus.emit("repair-request.updated", request);
  res.json(request);
});

// Diagnostic (personne réparatrice assignée).
router.patch("/:id/diagnosis", requireAuth, requireRole(ROLES.REPAIRER), async (req, res) => {
  const { text, partsNeeded } = req.body;
  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (!request.assignedTo || request.assignedTo.toString() !== req.user._id.toString()) {
    return res.status(403).json({ error: "Seule la personne réparatrice assignée peut consigner un diagnostic." });
  }

  request.diagnosis = { text, partsNeeded: partsNeeded || [], createdAt: new Date() };
  if (request.status === REQUEST_STATUSES.ASSIGNED) request.status = REQUEST_STATUSES.IN_DIAGNOSIS;
  await request.save();
  await addEvent({ requestId: request._id, type: "request.diagnosis_added", message: "Diagnostic consigné.", actorId: req.user._id });
  bus.emit("repair-request.updated", request);
  res.json(request);
});

// Estimation proposée par la personne réparatrice -> attente d'une réponse du demandeur.
router.patch("/:id/estimate", requireAuth, requireRole(ROLES.REPAIRER), async (req, res) => {
  const { cost, description } = req.body;
  if (cost === undefined) return res.status(400).json({ error: "cost est requis." });
  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (!request.assignedTo || request.assignedTo.toString() !== req.user._id.toString()) {
    return res.status(403).json({ error: "Seule la personne réparatrice assignée peut proposer une estimation." });
  }

  request.estimate = { cost, description: description || "", status: "pending" };
  request.status = REQUEST_STATUSES.WAITING;
  await request.save();
  await addEvent({ requestId: request._id, type: "request.estimate_ready", message: `Estimation proposée : ${cost}$.`, actorId: req.user._id });
  await notify({ userId: request.requester, title: "Estimation disponible", message: "Une estimation est disponible pour votre demande.", requestId: request._id });
  bus.emit("repair-request.updated", request);
  res.json(request);
});

// Réponse du demandeur ou de la demandeuse à une estimation.
router.patch("/:id/estimate-response", requireAuth, requireRole(ROLES.REQUESTER), async (req, res) => {
  const { accept } = req.body;
  if (typeof accept !== "boolean") return res.status(400).json({ error: "accept (boolean) est requis." });
  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (request.requester.toString() !== req.user._id.toString()) {
    return res.status(403).json({ error: "Seule la personne demandeuse peut répondre à l'estimation." });
  }
  if (!request.estimate?.cost && request.estimate?.cost !== 0) {
    return res.status(400).json({ error: "Aucune estimation en attente." });
  }

  request.estimate.status = accept ? "accepted" : "refused";
  request.estimate.respondedAt = new Date();
  request.status = accept ? REQUEST_STATUSES.IN_REPAIR : REQUEST_STATUSES.NOT_REPAIRABLE;
  if (!accept) request.closedReason = "Estimation refusée par la personne demandeuse.";
  await request.save();
  await addEvent({
    requestId: request._id,
    type: "request.estimate_responded",
    message: accept ? "Estimation acceptée." : "Estimation refusée.",
    actorId: req.user._id,
  });
  if (request.assignedTo) {
    await notify({ userId: request.assignedTo, title: "Réponse à l'estimation", message: accept ? "Le client a accepté l'estimation." : "Le client a refusé l'estimation.", requestId: request._id });
  }
  bus.emit("repair-request.updated", request);
  res.json(request);
});

// Intervention réalisée (personne réparatrice assignée).
router.post("/:id/interventions", requireAuth, requireRole(ROLES.REPAIRER), async (req, res) => {
  const { description, partsUsed } = req.body;
  if (!description) return res.status(400).json({ error: "description est requise." });
  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (!request.assignedTo || request.assignedTo.toString() !== req.user._id.toString()) {
    return res.status(403).json({ error: "Seule la personne réparatrice assignée peut ajouter une intervention." });
  }

  const usedParts = partsUsed || [];

  // Vérifier le stock disponible avant de rien modifier.
  for (const usage of usedParts) {
    const part = await Part.findById(usage.part);
    if (!part) return res.status(404).json({ error: `Pièce introuvable : ${usage.part}` });
    const quantity = usage.quantity || 1;
    if (part.stock < quantity) {
      return res.status(400).json({ error: `Stock insuffisant pour la pièce ${part.name} (disponible : ${part.stock}, demandé : ${quantity}).` });
    }
  }
  // Déduire le stock utilisé.
  for (const usage of usedParts) {
    await Part.findByIdAndUpdate(usage.part, { $inc: { stock: -(usage.quantity || 1) } });
  }

  request.interventions.push({ description, partsUsed: usedParts, performedBy: req.user._id });
  await request.save();
  await addEvent({ requestId: request._id, type: "request.intervention_added", message: `Intervention consignée : ${description}`, actorId: req.user._id });
  bus.emit("repair-request.updated", request);
  res.status(201).json(request);
});

// Transition d'état générique (respecte la table des transitions autorisées).
router.patch("/:id/status", requireAuth, requireRole(ROLES.REPAIRER, ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { status, reason } = req.body;
  if (!Object.values(REQUEST_STATUSES).includes(status)) return res.status(400).json({ error: "État invalide." });

  const request = await RepairRequest.findById(req.params.id);
  if (!request) return res.status(404).json({ error: "Demande introuvable." });
  if (req.user.role === ROLES.REPAIRER && (!request.assignedTo || request.assignedTo.toString() !== req.user._id.toString())) {
    return res.status(403).json({ error: "Seule la personne réparatrice assignée peut modifier cette demande." });
  }

  const allowed = ALLOWED_TRANSITIONS[request.status] || [];
  if (!allowed.includes(status)) {
    return res.status(400).json({ error: `Transition non permise : ${request.status} -> ${status}.` });
  }

  request.status = status;
  if ([REQUEST_STATUSES.CLOSED, REQUEST_STATUSES.CANCELLED, REQUEST_STATUSES.NOT_REPAIRABLE].includes(status) && reason) {
    request.closedReason = reason;
  }
  await request.save();
  await addEvent({ requestId: request._id, type: "request.status_changed", message: `État modifié à ${status}.`, actorId: req.user._id });
  await notify({ userId: request.requester, title: "État de la demande modifié", message: `Votre demande est maintenant : ${status}.`, requestId: request._id });
  bus.emit("repair-request.updated", request);
  res.json(request);
});

module.exports = router;
