const express = require("express");
const { z } = require("zod");
const Category = require("../models/Category");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { validate } = require("../middleware/validate");
const { authenticate, authorize } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = express.Router();

// GET /api/categories — accessible à toute personne authentifiée.
router.get(
  "/",
  authenticate,
  asyncHandler(async (req, res) => {
    const filter = {};
    if (req.query.active === "true") filter.active = true;
    const categories = await Category.find(filter).sort({ name: 1 });
    res.json(categories.map((c) => c.toJSON()));
  })
);

const categorySchema = z.object({
  name: z.string().min(1),
  slug: z
    .string()
    .min(1)
    .regex(/^[a-z0-9-]+$/, "Le slug ne doit contenir que des minuscules, chiffres et tirets."),
  description: z.string().optional(),
  active: z.boolean().optional(),
});

// POST /api/categories — réservé à la coordination et l'administration.
router.post(
  "/",
  authenticate,
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  validate(categorySchema),
  asyncHandler(async (req, res) => {
    if (await Category.findOne({ slug: req.body.slug })) {
      throw ApiError.conflict("Une catégorie avec ce slug existe déjà.");
    }
    const category = await Category.create(req.body);
    res.status(201).json(category.toJSON());
  })
);

// PATCH /api/categories/:id
router.patch(
  "/:id",
  authenticate,
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  validate(categorySchema.partial()),
  asyncHandler(async (req, res) => {
    const category = await Category.findByIdAndUpdate(req.params.id, req.body, {
      new: true,
      runValidators: true,
    });
    if (!category) throw ApiError.notFound("Catégorie introuvable.");
    res.json(category.toJSON());
  })
);

// DELETE /api/categories/:id — désactivation logique (préserve l'historique).
router.delete(
  "/:id",
  authenticate,
  authorize(ROLES.ADMIN),
  asyncHandler(async (req, res) => {
    const category = await Category.findByIdAndUpdate(
      req.params.id,
      { active: false },
      { new: true }
    );
    if (!category) throw ApiError.notFound("Catégorie introuvable.");
    res.json(category.toJSON());
  })
);

module.exports = router;
