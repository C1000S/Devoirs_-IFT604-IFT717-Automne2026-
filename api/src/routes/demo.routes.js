const { Router } = require("express");
const { requireAuth, requireRole } = require("../middleware/auth");
const { runNightlyBatch } = require("../jobs/nightlyJob");
const { ROLES } = require("../constants");

const router = Router();

// Déclenchement manuel réservé à la démonstration (hors production).
router.post("/nightly-batch", requireAuth, requireRole(ROLES.ADMIN), async (_req, res) => {
  if (process.env.NODE_ENV === "production") {
    return res.status(403).json({ error: "Route de démonstration désactivée en production." });
  }
  const result = await runNightlyBatch();
  res.json(result);
});

module.exports = router;
