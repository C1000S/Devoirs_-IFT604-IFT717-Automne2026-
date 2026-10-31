package ca.udes.reparecampus.data.model

/** États d'une demande (REQUEST_STATUSES du serveur) et transitions autorisées (ALLOWED_TRANSITIONS). */
enum class RequestStatus(val apiValue: String, val label: String, val isFinal: Boolean = false) {
    TO_TRIAGE("to_triage", "À trier"),
    ASSIGNED("assigned", "Affectée"),
    IN_DIAGNOSIS("in_diagnosis", "En diagnostic"),
    WAITING("waiting", "En attente"),
    IN_REPAIR("in_repair", "En réparation"),
    READY_FOR_PICKUP("ready_for_pickup", "Prête à récupérer"),
    CLOSED("closed", "Fermée", isFinal = true),
    CANCELLED("cancelled", "Annulée", isFinal = true),
    NOT_REPAIRABLE("not_repairable", "Non réparable", isFinal = true);

    /** Copie de ALLOWED_TRANSITIONS (src/constants.js). Le serveur reste l'autorité. */
    val allowedTransitions: List<RequestStatus>
        get() = when (this) {
            TO_TRIAGE -> listOf(ASSIGNED, CANCELLED)
            ASSIGNED -> listOf(IN_DIAGNOSIS, CANCELLED)
            IN_DIAGNOSIS -> listOf(WAITING, IN_REPAIR, NOT_REPAIRABLE, CANCELLED)
            WAITING -> listOf(IN_REPAIR, NOT_REPAIRABLE, CANCELLED)
            IN_REPAIR -> listOf(READY_FOR_PICKUP, NOT_REPAIRABLE)
            READY_FOR_PICKUP -> listOf(CLOSED)
            CLOSED, CANCELLED, NOT_REPAIRABLE -> emptyList()
        }

    companion object {
        fun fromApi(value: String?): RequestStatus = entries.firstOrNull { it.apiValue == value } ?: TO_TRIAGE
    }
}

enum class Priority(val apiValue: String, val label: String) {
    LOW("low", "Basse"),
    NORMAL("normal", "Normale"),
    HIGH("high", "Haute");

    companion object {
        fun fromApi(value: String?): Priority = entries.firstOrNull { it.apiValue == value } ?: NORMAL
    }
}
