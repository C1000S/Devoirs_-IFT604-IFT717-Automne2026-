const { verifyToken } = require("../utils/jwt");
const User = require("../models/User");

function extractToken(req) {
  const header = req.headers.authorization;
  if (header?.startsWith("Bearer ")) return header.slice(7);
  if (req.query.token) return req.query.token; // utile pour EventSource (SSE), qui ne peut fixer d'en-têtes
  return null;
}

async function requireAuth(req, res, next) {
  const token = extractToken(req);
  if (!token) return res.status(401).json({ error: "Authentification requise." });
  try {
    const payload = verifyToken(token);
    const user = await User.findById(payload.sub);
    if (!user) return res.status(401).json({ error: "Utilisateur introuvable." });
    req.user = user;
    next();
  } catch (err) {
    return res.status(401).json({ error: "Jeton invalide ou expiré." });
  }
}

function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.user) return res.status(401).json({ error: "Authentification requise." });
    if (!roles.includes(req.user.role)) {
      return res.status(403).json({ error: "Accès refusé pour ce rôle." });
    }
    next();
  };
}

module.exports = { requireAuth, requireRole };
