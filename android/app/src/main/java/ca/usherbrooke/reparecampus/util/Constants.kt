package ca.usherbrooke.reparecampus.util

object Constants {
    // 10.0.2.2 = machine hôte vue depuis l'émulateur Android.
    // Pour un appareil physique, remplacer par l'adresse IP locale du serveur.
    const val DEFAULT_BASE_URL = "http://10.0.2.2:3000/"

    const val PREFS_NAME = "repare_campus_session"

    // États du cycle de vie (doivent correspondre au serveur).
    const val STATUS_TO_TRIAGE = "to_triage"
    const val STATUS_ASSIGNED = "assigned"
    const val STATUS_IN_DIAGNOSIS = "in_diagnosis"
    const val STATUS_WAITING = "waiting"
    const val STATUS_IN_REPAIR = "in_repair"
    const val STATUS_READY = "ready_for_pickup"
    const val STATUS_CLOSED = "closed"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_NOT_REPAIRABLE = "not_repairable"

    // Rôles.
    const val ROLE_REQUESTER = "requester"
    const val ROLE_REPAIRER = "repairer"
    const val ROLE_COORDINATOR = "coordinator"
    const val ROLE_ADMIN = "admin"
}
