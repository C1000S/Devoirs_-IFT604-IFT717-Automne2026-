const { Router } = require("express");
const User = require("../models/User");
const { signToken } = require("../utils/jwt");
const { requireAuth } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = Router();

// Inscription publique : toujours en tant que demandeur ou demandeuse.
// La création de comptes réparateur/coordonnateur/administrateur passe par
// POST /api/users (réservé à l'administrateur), pour éviter une élévation de
// privilèges via l'inscription publique.
router.post("/register", async (req, res) => {
  const { name, email, password } = req.body;
  if (!name || !email || !password) {
    return res.status(400).json({ error: "name, email et password sont requis." });
  }
  const existing = await User.findOne({ email: email.toLowerCase() });
  if (existing) return res.status(409).json({ error: "Un compte existe déjà avec ce courriel." });

  const user = new User({ name, email, role: ROLES.REQUESTER });
  await user.setPassword(password);
  await user.save();

  const token = signToken(user);
  return res.status(201).json({ token, user: user.toPublicJSON() });
});

router.post("/login", async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) return res.status(400).json({ error: "email et password sont requis." });

  const user = await User.findOne({ email: email.toLowerCase() });
  if (!user) return res.status(401).json({ error: "Identifiants invalides." });
  const valid = await user.comparePassword(password);
  if (!valid) return res.status(401).json({ error: "Identifiants invalides." });

  const token = signToken(user);
  return res.json({ token, user: user.toPublicJSON() });
});

router.get("/me", requireAuth, (req, res) => {
  res.json(req.user.toPublicJSON());
});

module.exports = router;
