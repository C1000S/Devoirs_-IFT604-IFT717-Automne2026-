package ca.udes.reparecampus.domain

/** Rôles exposés par l'API (voir ROLES dans src/constants.js du serveur). */
enum class Role(val apiValue: String, val label: String) {
    REQUESTER("requester", "Demandeur·euse"),
    REPAIRER("repairer", "Réparateur·rice"),
    COORDINATOR("coordinator", "Coordination"),
    ADMIN("admin", "Administration"),
    UNKNOWN("unknown", "Inconnu");

    companion object {
        fun fromApi(value: String?): Role = entries.firstOrNull { it.apiValue == value } ?: UNKNOWN
    }
}
