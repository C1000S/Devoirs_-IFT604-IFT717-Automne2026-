package ca.udes.reparecampus.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject

/**
 * Référence vers un utilisateur. L'API renvoie soit un objet peuplé
 * ({ _id, name, email }) dans les listes et le détail, soit un simple
 * ObjectId (texte) dans les réponses POST/PATCH : [UserRefSerializer] accepte les deux.
 */
@Serializable
data class UserRef(
    @SerialName("_id") val id: String,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
)

object UserRefSerializer : JsonTransformingSerializer<UserRef>(UserRef.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonPrimitive) buildJsonObject { put("_id", element) } else element
}

@Serializable
data class Device(
    val type: String = "",
    val brand: String = "",
    val model: String = "",
) {
    val label: String
        get() = listOf(type.replaceFirstChar { it.titlecase() }, brand, model).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class Diagnosis(
    val text: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class Estimate(
    val cost: Double? = null,
    val description: String? = null,
    val status: String? = null,
    val respondedAt: String? = null,
)

@Serializable
data class Intervention(
    @SerialName("_id") val id: String? = null,
    val description: String = "",
    @Serializable(with = UserRefSerializer::class) val performedBy: UserRef? = null,
    val performedAt: String? = null,
)

@Serializable
data class RepairEvent(
    @SerialName("_id") val id: String? = null,
    val type: String = "",
    val message: String = "",
    @Serializable(with = UserRefSerializer::class) val actor: UserRef? = null,
    val createdAt: String? = null,
)

/** Demande de réparation (RepairRequest côté serveur). [events] n'est rempli que par GET /:id. */
@Serializable
data class RepairRequest(
    @SerialName("_id") val id: String,
    @Serializable(with = UserRefSerializer::class) val requester: UserRef? = null,
    val category: String = "",
    val device: Device = Device(),
    val problemDescription: String = "",
    val contactMethod: String = "",
    val status: String = RequestStatus.TO_TRIAGE.apiValue,
    val priority: String = "normal",
    @Serializable(with = UserRefSerializer::class) val assignedTo: UserRef? = null,
    val diagnosis: Diagnosis? = null,
    val estimate: Estimate? = null,
    val interventions: List<Intervention> = emptyList(),
    val closedReason: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val events: List<RepairEvent> = emptyList(),
    /** Modifications locales pas encore envoyées au serveur (non sérialisé). */
    @Transient val pendingSync: Boolean = false,
    /** Créée hors ligne, sans identifiant serveur (non sérialisé). */
    @Transient val isLocal: Boolean = false,
) {
    val statusEnum: RequestStatus get() = RequestStatus.fromApi(status)
    val hasEstimate: Boolean get() = estimate?.cost != null
    val hasPendingEstimate: Boolean get() = hasEstimate && estimate?.status == "pending"
}

@Serializable
data class Category(
    @SerialName("_id") val id: String,
    val name: String,
    val description: String = "",
)

@Serializable
data class AppNotification(
    @SerialName("_id") val id: String,
    val request: String? = null,
    val title: String = "",
    val message: String = "",
    val read: Boolean = false,
    val createdAt: String? = null,
)

