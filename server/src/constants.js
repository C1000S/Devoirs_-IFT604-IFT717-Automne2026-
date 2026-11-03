// Rôles des comptes. Les opérations sensibles sont contrôlées côté serveur selon ces rôles.
const ROLES = Object.freeze({
  REQUESTER: "requester", // Demandeur / demandeuse
  REPAIRER: "repairer", // Réparateur / réparatrice
  COORDINATOR: "coordinator", // Coordonnateur / coordonnatrice
  ADMIN: "admin", // Administrateur / administratrice
});

// Cycle de vie d'une demande de réparation.
const REQUEST_STATUSES = Object.freeze({
  TO_TRIAGE: "to_triage", // À trier
  ASSIGNED: "assigned", // Assignée
  IN_DIAGNOSIS: "in_diagnosis", // En diagnostic
  WAITING: "waiting", // En attente (approbation, pièce ou réponse du demandeur)
  IN_REPAIR: "in_repair", // En réparation
  READY_FOR_PICKUP: "ready_for_pickup", // Prête à récupérer
  CLOSED: "closed", // Fermée
  CANCELLED: "cancelled", // Annulée
  NOT_REPAIRABLE: "not_repairable", // Non réparable
});

// Transitions autorisées du graphe d'états. Toute transition hors de cette table est refusée.
const STATUS_TRANSITIONS = Object.freeze({
  [REQUEST_STATUSES.TO_TRIAGE]: [REQUEST_STATUSES.ASSIGNED, REQUEST_STATUSES.CANCELLED],
  [REQUEST_STATUSES.ASSIGNED]: [
    REQUEST_STATUSES.IN_DIAGNOSIS,
    REQUEST_STATUSES.TO_TRIAGE, // réaffectation : retour au tri
    REQUEST_STATUSES.CANCELLED,
  ],
  [REQUEST_STATUSES.IN_DIAGNOSIS]: [
    REQUEST_STATUSES.WAITING,
    REQUEST_STATUSES.IN_REPAIR,
    REQUEST_STATUSES.NOT_REPAIRABLE,
    REQUEST_STATUSES.CANCELLED,
  ],
  [REQUEST_STATUSES.WAITING]: [
    REQUEST_STATUSES.IN_DIAGNOSIS,
    REQUEST_STATUSES.IN_REPAIR,
    REQUEST_STATUSES.NOT_REPAIRABLE,
    REQUEST_STATUSES.CANCELLED,
  ],
  [REQUEST_STATUSES.IN_REPAIR]: [
    REQUEST_STATUSES.WAITING,
    REQUEST_STATUSES.READY_FOR_PICKUP,
    REQUEST_STATUSES.NOT_REPAIRABLE,
  ],
  [REQUEST_STATUSES.READY_FOR_PICKUP]: [REQUEST_STATUSES.CLOSED, REQUEST_STATUSES.IN_REPAIR],
  [REQUEST_STATUSES.NOT_REPAIRABLE]: [REQUEST_STATUSES.CLOSED],
  [REQUEST_STATUSES.CLOSED]: [],
  [REQUEST_STATUSES.CANCELLED]: [],
});

// États terminaux : la demande reste dans l'historique mais n'évolue plus.
const TERMINAL_STATUSES = Object.freeze([
  REQUEST_STATUSES.CLOSED,
  REQUEST_STATUSES.CANCELLED,
]);

const PRIORITIES = Object.freeze(["low", "normal", "high"]);

const ESTIMATE_STATUSES = Object.freeze({
  PENDING: "pending",
  ACCEPTED: "accepted",
  REFUSED: "refused",
});

// Types d'événements inscrits dans l'historique d'une demande.
const EVENT_TYPES = Object.freeze({
  CREATED: "request.created",
  UPDATED: "request.updated",
  ASSIGNED: "request.assigned",
  STATUS_CHANGED: "request.status_changed",
  DIAGNOSIS_ADDED: "request.diagnosis_added",
  ESTIMATE_ADDED: "request.estimate_added",
  ESTIMATE_ANSWERED: "request.estimate_answered",
  INTERVENTION_ADDED: "request.intervention_added",
  PART_ADDED: "request.part_added",
  COMMENT_ADDED: "request.comment_added",
});

// Types de notifications présentées aux personnes.
const NOTIFICATION_TYPES = Object.freeze({
  ASSIGNED: "assigned",
  STATUS_CHANGED: "status_changed",
  ESTIMATE_READY: "estimate_ready",
  ESTIMATE_ANSWERED: "estimate_answered",
  READY_FOR_PICKUP: "ready_for_pickup",
  REMINDER: "reminder",
});

module.exports = {
  ROLES,
  REQUEST_STATUSES,
  STATUS_TRANSITIONS,
  TERMINAL_STATUSES,
  PRIORITIES,
  ESTIMATE_STATUSES,
  EVENT_TYPES,
  NOTIFICATION_TYPES,
};
