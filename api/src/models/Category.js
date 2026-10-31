const { Schema, model } = require("mongoose");

const categorySchema = new Schema({
  _id: { type: String, trim: true }, // ex: "mobile", "computers"
  name: { type: String, required: true },
  description: { type: String, default: "" },
}, { _id: false, timestamps: true });

module.exports = model("Category", categorySchema);
