const { Schema, model } = require("mongoose");

const partSchema = new Schema({
  name: { type: String, required: true },
  sku: { type: String, required: true, unique: true },
  unitCost: { type: Number, default: 0 },
  stock: { type: Number, default: 0 },
}, { timestamps: true });

module.exports = model("Part", partSchema);
