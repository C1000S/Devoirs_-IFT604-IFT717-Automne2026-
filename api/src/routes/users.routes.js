const { Router } = require("express");
const User = require("../models/User");
const { requireAuth, requireRole } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

// Liste des comptes : administrateur (gestion) et coordination (affectation) uniquement.
router.get("/", requireAuth, requireRole(ROLES.ADMIN, ROLES.COORDINATOR), async (req, res) => {
  const { role, skill } = req.query;
  const filter = {};
  if (role) filter.role = role;
  if (skill) filter.skills = skill;
  const users = await User.find(filter);
  res.json(users.map((u) => u.toPublicJSON()));
});

// Création de compte par l'administrateur, avec rôle choisi (réparateur, coordonnateur, admin...).
router.post("/", requireAuth, requireRole(ROLES.ADMIN), async (req, res) => {
  const { name, email, password, role, skills } = req.body;
  if (!name || !email || !password || !role) {
    return res.status(400).json({ error: "name, email, password et role sont requis." });
  }
  if (!Object.values(ROLES).includes(role)) return res.status(400).json({ error: "Rôle invalide." });

  const existing = await User.findOne({ email: email.toLowerCase() });
  if (existing) return res.status(409).json({ error: "Un compte existe déjà avec ce courriel." });

  const user = new User({ name, email, role, skills: skills || [] });
  await user.setPassword(password);
  await user.save();
  res.status(201).json(user.toPublicJSON());
});

router.patch("/:id/role", requireAuth, requireRole(ROLES.ADMIN), async (req, res) => {
  const { role, skills } = req.body;
  const update = {};
  if (role) {
    if (!Object.values(ROLES).includes(role)) return res.status(400).json({ error: "Rôle invalide." });
    update.role = role;
  }
  if (skills) update.skills = skills;
  const user = await User.findByIdAndUpdate(req.params.id, update, { new: true });
  if (!user) return res.status(404).json({ error: "Utilisateur introuvable." });
  res.json(user.toPublicJSON());
});

module.exports = router;
