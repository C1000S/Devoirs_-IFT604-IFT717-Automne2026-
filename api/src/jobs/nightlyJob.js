const cron = require("node-cron");
const RepairRequest = require("../models/RepairRequest");
const { REQUEST_STATUSES } = require("../constants");
const { addEvent, notify } = require("../services/notificationService");

const READY_REMINDER_DAYS = 7;
const INACTIVITY_DAYS = 5;

// Traitement quotidien : rappels de récupération, demandes inactives et estimations sans réponse.
// Exporté séparément du planificateur pour être réutilisable par la route de démonstration.
async function runNightlyBatch() {
  const now = Date.now();
  const readyCutoff = new Date(now - READY_REMINDER_DAYS * 24 * 60 * 60 * 1000);
  const inactivityCutoff = new Date(now - INACTIVITY_DAYS * 24 * 60 * 60 * 1000);

  const result = { ranAt: new Date(), pickupReminders: 0, inactivityReminders: 0, estimateReminders: 0 };

  const readyRequests = await RepairRequest.find({ status: REQUEST_STATUSES.READY_FOR_PICKUP, updatedAt: { $lte: readyCutoff } });
  for (const request of readyRequests) {
    await notify({ userId: request.requester, title: "Rappel de récupération", message: "Votre objet est prêt à récupérer depuis plus de sept jours.", requestId: request._id });
    result.pickupReminders += 1;
  }

  const staleRequests = await RepairRequest.find({
    status: { $in: [REQUEST_STATUSES.IN_DIAGNOSIS, REQUEST_STATUSES.ASSIGNED] },
    updatedAt: { $lte: inactivityCutoff },
  });
  for (const request of staleRequests) {
    await addEvent({ requestId: request._id, type: "request.inactive", message: "Demande inactive depuis plusieurs jours.", actorId: null });
    if (request.assignedTo) {
      await notify({ userId: request.assignedTo, title: "Demande inactive", message: "Une demande qui vous est affectée est inactive depuis plusieurs jours.", requestId: request._id });
    }
    result.inactivityReminders += 1;
  }

  const waitingEstimates = await RepairRequest.find({ status: REQUEST_STATUSES.WAITING, "estimate.status": "pending", updatedAt: { $lte: inactivityCutoff } });
  for (const request of waitingEstimates) {
    await notify({ userId: request.requester, title: "Estimation sans réponse", message: "Une estimation attend toujours votre réponse.", requestId: request._id });
    result.estimateReminders += 1;
  }

  return result;
}

function scheduleNightlyJob() {
  // Chaque soir à 23h, heure du serveur.
  cron.schedule("0 23 * * *", () => {
    runNightlyBatch().catch((err) => console.error("Erreur du traitement quotidien :", err));
  });
}

module.exports = { runNightlyBatch, scheduleNightlyJob };
