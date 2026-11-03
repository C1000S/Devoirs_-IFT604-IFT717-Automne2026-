const { EventEmitter } = require("node:events");
const { ROLES } = require("../constants");

// Bus temps réel : les routes publient des événements, le flux SSE (/api/events)
// les relaie aux clients connectés, filtrés selon les droits de chaque personne.
class RealtimeBus extends EventEmitter {
  constructor() {
    super();
    this.setMaxListeners(0); // nombre de clients SSE non borné
  }

  // Publie un événement destiné à un ensemble de personnes.
  // audience : tableau d'identifiants d'utilisateurs autorisés à recevoir l'événement.
  // Les coordonnateurs et administrateurs reçoivent tout (voir shouldReceive).
  publish(type, payload, audience = []) {
    const envelope = {
      type,
      payload,
      audience: audience.map((id) => id && id.toString()).filter(Boolean),
      at: new Date().toISOString(),
    };
    this.emit("event", envelope);
  }

  // Détermine si un utilisateur connecté doit recevoir un événement donné.
  static shouldReceive(user, envelope) {
    if (!user) return false;
    if (user.role === ROLES.ADMIN || user.role === ROLES.COORDINATOR) return true;
    return envelope.audience.includes(user.id.toString());
  }
}

const realtime = new RealtimeBus();

module.exports = { realtime, RealtimeBus };
