const mongoose = require("mongoose");
const toJSONOptions = require("./toJSON");
const { NOTIFICATION_TYPES } = require("../constants");

// Notification persistante destinée à une personne.
const notificationSchema = new mongoose.Schema(
  {
    user: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    request: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "RepairRequest",
      default: null,
    },
    type: { type: String, enum: Object.values(NOTIFICATION_TYPES), required: true },
    title: { type: String, required: true, trim: true },
    message: { type: String, required: true, trim: true },
    read: { type: Boolean, default: false, index: true },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

notificationSchema.set("toJSON", toJSONOptions);

module.exports = mongoose.model("Notification", notificationSchema);
