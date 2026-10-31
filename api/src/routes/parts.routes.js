const { Router } = require("express");
const Part = require("../models/Part");
const { requireAuth, requireRole } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

// Consultation du catalogue : toute personne authentifiée (utile aux
// réparateurs pour choisir une pièce lors d'un diagnostic/intervention).
router.get("/", requireAuth, async (_req, res) => {
  const parts = await Part.find().sort({ name: 1 });
  res.json(parts);
});

router.get("/:id", requireAuth, async (req, res) => {
  const part = await Part.findById(req.params.id);
  if (!part) return res.status(404).json({ error: "Pièce introuvable." });
  res.json(part);
});

// Gestion du catalogue : réservée à la coordination/administration.
router.post("/", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { name, sku, unitCost, stock } = req.body;
  if (!name || !sku) return res.status(400).json({ error: "name et sku sont requis." });
  const existing = await Part.findOne({ sku });
  if (existing) return res.status(409).json({ error: "Une pièce existe déjà avec ce SKU." });
  const part = await Part.create({ name, sku, unitCost: unitCost || 0, stock: stock || 0 });
  res.status(201).json(part);
});

router.patch("/:id", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { name, unitCost, stock } = req.body;
  const update = {};
  if (name !== undefined) update.name = name;
  if (unitCost !== undefined) update.unitCost = unitCost;
  if (stock !== undefined) update.stock = stock;
  const part = await Part.findByIdAndUpdate(req.params.id, update, { new: true });
  if (!part) return res.status(404).json({ error: "Pièce introuvable." });
  res.json(part);
});

router.delete("/:id", requireAuth, requireRole(ROLES.ADMIN), async (req, res) => {
  const part = await Part.findByIdAndDelete(req.params.id);
  if (!part) return res.status(404).json({ error: "Pièce introuvable." });
  res.status(204).send();
});

module.exports = router;
