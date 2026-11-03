const mongoose = require("mongoose");
const ApiError = require("../utils/ApiError");

// Route non trouvée.
function notFoundHandler(_req, res) {
  res.status(404).json({ error: { code: "NOT_FOUND", message: "Route introuvable." } });
}

// Gestionnaire d'erreurs central : transforme toute erreur en réponse JSON cohérente.
// eslint-disable-next-line no-unused-vars
function errorHandler(err, _req, res, _next) {
  // Erreurs applicatives maîtrisées.
  if (err instanceof ApiError) {
    return res.status(err.statusCode).json({
      error: { code: err.code, message: err.message, details: err.details },
    });
  }

  // Erreur de validation Mongoose.
  if (err instanceof mongoose.Error.ValidationError) {
    const details = Object.values(err.errors).map((e) => ({
      path: e.path,
      message: e.message,
    }));
    return res.status(400).json({
      error: { code: "VALIDATION_ERROR", message: "Données invalides.", details },
    });
  }

  // Identifiant Mongo mal formé.
  if (err instanceof mongoose.Error.CastError) {
    return res.status(400).json({
      error: { code: "INVALID_ID", message: `Identifiant invalide : ${err.value}.` },
    });
  }

  // Violation d'unicité (ex. courriel déjà utilisé).
  if (err && err.code === 11000) {
    return res.status(409).json({
      error: {
        code: "DUPLICATE_KEY",
        message: "Une ressource avec cette valeur unique existe déjà.",
        details: err.keyValue,
      },
    });
  }

  // Erreur inattendue.
  console.error("Erreur non gérée :", err);
  return res.status(500).json({
    error: { code: "INTERNAL_ERROR", message: "Erreur interne du serveur." },
  });
}

module.exports = { notFoundHandler, errorHandler };
