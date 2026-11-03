const jwt = require("jsonwebtoken");
const env = require("../config/env");

// Signe un JSON Web Token contenant l'identifiant et le rôle de l'utilisateur.
function signToken(user) {
  const payload = { sub: user.id.toString(), role: user.role };
  return jwt.sign(payload, env.jwtSecret, { expiresIn: env.jwtExpiresIn });
}

// Vérifie et décode un token. Lève une erreur si invalide ou expiré.
function verifyToken(token) {
  return jwt.verify(token, env.jwtSecret);
}

module.exports = { signToken, verifyToken };
