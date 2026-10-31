const { Router } = require("express");
const { requireAuth } = require("../middleware/auth");
const { bus } = require("../services/notificationService");
const RepairRequest = require("../models/RepairRequest");
const { ROLES } = require("../constants");

const router = Router();

// Flux SSE temps réel, filtré selon le rôle de la personne connectée.
// EventSource ne permet pas d'en-têtes personnalisés : le jeton est transmis en requête (?token=...).
router.get("/", requireAuth, async (req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
  res.write(`event: connected\ndata: ${JSON.stringify({ message: "Flux RépareCampus connecté" })}\n\n`);

  const send = (type, payload) => res.write(`event: ${type}\ndata: ${JSON.stringify(payload)}\n\n`);

  const isAuthorizedForRequest = async (request) => {
    if ([ROLES.ADMIN, ROLES.COORDINATOR].includes(req.user.role)) return true;
    if (req.user.role === ROLES.REQUESTER) return request.requester?.toString() === req.user._id.toString();
    if (req.user.role === ROLES.REPAIRER) return !request.assignedTo || request.assignedTo?.toString() === req.user._id.toString();
    return false;
  };

  const onRequestEvent = async (payload) => {
    if (await isAuthorizedForRequest(payload)) send("repair-request", payload);
  };
  const onNotification = (payload) => {
    if (payload.user?.toString() === req.user._id.toString()) send("notification", payload);
  };

  bus.on("repair-request.created", onRequestEvent);
  bus.on("repair-request.updated", onRequestEvent);
  bus.on("notification.created", onNotification);

  const heartbeat = setInterval(() => res.write(": heartbeat\n\n"), 25000);

  req.on("close", () => {
    clearInterval(heartbeat);
    bus.off("repair-request.created", onRequestEvent);
    bus.off("repair-request.updated", onRequestEvent);
    bus.off("notification.created", onNotification);
    res.end();
  });
});

module.exports = router;
