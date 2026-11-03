const { ROLES } = require("../constants");

// Règles d'autorisation au niveau d'une demande précise.
// Ces fonctions complètent le contrôle de rôle des routes : elles vérifient
// qu'une personne ne consulte ou ne modifie que les demandes auxquelles elle a droit.

function idEquals(a, b) {
  if (!a || !b) return false;
  const av = a._id ? a._id.toString() : a.toString();
  const bv = b._id ? b._id.toString() : b.toString();
  return av === bv;
}

function isStaff(user) {
  return user.role === ROLES.COORDINATOR || user.role === ROLES.ADMIN;
}

// Peut consulter la demande ?
function canViewRequest(user, request) {
  if (isStaff(user)) return true;
  if (user.role === ROLES.REPAIRER) {
    return idEquals(request.assignedTo, user.id);
  }
  // Demandeur : uniquement ses propres demandes.
  return idEquals(request.requester, user.id);
}

// Peut modifier les champs descriptifs (appareil, problème, photos, contact, priorité) ?
// Le demandeur peut le faire tant que la demande n'est pas prise en charge; le personnel toujours.
function canEditRequestFields(user, request) {
  if (isStaff(user)) return true;
  return idEquals(request.requester, user.id);
}

// Peut agir en tant que réparateur sur cette demande (diagnostic, estimation, intervention, pièces) ?
function canWorkOnRequest(user, request) {
  if (isStaff(user)) return true;
  return user.role === ROLES.REPAIRER && idEquals(request.assignedTo, user.id);
}

module.exports = {
  idEquals,
  isStaff,
  canViewRequest,
  canEditRequestFields,
  canWorkOnRequest,
};
