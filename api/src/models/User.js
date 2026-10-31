const { Schema, model } = require("mongoose");
const bcrypt = require("bcryptjs");
const { ROLES } = require("../constants");

const userSchema = new Schema({
  name: { type: String, required: true, trim: true },
  email: { type: String, required: true, unique: true, lowercase: true, trim: true },
  passwordHash: { type: String, required: true },
  role: { type: String, enum: Object.values(ROLES), default: ROLES.REQUESTER },
  skills: [{ type: String }], // clés de catégories, pertinent pour les réparateurs
}, { timestamps: true });

userSchema.methods.setPassword = async function setPassword(plain) {
  this.passwordHash = await bcrypt.hash(plain, 10);
};

userSchema.methods.comparePassword = function comparePassword(plain) {
  return bcrypt.compare(plain, this.passwordHash);
};

userSchema.methods.toPublicJSON = function toPublicJSON() {
  return { id: this._id, name: this.name, email: this.email, role: this.role, skills: this.skills };
};

module.exports = model("User", userSchema);
