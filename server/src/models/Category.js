const mongoose = require("mongoose");
const toJSONOptions = require("./toJSON");

const categorySchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true },
    // Identifiant lisible et stable (ex. "mobile"), utile pour l'API et le Web sémantique (TP3).
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    description: { type: String, trim: true, default: "" },
    active: { type: Boolean, default: true },
  },
  { timestamps: true }
);

categorySchema.set("toJSON", toJSONOptions);

module.exports = mongoose.model("Category", categorySchema);
