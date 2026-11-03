const express = require("express");
const { authenticate } = require("../middleware/auth");
const { realtime, RealtimeBus } = require("../services/realtime");

const router = express.Router();

// GET /api/events — flux Server-Sent Events (SSE).
// Authentifié (en-tête Authorization ou ?token=...). Chaque client ne reçoit que
// les événements auxquels son rôle et ses droits lui donnent accès.
router.get("/", authenticate, (req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });
  res.flushHeaders?.();

  // Message de bienvenue.
  res.write(`event: connected\ndata: ${JSON.stringify({ message: "Flux RépareCampus connecté", userId: req.user.id })}\n\n`);

  // Relaie les événements du bus vers ce client, après filtrage.
  const onEvent = (envelope) => {
    if (!RealtimeBus.shouldReceive(req.user, envelope)) return;
    res.write(`event: ${envelope.type}\ndata: ${JSON.stringify(envelope.payload)}\n\n`);
  };
  realtime.on("event", onEvent);

  // Battement périodique pour garder la connexion ouverte (commentaire SSE).
  const heartbeat = setInterval(() => res.write(": ping\n\n"), 25000);

  req.on("close", () => {
    clearInterval(heartbeat);
    realtime.off("event", onEvent);
    res.end();
  });
});

module.exports = router;
