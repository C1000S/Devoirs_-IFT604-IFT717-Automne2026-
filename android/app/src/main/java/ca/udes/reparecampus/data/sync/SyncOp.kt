package ca.udes.reparecampus.data.sync

import ca.udes.reparecampus.data.remote.dto.CreateRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Action métier sur une demande existante. Sérialisable pour pouvoir être mise en file hors ligne. */
@Serializable
sealed interface RequestAction {
    @Serializable @SerialName("respond_estimate")
    data class RespondEstimate(val accept: Boolean) : RequestAction

    @Serializable @SerialName("change_status")
    data class ChangeStatus(val status: String, val reason: String? = null) : RequestAction

    @Serializable @SerialName("diagnosis")
    data class AddDiagnosis(val text: String) : RequestAction

    @Serializable @SerialName("estimate")
    data class ProposeEstimate(val cost: Double, val description: String) : RequestAction

    @Serializable @SerialName("intervention")
    data class AddIntervention(val description: String) : RequestAction

    /** [repairerName] ne sert qu'à l'affichage local avant synchronisation. */
    @Serializable @SerialName("assign")
    data class Assign(val repairerId: String, val repairerName: String? = null) : RequestAction
}

/** Opération de la file de synchronisation. */
@Serializable
sealed interface SyncOp {
    @Serializable @SerialName("request_action")
    data class OnRequest(val requestId: String, val action: RequestAction) : SyncOp

    @Serializable @SerialName("create_request")
    data class Create(val localId: String, val body: CreateRequestBody) : SyncOp

    @Serializable @SerialName("mark_read")
    data class MarkRead(val notificationId: String) : SyncOp
}

/** Résultat d'une opération : faite tout de suite, ou mise en file faute de réseau. */
enum class Outcome { DONE, QUEUED }
