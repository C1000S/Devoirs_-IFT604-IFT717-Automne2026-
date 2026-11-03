const mongoose = require("mongoose");
const toJSONOptions = require("./toJSON");
const { EVENT_TYPES } = require("../constants");

// Entrée d'historique rattachée à une demande (trace des transitions significatives).
const repairEventSchema = new mongoose.Schema(
  {
    request: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "RepairRequest",
      required: true,
      index: true,
    },
    type: { type: String, enum: Object.values(EVENT_TYPES), required: true },
    message: { type: String, required: true, trim: true },
    actor: { type: mongoose.Schema.Types.ObjectId, ref: "User", default: null },
    // Données additionnelles libres (ex. ancien/nouvel état).
    meta: { type: mongoose.Schema.Types.Mixed, default: {} },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

repairEventSchema.set("toJSON", toJSONOptions);

module.exports = mongoose.model("RepairEvent", repairEventSchema);
