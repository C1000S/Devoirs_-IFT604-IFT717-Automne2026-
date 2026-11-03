const express = require("express");
const { z } = require("zod");
const mongoose = require("mongoose");
const RepairRequest = require("../models/RepairRequest");
const RepairEvent = require("../models/RepairEvent");
const Category = require("../models/Category");
const User = require("../models/User");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const { validate } = require("../middleware/validate");
const { authenticate, authorize } = require("../middleware/auth");
const access = require("../services/access");
const lifecycle = require("../services/requests");
const { recordEvent } = require("../services/requests");
const { EVENT_TYPES, PRIORITIES, ROLES, ESTIMATE_STATUSES } = require("../constants");

const router = express.Router();
router.use(authenticate);

const POPULATE = [
  { path: "requester", select: "name email role" },
  { path: "category", select: "name slug" },
  { path: "assignedTo", select: "name role skills" },
  { path: "slot", select: "start end" },
];

// Charge la demande ciblée et la place dans req.repairRequest.
const loadRequest = asyncHandler(async (req, _res, next) => {
  if (!mongoose.isValidObjectId(req.params.id)) {
    throw ApiError.badRequest("Identifiant de demande invalide.");
  }
  const request = await RepairRequest.findById(req.params.id);
  if (!request) throw ApiError.notFound("Demande introuvable.");
  req.repairRequest = request;
  next();
});

// Renvoie la demande peuplée (références résolues) pour les réponses JSON.
function populated(id) {
  return RepairRequest.findById(id).populate(POPULATE);
}

// ---------------------------------------------------------------------------
// Liste et recherche
// ---------------------------------------------------------------------------

// GET /api/repair-requests
// Filtres : status, categoryId, priority, assignedTo, requesterId, hasEstimate, q,
//           createdFrom, createdTo. Pagination : page, limit. Tri : sort.
router.get(
  "/",
  asyncHandler(async (req, res) => {
    const { role, id: userId } = req.user;
    const filter = {};

    // Portée selon le rôle : chacun ne voit que ce à quoi il a droit.
    if (role === ROLES.REQUESTER) {
      filter.requester = userId;
    } else if (role === ROLES.REPAIRER) {
      // Demandes assignées à la personne, ou demandes non assignées compatibles avec ses compétences.
      const me = await User.findById(userId);
      filter.$or = [
        { assignedTo: userId },
        { assignedTo: null, category: { $in: me.skills || [] } },
      ];
    }
    // coordinator / admin : aucune restriction.

    // Filtres de recherche.
    if (req.query.status) filter.status = req.query.status;
    if (req.query.categoryId) filter.category = req.query.categoryId;
    if (req.query.priority) filter.priority = req.query.priority;
    if (req.query.assignedTo) filter.assignedTo = req.query.assignedTo;
    if (req.query.requesterId && role !== ROLES.REQUESTER) {
      filter.requester = req.query.requesterId;
    }
    if (req.query.hasEstimate === "true") filter["estimate"] = { $ne: null };
    if (req.query.hasEstimate === "false") filter["estimate"] = null;

    if (req.query.createdFrom || req.query.createdTo) {
      filter.createdAt = {};
      if (req.query.createdFrom) filter.createdAt.$gte = new Date(req.query.createdFrom);
      if (req.query.createdTo) filter.createdAt.$lte = new Date(req.query.createdTo);
    }

    // Recherche plein texte simple sur l'appareil et la description.
    if (req.query.q) {
      const rx = new RegExp(req.query.q.trim(), "i");
      const search = [
        { problemDescription: rx },
        { "device.type": rx },
        { "device.brand": rx },
        { "device.model": rx },
      ];
      filter.$and = [...(filter.$and || []), { $or: search }];
    }

    const page = Math.max(parseInt(req.query.page || "1", 10), 1);
    const limit = Math.min(Math.max(parseInt(req.query.limit || "20", 10), 1), 100);
    const sort = req.query.sort || "-createdAt";

    const [items, total] = await Promise.all([
      RepairRequest.find(filter)
        .populate(POPULATE)
        .sort(sort)
        .skip((page - 1) * limit)
        .limit(limit),
      RepairRequest.countDocuments(filter),
    ]);

    res.json({
      items: items.map((r) => r.toJSON()),
      page,
      limit,
      total,
      totalPages: Math.ceil(total / limit),
    });
  })
);

// ---------------------------------------------------------------------------
// Création
// ---------------------------------------------------------------------------

const deviceSchema = z.object({
  type: z.string().min(1, "Le type d'appareil est requis."),
  brand: z.string().optional(),
  model: z.string().optional(),
});

