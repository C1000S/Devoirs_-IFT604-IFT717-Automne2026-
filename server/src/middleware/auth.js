const { verifyToken } = require("../utils/token");
const asyncHandler = require("../utils/asyncHandler");
const ApiError = require("../utils/ApiError");
const User = require("../models/User");

// Extrait le jeton de l'en-tête Authorization ou, à défaut, du paramètre ?token.
// Le paramètre d'URL est utile pour EventSource (SSE), qui ne peut pas poser d'en-têtes.
function extractToken(req) {
  const header = req.headers.authorization || "";
  if (header.startsWith("Bearer ")) return header.slice(7).trim();
  if (typeof req.query.token === "string") return req.query.token;
  return null;
}

// Vérifie l'identité de la personne connectée et charge son compte dans req.user.
const authenticate = asyncHandler(async (req, _res, next) => {
  const token = extractToken(req);
  if (!token) throw ApiError.unauthorized("Jeton d'authentification manquant.");

  let payload;
  try {
    payload = verifyToken(token);
  } catch (err) {
    throw ApiError.unauthorized("Jeton invalide ou expiré.");
  }

  const user = await User.findById(payload.sub);
  if (!user || !user.active) {
    throw ApiError.unauthorized("Compte introuvable ou désactivé.");
  }
  req.user = user;
  next();
});

// Restreint l'accès à certains rôles. À placer après `authenticate`.
function authorize(...roles) {
  return (req, _res, next) => {
    if (!req.user) return next(ApiError.unauthorized());
    if (roles.length && !roles.includes(req.user.role)) {
      return next(ApiError.forbidden("Votre rôle ne permet pas cette opération."));
    }
    next();
  };
}

module.exports = { authenticate, authorize, extractToken };
