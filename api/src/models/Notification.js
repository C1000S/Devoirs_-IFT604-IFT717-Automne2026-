const { Schema, model } = require("mongoose");

const notificationSchema = new Schema({
  user: { type: Schema.Types.ObjectId, ref: "User", required: true },
  request: { type: Schema.Types.ObjectId, ref: "RepairRequest", default: null },
  title: { type: String, required: true },
  message: { type: String, required: true },
  read: { type: Boolean, default: false },
}, { timestamps: { createdAt: true, updatedAt: false } });

module.exports = model("Notification", notificationSchema);
