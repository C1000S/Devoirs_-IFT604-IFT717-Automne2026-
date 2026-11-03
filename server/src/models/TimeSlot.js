const mongoose = require("mongoose");
const toJSONOptions = require("./toJSON");

// Créneau où la clinique peut accueillir des réparations.
// Géré par la coordination, éventuellement rattaché à un réparateur.
const timeSlotSchema = new mongoose.Schema(
  {
    start: { type: Date, required: true },
    end: { type: Date, required: true },
    capacity: { type: Number, default: 1, min: 1 },
    repairer: { type: mongoose.Schema.Types.ObjectId, ref: "User", default: null },
    notes: { type: String, trim: true, default: "" },
    createdBy: { type: mongoose.Schema.Types.ObjectId, ref: "User" },
  },
  { timestamps: true }
);

timeSlotSchema.set("toJSON", toJSONOptions);

module.exports = mongoose.model("TimeSlot", timeSlotSchema);
