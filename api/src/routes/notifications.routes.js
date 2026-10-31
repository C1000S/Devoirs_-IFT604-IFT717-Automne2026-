const { Router } = require("express");
const Notification = require("../models/Notification");
const { requireAuth } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

// Chaque personne consulte ses propres notifications ; l'administration peut
// consulter celles de n'importe qui via ?userId=...
router.get("/", requireAuth, async (req, res) => {
  const filter = {};
  if (req.user.role === ROLES.ADMIN && req.query.userId) {
    filter.user = req.query.userId;
  } else {
    filter.user = req.user._id;
  }
  const notifications = await Notification.find(filter).sort({ createdAt: -1 });
  res.json(notifications);
});

router.patch("/:id/read", requireAuth, async (req, res) => {
  const notification = await Notification.findById(req.params.id);
  if (!notification) return res.status(404).json({ error: "Notification introuvable." });
  if (notification.user.toString() !== req.user._id.toString() && req.user.role !== ROLES.ADMIN) {
    return res.status(403).json({ error: "Accès refusé." });
  }
  notification.read = true;
  await notification.save();
  res.json(notification);
});

module.exports = router;
