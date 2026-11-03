const express = require("express");
const { z } = require("zod");
const User = require("../models/User");
const Category = require("../models/Category");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { signToken } = require("../utils/token");
const { validate } = require("../middleware/validate");
const { authenticate } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = express.Router();

const registerSchema = z.object({
  name: z.string().min(1, "Le nom est requis."),
  email: z.string().email("Courriel invalide."),
  password: z.string().min(6, "Le mot de passe doit comporter au moins 6 caractères."),
  // À l'inscription publique, on n'autorise que demandeur ou réparateur.
  role: z.enum([ROLES.REQUESTER, ROLES.REPAIRER]).optional().default(ROLES.REQUESTER),
  phone: z.string().optional(),
  skills: z.array(z.string()).optional(),
});

const loginSchema = z.object({
  email: z.string().email("Courriel invalide."),
  password: z.string().min(1, "Le mot de passe est requis."),
});

// Convertit des slugs de catégories en identifiants (pour les compétences du réparateur).
async function resolveSkillIds(slugs = []) {
  if (!slugs.length) return [];
  const categories = await Category.find({ slug: { $in: slugs } });
  return categories.map((c) => c._id);
}

// POST /api/auth/register — inscription d'un demandeur ou d'un réparateur.
router.post(
  "/register",
  validate(registerSchema),
  asyncHandler(async (req, res) => {
    const { name, email, password, role, phone, skills } = req.body;

    const existing = await User.findOne({ email });
    if (existing) throw ApiError.conflict("Un compte avec ce courriel existe déjà.");

    const user = new User({
      name,
      email,
      role,
      phone,
      skills: role === ROLES.REPAIRER ? await resolveSkillIds(skills) : [],
    });
    await user.setPassword(password);
    await user.save();

    const token = signToken(user);
    res.status(201).json({ token, user: user.toJSON() });
  })
);

// POST /api/auth/login — connexion par courriel et mot de passe.
router.post(
  "/login",
  validate(loginSchema),
  asyncHandler(async (req, res) => {
    const { email, password } = req.body;
    // Le passwordHash est `select: false` : il faut l'inclure explicitement.
    const user = await User.findOne({ email }).select("+passwordHash");
    if (!user || !user.active) throw ApiError.unauthorized("Identifiants invalides.");

    const ok = await user.verifyPassword(password);
    if (!ok) throw ApiError.unauthorized("Identifiants invalides.");

    const token = signToken(user);
    res.json({ token, user: user.toJSON() });
  })
);

// GET /api/auth/me — profil de la personne connectée.
router.get(
  "/me",
  authenticate,
  asyncHandler(async (req, res) => {
    const user = await User.findById(req.user.id).populate("skills", "name slug");
    res.json(user.toJSON());
  })
);

module.exports = router;