const createSchema = z.object({
  categoryId: z.string().min(1, "La catégorie est requise."),
  device: deviceSchema,
  problemDescription: z.string().min(1, "La description du problème est requise."),
  photos: z.array(z.string()).optional(),
  contact: z
    .object({ method: z.string().optional(), value: z.string().optional() })
    .optional(),
  priority: z.enum(PRIORITIES).optional(),
  requesterId: z.string().optional(), // le personnel peut créer au nom d'un demandeur
});

// POST /api/repair-requests
router.post(
  "/",
  validate(createSchema),
  asyncHandler(async (req, res) => {
    const { categoryId, device, problemDescription, photos, contact, priority, requesterId } =
      req.body;

    if (!mongoose.isValidObjectId(categoryId)) {
      throw ApiError.badRequest("Identifiant de catégorie invalide.");
    }
    const category = await Category.findById(categoryId);
    if (!category || !category.active) throw ApiError.badRequest("Catégorie inexistante.");

    // Un demandeur crée pour lui-même; le personnel peut préciser requesterId.
    let requester = req.user.id;
    if (requesterId && access.isStaff(req.user)) {
      if (!mongoose.isValidObjectId(requesterId)) {
        throw ApiError.badRequest("Identifiant de demandeur invalide.");
      }
      requester = requesterId;
    }

    const request = await RepairRequest.create({
      requester,
      category: category._id,
      device,
      problemDescription,
      photos: photos || [],
      contact: contact || { method: "email", value: "" },
      priority: priority || "normal",
    });

    await recordEvent(request, EVENT_TYPES.CREATED, "Demande de réparation créée.", req.user);
    await request.save();
    lifecycle.publishRequestUpdate(request);

    res.status(201).json((await populated(request._id)).toJSON());
  })
);

// ---------------------------------------------------------------------------
// Détail et historique
// ---------------------------------------------------------------------------

// GET /api/repair-requests/:id
router.get(
  "/:id",
  loadRequest,
  asyncHandler(async (req, res) => {
    if (!access.canViewRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Vous n'avez pas accès à cette demande.");
    }
    const request = await populated(req.repairRequest._id);
    const events = await RepairEvent.find({ request: request._id })
      .populate("actor", "name role")
      .sort({ createdAt: 1 });
    res.json({ ...request.toJSON(), events: events.map((e) => e.toJSON()) });
  })
);

// GET /api/repair-requests/:id/events — historique seul.
router.get(
  "/:id/events",
  loadRequest,
  asyncHandler(async (req, res) => {
    if (!access.canViewRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Vous n'avez pas accès à cette demande.");
    }
    const events = await RepairEvent.find({ request: req.repairRequest._id })
      .populate("actor", "name role")
      .sort({ createdAt: 1 });
    res.json(events.map((e) => e.toJSON()));
  })
);

// ---------------------------------------------------------------------------
// Modification des champs descriptifs
// ---------------------------------------------------------------------------

const updateSchema = z.object({
  device: deviceSchema.partial().optional(),
  problemDescription: z.string().min(1).optional(),
  photos: z.array(z.string()).optional(),
  contact: z.object({ method: z.string().optional(), value: z.string().optional() }).optional(),
  priority: z.enum(PRIORITIES).optional(),
});

// PATCH /api/repair-requests/:id
router.patch(
  "/:id",
  loadRequest,
  validate(updateSchema),
  asyncHandler(async (req, res) => {
    const request = req.repairRequest;
    if (!access.canEditRequestFields(req.user, request)) {
      throw ApiError.forbidden("Vous ne pouvez pas modifier cette demande.");
    }
    const { device, problemDescription, photos, contact, priority } = req.body;
    if (device) request.device = { ...request.device.toObject(), ...device };
    if (problemDescription !== undefined) request.problemDescription = problemDescription;
    if (photos !== undefined) request.photos = photos;
    if (contact !== undefined) request.contact = { ...request.contact, ...contact };
    if (priority !== undefined) request.priority = priority;

    await request.save();
    await recordEvent(request, EVENT_TYPES.UPDATED, "Informations de la demande mises à jour.", req.user);
    await request.save();
    lifecycle.publishRequestUpdate(request);
    res.json((await populated(request._id)).toJSON());
  })
);

// ---------------------------------------------------------------------------
// Affectation / prise en charge
// ---------------------------------------------------------------------------

const assignSchema = z.object({
  repairerId: z.string().min(1),
  slotId: z.string().optional(),
});

