const { Schema, model } = require("mongoose");
const { REQUEST_STATUSES, PRIORITIES } = require("../constants");

const partUsageSchema = new Schema({
  part: { type: Schema.Types.ObjectId, ref: "Part" },
  quantity: { type: Number, default: 1 },
}, { _id: false });

const interventionSchema = new Schema({
  description: { type: String, required: true },
  partsUsed: [partUsageSchema],
  performedBy: { type: Schema.Types.ObjectId, ref: "User" },
  performedAt: { type: Date, default: Date.now },
});

const repairRequestSchema = new Schema({
  requester: { type: Schema.Types.ObjectId, ref: "User", required: true },
  category: { type: String, ref: "Category", required: true },
  device: {
    type: { type: String, required: true },
    brand: { type: String, default: "" },
    model: { type: String, default: "" },
  },
  problemDescription: { type: String, required: true },
  photos: [{ type: String }],
  contactMethod: { type: String, default: "" },
  status: { type: String, enum: Object.values(REQUEST_STATUSES), default: REQUEST_STATUSES.TO_TRIAGE },
  priority: { type: String, enum: PRIORITIES, default: "normal" },
  assignedTo: { type: Schema.Types.ObjectId, ref: "User", default: null },
  scheduledSlot: { type: Schema.Types.ObjectId, ref: "Slot", default: null },
  diagnosis: {
    text: { type: String },
    partsNeeded: [partUsageSchema],
    createdAt: { type: Date },
  },
  estimate: {
    cost: { type: Number },
    description: { type: String },
    status: { type: String, enum: ["pending", "accepted", "refused"], default: undefined },
    respondedAt: { type: Date },
  },
  interventions: [interventionSchema],
  closedReason: { type: String },
}, { timestamps: true });

repairRequestSchema.index({ status: 1, category: 1, assignedTo: 1, requester: 1, createdAt: -1 });

module.exports = model("RepairRequest", repairRequestSchema);
