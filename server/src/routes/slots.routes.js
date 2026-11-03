const express = require("express");
const { z } = require("zod");
const TimeSlot = require("../models/TimeSlot");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { validate } = require("../middleware/validate");
const { authenticate, authorize } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = express.Router();

router.use(authenticate);

// GET /api/slots — créneaux de la clinique (visibles au personnel).
router.get(
  "/",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN, ROLES.REPAIRER),
  asyncHandler(async (req, res) => {
    const filter = {};
    if (req.query.repairer) filter.repairer = req.query.repairer;
    const slots = await TimeSlot.find(filter).populate("repairer", "name").sort({ start: 1 });
    res.json(slots.map((s) => s.toJSON()));
  })
);

const slotBaseSchema = z.object({
  start: z.coerce.date(),
  end: z.coerce.date(),
  capacity: z.number().int().positive().optional(),
  repairer: z.string().optional(),
  notes: z.string().optional(),
});

// À la création, la fin doit être postérieure au début.
const slotSchema = slotBaseSchema.refine((data) => data.end > data.start, {
  message: "La fin du créneau doit être postérieure au début.",
  path: ["end"],
});

// POST /api/slots — création d'un créneau (coordination).
router.post(
  "/",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  validate(slotSchema),
  asyncHandler(async (req, res) => {
    const slot = await TimeSlot.create({ ...req.body, createdBy: req.user.id });
    res.status(201).json(slot.toJSON());
  })
);

// PATCH /api/slots/:id
router.patch(
  "/:id",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  validate(slotBaseSchema.partial()),
  asyncHandler(async (req, res) => {
    const slot = await TimeSlot.findByIdAndUpdate(req.params.id, req.body, {
      new: true,
      runValidators: true,
    });
    if (!slot) throw ApiError.notFound("Créneau introuvable.");
    res.json(slot.toJSON());
  })
);

// DELETE /api/slots/:id
router.delete(
  "/:id",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  asyncHandler(async (req, res) => {
    const slot = await TimeSlot.findByIdAndDelete(req.params.id);
    if (!slot) throw ApiError.notFound("Créneau introuvable.");
    res.status(204).end();
  })
);

module.exports = router;
