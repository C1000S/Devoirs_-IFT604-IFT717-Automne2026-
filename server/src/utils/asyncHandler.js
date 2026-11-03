// Enveloppe un gestionnaire de route asynchrone pour transmettre toute erreur
// rejetée au middleware d'erreurs via next(), sans try/catch répétés.
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}

module.exports = asyncHandler;