// POST /api/repair-requests/:id/assign — affectation par la coordination.
router.post(
  "/:id/assign",
  authorize(ROLES.COORDINATOR, ROLES.ADMIN),
  loadRequest,
  validate(assignSchema),
  asyncHandler(async (req, res) => {
    const repairer = await User.findById(req.body.repairerId);
    if (!repairer || repairer.role !== ROLES.REPAIRER) {
      throw ApiError.badRequest("La personne ciblée n'est pas une réparatrice ou un réparateur.");
    }
    await lifecycle.assignRequest(req.repairRequest, repairer, req.user, {
      slot: req.body.slotId || null,
    });
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

// POST /api/repair-requests/:id/claim — une personne réparatrice prend en charge
// une demande non assignée compatible avec ses compétences.
router.post(
  "/:id/claim",
  authorize(ROLES.REPAIRER),
  loadRequest,
  asyncHandler(async (req, res) => {
    const request = req.repairRequest;
    if (request.assignedTo) throw ApiError.conflict("Cette demande est déjà assignée.");
    const me = await User.findById(req.user.id);
    const compatible = (me.skills || []).some((s) => access.idEquals(s, request.category));
    if (!compatible) {
      throw ApiError.forbidden("Cette demande ne correspond pas à vos compétences.");
    }
    await lifecycle.assignRequest(request, me, req.user);
    res.json((await populated(request._id)).toJSON());
  })
);

// ---------------------------------------------------------------------------
// Changement d'état
// ---------------------------------------------------------------------------

const statusSchema = z.object({
  status: z.string().min(1),
  reason: z.string().optional(),
});

// POST /api/repair-requests/:id/status
router.post(
  "/:id/status",
  loadRequest,
  validate(statusSchema),
  asyncHandler(async (req, res) => {
    const request = req.repairRequest;
    // Le demandeur peut annuler sa propre demande; sinon, personnel travaillant dessus.
    const wantsCancel = req.body.status === "cancelled";
    const requesterCancelling =
      wantsCancel && access.idEquals(request.requester, req.user.id);
    if (!access.canWorkOnRequest(req.user, request) && !requesterCancelling) {
      throw ApiError.forbidden("Vous ne pouvez pas changer l'état de cette demande.");
    }
    await lifecycle.changeStatus(request, req.body.status, req.user, { reason: req.body.reason });
    res.json((await populated(request._id)).toJSON());
  })
);

// ---------------------------------------------------------------------------
// Diagnostic, estimation, interventions, pièces
// ---------------------------------------------------------------------------

const diagnosisSchema = z.object({ notes: z.string().min(1) });

router.post(
  "/:id/diagnosis",
  loadRequest,
  validate(diagnosisSchema),
  asyncHandler(async (req, res) => {
    if (!access.canWorkOnRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Seule la personne assignée peut consigner un diagnostic.");
    }
    await lifecycle.addDiagnosis(req.repairRequest, req.body, req.user);
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

const estimateSchema = z.object({
  amount: z.number().nonnegative(),
  currency: z.string().optional(),
  details: z.string().optional(),
});

router.post(
  "/:id/estimate",
  loadRequest,
  validate(estimateSchema),
  asyncHandler(async (req, res) => {
    if (!access.canWorkOnRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Seule la personne assignée peut soumettre une estimation.");
    }
    await lifecycle.addEstimate(req.repairRequest, req.body, req.user);
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

const estimateResponseSchema = z.object({
  decision: z.enum([ESTIMATE_STATUSES.ACCEPTED, ESTIMATE_STATUSES.REFUSED]),
});

// POST /api/repair-requests/:id/estimate/response — réponse du demandeur.
router.post(
  "/:id/estimate/response",
  loadRequest,
  validate(estimateResponseSchema),
  asyncHandler(async (req, res) => {
    if (!access.idEquals(req.repairRequest.requester, req.user.id)) {
      throw ApiError.forbidden("Seul le demandeur peut répondre à l'estimation.");
    }
    await lifecycle.answerEstimate(req.repairRequest, req.body.decision, req.user);
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

const interventionSchema = z.object({
  notes: z.string().min(1),
  minutesSpent: z.number().int().nonnegative().optional(),
});

router.post(
  "/:id/interventions",
  loadRequest,
  validate(interventionSchema),
  asyncHandler(async (req, res) => {
    if (!access.canWorkOnRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Seule la personne assignée peut consigner une intervention.");
    }
    await lifecycle.addIntervention(req.repairRequest, req.body, req.user);
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

const partSchema = z.object({
  name: z.string().min(1),
  quantity: z.number().int().positive().optional(),
  unitCost: z.number().nonnegative().optional(),
  status: z.enum(["needed", "ordered", "received"]).optional(),
});

router.post(
  "/:id/parts",
  loadRequest,
  validate(partSchema),
  asyncHandler(async (req, res) => {
    if (!access.canWorkOnRequest(req.user, req.repairRequest)) {
      throw ApiError.forbidden("Seule la personne assignée peut ajouter une pièce.");
    }
    await lifecycle.addPart(req.repairRequest, req.body, req.user);
    res.json((await populated(req.repairRequest._id)).toJSON());
  })
);

module.exports = router;
