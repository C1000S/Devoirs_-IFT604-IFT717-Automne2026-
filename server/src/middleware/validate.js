const { ZodError } = require("zod");
const ApiError = require("../utils/ApiError");

// Valide une partie de la requête (body par défaut) contre un schéma Zod.
// Remplace la valeur par la version analysée (valeurs par défaut, types convertis).
function validate(schema, source = "body") {
  return (req, _res, next) => {
    try {
      req[source] = schema.parse(req[source]);
      next();
    } catch (err) {
      if (err instanceof ZodError) {
        const details = err.issues.map((issue) => ({
          path: issue.path.join("."),
          message: issue.message,
        }));
        return next(ApiError.badRequest("Données invalides.", details));
      }
      return next(err);
    }
  };
}

module.exports = { validate };
