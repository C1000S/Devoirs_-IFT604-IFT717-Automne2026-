// Erreur applicative portant un code de statut HTTP et un code machine.
// Le gestionnaire d'erreurs central (middleware/error.js) la transforme en réponse JSON.
class ApiError extends Error {
  constructor(statusCode, message, code = undefined, details = undefined) {
    super(message);
    this.name = "ApiError";
    this.statusCode = statusCode;
    this.code = code;
    this.details = details;
  }

  static badRequest(message, details) {
    return new ApiError(400, message, "BAD_REQUEST", details);
  }

  static unauthorized(message = "Authentification requise.") {
    return new ApiError(401, message, "UNAUTHORIZED");
  }

  static forbidden(message = "Accès refusé.") {
    return new ApiError(403, message, "FORBIDDEN");
  }

  static notFound(message = "Ressource introuvable.") {
    return new ApiError(404, message, "NOT_FOUND");
  }

  static conflict(message, details) {
    return new ApiError(409, message, "CONFLICT", details);
  }

  static unprocessable(message, details) {
    return new ApiError(422, message, "UNPROCESSABLE_ENTITY", details);
  }
}

module.exports = ApiError;
