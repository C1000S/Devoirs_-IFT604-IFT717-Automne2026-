const { Router } = require("express");
const Slot = require("../models/Slot");
const { requireAuth, requireRole } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

// Consultation : toute personne authentifiée peut voir les créneaux
// (utile au demandeur pour choisir un rendez-vous, au réparateur pour voir
// ses disponibilités confirmées).
router.get("/", requireAuth, async (req, res) => {
  const { from, to, repairerId } = req.query;
  const filter = {};
  if (from || to) {
    filter.date = {};
    if (from) filter.date.$gte = new Date(from);
    if (to) filter.date.$lte = new Date(to);
  }
  if (repairerId) filter.availableRepairers = repairerId;
  const slots = await Slot.find(filter).populate("availableRepairers", "name skills").sort({ date: 1, startTime: 1 });
  res.json(slots);
});

router.get("/:id", requireAuth, async (req, res) => {
  const slot = await Slot.findById(req.params.id).populate("availableRepairers", "name skills");
  if (!slot) return res.status(404).json({ error: "Créneau introuvable." });
  res.json(slot);
});

// Création et gestion des créneaux : réservées à la coordination/administration.
router.post("/", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { date, startTime, endTime, capacity, notes } = req.body;
  if (!date || !startTime || !endTime) {
    return res.status(400).json({ error: "date, startTime et endTime sont requis." });
  }
  const slot = await Slot.create({ date, startTime, endTime, capacity: capacity || 1, notes: notes || "" });
  res.status(201).json(slot);
});

router.patch("/:id", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { date, startTime, endTime, capacity, notes } = req.body;
  const update = {};
  if (date) update.date = date;
  if (startTime) update.startTime = startTime;
  if (endTime) update.endTime = endTime;
  if (capacity !== undefined) update.capacity = capacity;
  if (notes !== undefined) update.notes = notes;
  const slot = await Slot.findByIdAndUpdate(req.params.id, update, { new: true });
  if (!slot) return res.status(404).json({ error: "Créneau introuvable." });
  res.json(slot);
});

// Validation de la disponibilité d'une personne réparatrice pour un créneau.
router.patch("/:id/availability", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const { repairerId, available } = req.body;
  if (!repairerId || typeof available !== "boolean") {
    return res.status(400).json({ error: "repairerId et available (boolean) sont requis." });
  }
  const slot = await Slot.findById(req.params.id);
  if (!slot) return res.status(404).json({ error: "Créneau introuvable." });

  const already = slot.availableRepairers.some((id) => id.toString() === repairerId);
  if (available && !already) slot.availableRepairers.push(repairerId);
  if (!available && already) {
    slot.availableRepairers = slot.availableRepairers.filter((id) => id.toString() !== repairerId);
  }
  await slot.save();
  res.json(slot);
});

router.delete("/:id", requireAuth, requireRole(ROLES.COORDINATOR, ROLES.ADMIN), async (req, res) => {
  const slot = await Slot.findByIdAndDelete(req.params.id);
  if (!slot) return res.status(404).json({ error: "Créneau introuvable." });
  res.status(204).send();
});

module.exports = router;
