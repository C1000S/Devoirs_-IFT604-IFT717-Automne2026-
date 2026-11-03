package ca.usherbrooke.reparecampus.util

import ca.usherbrooke.reparecampus.util.Constants.STATUS_ASSIGNED
import ca.usherbrooke.reparecampus.util.Constants.STATUS_CANCELLED
import ca.usherbrooke.reparecampus.util.Constants.STATUS_CLOSED
import ca.usherbrooke.reparecampus.util.Constants.STATUS_IN_DIAGNOSIS
import ca.usherbrooke.reparecampus.util.Constants.STATUS_IN_REPAIR
import ca.usherbrooke.reparecampus.util.Constants.STATUS_NOT_REPAIRABLE
import ca.usherbrooke.reparecampus.util.Constants.STATUS_READY
import ca.usherbrooke.reparecampus.util.Constants.STATUS_TO_TRIAGE
import ca.usherbrooke.reparecampus.util.Constants.STATUS_WAITING

// Libellés en français pour l'affichage des états et rôles.
object Labels {
    fun status(code: String): String = when (code) {
        STATUS_TO_TRIAGE -> "À trier"
        STATUS_ASSIGNED -> "Assignée"
        STATUS_IN_DIAGNOSIS -> "En diagnostic"
        STATUS_WAITING -> "En attente"
        STATUS_IN_REPAIR -> "En réparation"
        STATUS_READY -> "Prête à récupérer"
        STATUS_CLOSED -> "Fermée"
        STATUS_CANCELLED -> "Annulée"
        STATUS_NOT_REPAIRABLE -> "Non réparable"
        else -> code
    }

    fun role(code: String): String = when (code) {
        Constants.ROLE_REQUESTER -> "Demandeur·euse"
        Constants.ROLE_REPAIRER -> "Réparateur·rice"
        Constants.ROLE_COORDINATOR -> "Coordination"
        Constants.ROLE_ADMIN -> "Administration"
        else -> code
    }

    fun priority(code: String): String = when (code) {
        "low" -> "Basse"
        "normal" -> "Normale"
        "high" -> "Haute"
        else -> code
    }

    // Tous les états, pour les filtres.
    val allStatuses = listOf(
        STATUS_TO_TRIAGE, STATUS_ASSIGNED, STATUS_IN_DIAGNOSIS, STATUS_WAITING,
        STATUS_IN_REPAIR, STATUS_READY, STATUS_CLOSED, STATUS_CANCELLED, STATUS_NOT_REPAIRABLE
    )
}
