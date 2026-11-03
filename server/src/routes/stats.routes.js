const express = require("express");
const RepairRequest = require("../models/RepairRequest");
const asyncHandler = require("../utils/asyncHandler");
const { authenticate, authorize } = require("../middleware/auth");
const { ROLES, REQUEST_STATUSES, TERMINAL_STATUSES } = require("../constants");

const router = express.Router();
router.use(authenticate, authorize(ROLES.COORDINATOR, ROLES.ADMIN));

// GET /api/stats/overview — agrégats pour les visualisations graphiques.
// Fournit les données que le client (Web/Android) transformera en graphiques.
router.get(
  "/overview",
  asyncHandler(async (_req, res) => {
    const byStatus = await RepairRequest.aggregate([
      { $group: { _id: "$status", count: { $sum: 1 } } },
      { $sort: { count: -1 } },
    ]);

    const byCategory = await RepairRequest.aggregate([
      { $group: { _id: "$category", count: { $sum: 1 } } },
      {
        $lookup: {
          from: "categories",
          localField: "_id",
          foreignField: "_id",
          as: "category",
        },
      },
      { $unwind: { path: "$category", preserveNullAndEmptyArrays: true } },
      { $project: { _id: 0, category: "$category.name", slug: "$category.slug", count: 1 } },
      { $sort: { count: -1 } },
    ]);

    const byPriority = await RepairRequest.aggregate([
      { $group: { _id: "$priority", count: { $sum: 1 } } },
    ]);

    // Demandes créées par jour (30 derniers jours).
    const since = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const byDay = await RepairRequest.aggregate([
      { $match: { createdAt: { $gte: since } } },
      {
        $group: {
          _id: { $dateToString: { format: "%Y-%m-%d", date: "$createdAt" } },
          count: { $sum: 1 },
        },
      },
      { $sort: { _id: 1 } },
    ]);

    // Appareils (types) les plus fréquents.
    const topDevices = await RepairRequest.aggregate([
      { $group: { _id: "$device.type", count: { $sum: 1 } } },
      { $sort: { count: -1 } },
      { $limit: 10 },
      { $project: { _id: 0, type: "$_id", count: 1 } },
    ]);

    // Taux de réparation : fermées (réparées) vs total terminé.
    const total = await RepairRequest.countDocuments();
    const closed = await RepairRequest.countDocuments({ status: REQUEST_STATUSES.CLOSED });
    const notRepairable = await RepairRequest.countDocuments({
      status: REQUEST_STATUSES.NOT_REPAIRABLE,
    });
    const cancelled = await RepairRequest.countDocuments({ status: REQUEST_STATUSES.CANCELLED });
    const finished = closed + notRepairable + cancelled;
    const repairRate = finished > 0 ? closed / finished : 0;

    // Délai moyen de traitement (création → dernière activité) pour les demandes terminées.
    const delayAgg = await RepairRequest.aggregate([
      { $match: { status: { $in: TERMINAL_STATUSES } } },
      {
        $project: {
          durationMs: { $subtract: ["$lastActivityAt", "$createdAt"] },
        },
      },
      { $group: { _id: null, avgMs: { $avg: "$durationMs" } } },
    ]);
    const avgResolutionDays = delayAgg.length ? delayAgg[0].avgMs / (24 * 60 * 60 * 1000) : 0;

    res.json({
      total,
      byStatus: byStatus.map((s) => ({ status: s._id, count: s.count })),
      byCategory,
      byPriority: byPriority.map((p) => ({ priority: p._id, count: p.count })),
      byDay: byDay.map((d) => ({ date: d._id, count: d.count })),
      topDevices,
      repairRate,
      avgResolutionDays,
    });
  })
);

module.exports = router;
