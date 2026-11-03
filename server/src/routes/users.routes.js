const express = require("express");
const { z } = require("zod");
const User = require("../models/User");
const Category = require("../models/Category");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { validate } = require("../middleware/validate");
const { authenticate, authorize } = require("../middleware/auth");
const { ROLES } = require("../constants");

const router = express.Router();

router.use(authenticate);

async function resolveSkillIds(slugs = []) {
  if (!slugs.length) return [];
  const categories = await Category.find({ slug: { $in: slugs } });
  return categories.map((c) => c._id);
}

// GET /api/users — liste des comptes (coordination et administration).
// Filtres : ?role=repairer, ?categoryId=<id de catégorie> (réparateurs compétents).
router.get(
  "/",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  asyncHandler(async (req, res) => {
    const filter = {};
    if (req.query.role) filter.role = req.query.role;
    if (req.query.categoryId) {
      filter.role = ROLES.REPAIRER;
      filter.skills = req.query.categoryId;
    }
    const users = await User.find(filter).populate("skills", "name slug").sort({ name: 1 });
    res.json(users.map((u) => u.toJSON()));
  })
);

// POST /api/users — création d'un compte par un administrateur (tout rôle permis).
const createUserSchema = z.object({
  name: z.string().min(1),
  email: z.string().email(),
  password: z.string().min(6),
  role: z.enum(Object.values(ROLES)),
  phone: z.string().optional(),
  skills: z.array(z.string()).optional(),
});

router.post(
  "/",
  authorize(ROLES.ADMIN),
  validate(createUserSchema),
  asyncHandler(async (req, res) => {
    const { name, email, password, role, phone, skills } = req.body;
    if (await User.findOne({ email })) {
      throw ApiError.conflict("Un compte avec ce courriel existe déjà.");
    }
    const user = new User({
      name,
      email,
      role,
      phone,
      skills: role === ROLES.REPAIRER ? await resolveSkillIds(skills) : [],
    });
    await user.setPassword(password);
    await user.save();
    res.status(201).json(user.toJSON());
  })
);

// PATCH /api/users/:id — modification d'un compte (rôle, état, compétences) par l'administration.
const updateUserSchema = z.object({
  name: z.string().min(1).optional(),
  role: z.enum(Object.values(ROLES)).optional(),
  phone: z.string().optional(),
  active: z.boolean().optional(),
  skills: z.array(z.string()).optional(),
});

router.patch(
  "/:id",
  authorize(ROLES.ADMIN),
  validate(updateUserSchema),
  asyncHandler(async (req, res) => {
    const user = await User.findById(req.params.id);
    if (!user) throw ApiError.notFound("Utilisateur introuvable.");

    const { name, role, phone, active, skills } = req.body;
    if (name !== undefined) user.name = name;
    if (role !== undefined) user.role = role;
    if (phone !== undefined) user.phone = phone;
    if (active !== undefined) user.active = active;
    if (skills !== undefined) user.skills = await resolveSkillIds(skills);

    await user.save();
    res.json(user.toJSON());
  })
);

module.exports = router;
