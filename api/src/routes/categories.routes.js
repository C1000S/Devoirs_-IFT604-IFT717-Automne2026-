const { Router } = require("express");
const Category = require("../models/Category");
const { requireAuth, requireRole } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

router.get("/", requireAuth, async (_req, res) => {
  const categories = await Category.find();
  res.json(categories);
});

router.post("/", requireAuth, requireRole(ROLES.ADMIN, ROLES.COORDINATOR), async (req, res) => {
  const { id, name, description } = req.body;
  if (!id || !name) return res.status(400).json({ error: "id et name sont requis." });
  const existing = await Category.findById(id);
  if (existing) return res.status(409).json({ error: "Cette catégorie existe déjà." });
  const category = await Category.create({ _id: id, name, description: description || "" });
  res.status(201).json(category);
});

router.patch("/:id", requireAuth, requireRole(ROLES.ADMIN, ROLES.COORDINATOR), async (req, res) => {
  const { name, description } = req.body;
  const category = await Category.findByIdAndUpdate(req.params.id, { name, description }, { new: true });
  if (!category) return res.status(404).json({ error: "Catégorie introuvable." });
  res.json(category);
});

module.exports = router;
