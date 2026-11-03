const mongoose = require("mongoose");
const toJSONOptions = require("./toJSON");
const {
  REQUEST_STATUSES,
  PRIORITIES,
  ESTIMATE_STATUSES,
} = require("../constants");

// Appareil concerné par la demande.
const deviceSchema = new mongoose.Schema(
  {
    type: { type: String, required: true, trim: true }, // ex. "montre connectée"
    brand: { type: String, trim: true, default: "" },
    model: { type: String, trim: true, default: "" },
  },
  { _id: false }
);

// Diagnostic consigné par la personne réparatrice.
const diagnosisSchema = new mongoose.Schema(
  {
    notes: { type: String, required: true, trim: true },
    createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
    createdAt: { type: Date, default: Date.now },
  },
  { _id: false }
);

// Estimation (coût / délai) soumise au demandeur, qui peut l'accepter ou la refuser.
const estimateSchema = new mongoose.Schema(
  {
    amount: { type: Number, required: true, min: 0 },
    currency: { type: String, default: "CAD" },
    details: { type: String, trim: true, default: "" },
    status: {
      type: String,
      enum: Object.values(ESTIMATE_STATUSES),
      default: ESTIMATE_STATUSES.PENDING,
    },
    createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
    createdAt: { type: Date, default: Date.now },
    respondedAt: { type: Date, default: null },
  },
  { _id: false }
);

// Pièce nécessaire à la réparation.
const partSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true },
    quantity: { type: Number, default: 1, min: 1 },
    unitCost: { type: Number, default: 0, min: 0 },
    status: { type: String, default: "needed" }, // needed | ordered | received
    addedBy: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
    addedAt: { type: Date, default: Date.now },
  },
  { _id: true }
);

// Intervention réalisée sur l'appareil.
const interventionSchema = new mongoose.Schema(
  {
    notes: { type: String, required: true, trim: true },
    minutesSpent: { type: Number, default: 0, min: 0 },
    performedBy: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
    performedAt: { type: Date, default: Date.now },
  },
  { _id: true }
);

const repairRequestSchema = new mongoose.Schema(
  {
    requester: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    category: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "Category",
      required: true,
      index: true,
    },
    device: { type: deviceSchema, required: true },
    problemDescription: { type: String, required: true, trim: true },
    photos: [{ type: String }], // URLs ou chemins des photos
    contact: {
      method: { type: String, default: "email" }, // email | phone | other
      value: { type: String, trim: true, default: "" },
    },
    priority: { type: String, enum: PRIORITIES, default: "normal", index: true },
    status: {
      type: String,
      enum: Object.values(REQUEST_STATUSES),
      default: REQUEST_STATUSES.TO_TRIAGE,
      index: true,
    },
    assignedTo: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      default: null,
      index: true,
    },
    slot: { type: mongoose.Schema.Types.ObjectId, ref: "TimeSlot", default: null },
    diagnosis: { type: diagnosisSchema, default: null },
    estimate: { type: estimateSchema, default: null },
    parts: { type: [partSchema], default: [] },
    interventions: { type: [interventionSchema], default: [] },
    // Motif de clôture / annulation / non-réparabilité.
    closureReason: { type: String, trim: true, default: null },
    // Horodatage de la dernière activité, utilisé par le traitement nocturne.
    lastActivityAt: { type: Date, default: Date.now, index: true },
  },
  { timestamps: true }
);

repairRequestSchema.set("toJSON", toJSONOptions);

module.exports = mongoose.model("RepairRequest", repairRequestSchema);
