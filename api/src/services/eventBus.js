const { EventEmitter } = require("node:events");

// Bus d'événements partagé : relie les mutations de données (routes, jobs)
// au flux SSE (/api/events) sans dépendance circulaire.
class Bus extends EventEmitter {}

module.exports = new Bus();
