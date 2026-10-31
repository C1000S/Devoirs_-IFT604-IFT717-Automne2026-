// Express 4 ne transmet pas automatiquement les rejets de promesses à next().
// Ce wrapper est disponible pour les routes qui en ont besoin.
function asyncHandler(fn) {
  return (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
}
module.exports = { asyncHandler };
