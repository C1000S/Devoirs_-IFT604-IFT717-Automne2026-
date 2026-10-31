const { Schema, model } = require("mongoose");

const repairEventSchema = new Schema({
  request: { type: Schema.Types.ObjectId, ref: "RepairRequest", required: true },
  type: { type: String, required: true },
  message: { type: String, required: true },
  actor: { type: Schema.Types.ObjectId, ref: "User", default: null },
}, { timestamps: { createdAt: true, updatedAt: false } });

module.exports = model("RepairEvent", repairEventSchema);
