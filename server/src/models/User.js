const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");
const env = require("../config/env");
const { ROLES } = require("../constants");

const userSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true },
    email: {
      type: String,
      required: true,
      unique: true,
      lowercase: true,
      trim: true,
      match: [/^[^\s@]+@[^\s@]+\.[^\s@]+$/, "Courriel invalide."],
    },
    // Le mot de passe en clair n'est jamais stocké : seul le hachage bcrypt l'est.
    passwordHash: { type: String, required: true, select: false },
    role: {
      type: String,
      enum: Object.values(ROLES),
      default: ROLES.REQUESTER,
      index: true,
    },
    // Catégories de réparation maîtrisées (pertinent pour les réparateurs).
    skills: [{ type: mongoose.Schema.Types.ObjectId, ref: "Category" }],
    phone: { type: String, trim: true },
    active: { type: Boolean, default: true },
  },
  { timestamps: true }
);

// Hache le mot de passe fourni et le place dans passwordHash.
userSchema.methods.setPassword = async function setPassword(plain) {
  this.passwordHash = await bcrypt.hash(plain, env.bcryptRounds);
};

// Compare un mot de passe en clair au hachage stocké.
userSchema.methods.verifyPassword = function verifyPassword(plain) {
  if (!this.passwordHash) return Promise.resolve(false);
  return bcrypt.compare(plain, this.passwordHash);
};

// Ne jamais exposer le hachage du mot de passe dans les réponses JSON.
userSchema.set("toJSON", {
  virtuals: true,
  versionKey: false,
  transform: (_doc, ret) => {
    ret.id = ret._id;
    delete ret._id;
    delete ret.passwordHash;
    return ret;
  },
});

module.exports = mongoose.model("User", userSchema);
