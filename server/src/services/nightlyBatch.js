const RepairRequest = require("../models/RepairRequest");
const { createNotification } = require("./notifications");
const { recordEvent } = require("./requests");
const {
  REQUEST_STATUSES,
  TERMINAL_STATUSES,
  ESTIMATE_STATUSES,
  EVENT_TYPES,
  NOTIFICATION_TYPES,
} = require("../constants");

const DAY_MS = 24 * 60 * 60 * 1000;

// Seuils (en jours) configurables si besoin.
const INACTIVE_DAYS = 5; // demande non terminale sans activité
const ESTIMATE_PENDING_DAYS = 3; // estimation sans réponse
const PICKUP_DAYS = 7; // objet prêt à récupérer non repris

// Traitement quotidien : repère les demandes inactives, les estimations sans réponse
// et les objets prêts depuis trop longtemps, puis génère les notifications utiles.
// Idempotent au sens où il peut être relancé sans corrompre les données.
async function runNightlyBatch({ now = new Date() } = {}) {
  const ranAt = now;
  const summary = {
    ranAt,
    inactiveReminders: 0,
    estimateReminders: 0,
    pickupReminders: 0,
    notificationsCreated: 0,
  };

  const requests = await RepairRequest.find({
    status: { $nin: TERMINAL_STATUSES },
  });

  for (const request of requests) {
    const lastActivity = new Date(request.lastActivityAt || request.updatedAt).getTime();
    const ageDays = (ranAt.getTime() - lastActivity) / DAY_MS;

    // 1) Objets prêts à récupérer depuis trop longtemps.
    if (request.status === REQUEST_STATUSES.READY_FOR_PICKUP && ageDays >= PICKUP_DAYS) {
      await createNotification({
        user: request.requester,
        request: request._id,
        type: NOTIFICATION_TYPES.REMINDER,
        title: "Rappel de récupération",
        message: `Votre « ${request.device.type} » est prêt depuis plus de ${PICKUP_DAYS} jours.`,
      });
      await recordEvent(request, EVENT_TYPES.UPDATED, "Rappel de récupération généré (traitement nocturne).", null);
      await request.save();
      summary.pickupReminders += 1;
      summary.notificationsCreated += 1;
      continue;
    }

    // 2) Estimation en attente de réponse depuis trop longtemps.
    if (
      request.estimate &&
      request.estimate.status === ESTIMATE_STATUSES.PENDING &&
      (ranAt.getTime() - new Date(request.estimate.createdAt).getTime()) / DAY_MS >= ESTIMATE_PENDING_DAYS
    ) {
      await createNotification({
        user: request.requester,
        request: request._id,
        type: NOTIFICATION_TYPES.REMINDER,
        title: "Estimation en attente de réponse",
        message: "Une estimation attend votre approbation depuis plusieurs jours.",
      });
      summary.estimateReminders += 1;
      summary.notificationsCreated += 1;
      continue;
    }

    // 3) Demande inactive : prévenir la personne réparatrice (ou la coordination).
    if (ageDays >= INACTIVE_DAYS) {
      const target = request.assignedTo || request.requester;
      await createNotification({
        user: target,
        request: request._id,
        type: NOTIFICATION_TYPES.REMINDER,
        title: "Demande inactive",
        message: `Aucune activité depuis ${Math.floor(ageDays)} jours sur la demande « ${request.device.type} ».`,
      });
      summary.inactiveReminders += 1;
      summary.notificationsCreated += 1;
    }
  }

  return summary;
}

module.exports = { runNightlyBatch, INACTIVE_DAYS, ESTIMATE_PENDING_DAYS, PICKUP_DAYS };
