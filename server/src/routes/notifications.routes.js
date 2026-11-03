const express = require("express");
const Notification = require("../models/Notification");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { authenticate } = require("../middleware/auth");

const router = express.Router();
router.use(authenticate);

// GET /api/notifications — notifications de la personne connectée.
// Filtre : ?unread=true pour ne garder que les non lues.
router.get(
  "/",
  asyncHandler(async (req, res) => {
    const filter = { user: req.user.id };
    if (req.query.unread === "true") filter.read = false;
    const notifications = await Notification.find(filter).sort({ createdAt: -1 }).limit(100);
    const unreadCount = await Notification.countDocuments({ user: req.user.id, read: false });
    res.json({ items: notifications.map((n) => n.toJSON()), unreadCount });
  })
);

// PATCH /api/notifications/:id/read — marquer une notification comme lue.
router.patch(
  "/:id/read",
  asyncHandler(async (req, res) => {
    const notification = await Notification.findOne({ _id: req.params.id, user: req.user.id });
    if (!notification) throw ApiError.notFound("Notification introuvable.");
    notification.read = true;
    await notification.save();
    res.json(notification.toJSON());
  })
);

// POST /api/notifications/read-all — tout marquer comme lu.
router.post(
  "/read-all",
  asyncHandler(async (req, res) => {
    const result = await Notification.updateMany(
      { user: req.user.id, read: false },
      { read: true }
    );
    res.json({ updated: result.modifiedCount });
  })
);

module.exports = router;
