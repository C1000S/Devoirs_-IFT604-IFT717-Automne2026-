const { Schema, model } = require("mongoose");

// Créneau où la clinique peut accueillir des réparations, et disponibilité
// des personnes réparatrices pour ce créneau (gérés par la coordination).
const slotSchema = new Schema({
  date: { type: Date, required: true },
  startTime: { type: String, required: true }, // ex: "09:00"
  endTime: { type: String, required: true },   // ex: "12:00"
  capacity: { type: Number, default: 1 },      // nb de réparations simultanées acceptées
  notes: { type: String, default: "" },
  availableRepairers: [{ type: Schema.Types.ObjectId, ref: "User" }],
}, { timestamps: true });

slotSchema.index({ date: 1, startTime: 1 });

module.exports = model("Slot", slotSchema);
