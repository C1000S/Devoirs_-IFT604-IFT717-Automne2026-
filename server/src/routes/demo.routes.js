const express = require("express");
const env = require("../config/env");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { authenticate, authorize } = require("../middleware/auth");
const { runNightlyBatch } = require("../services/nightlyBatch");
const { ROLES } = require("../constants");

const router = express.Router();

// Les routes de démonstration ne sont disponibles qu'en mode démonstration.
router.use((req, _res, next) => {
  if (!env.demoMode) return next(ApiError.forbidden("Mode démonstration désactivé."));
  next();
});

// POST /api/demo/nightly-batch — déclenche immédiatement le traitement nocturne.
// Réservé à la coordination et l'administration, pour la démonstration.
router.post(
  "/nightly-batch",
  authenticate,
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  asyncHandler(async (_req, res) => {
    const summary = await runNightlyBatch();
    res.json({ message: "Traitement nocturne exécuté manuellement.", summary });
  })
);

module.exports = router;
