package ca.usherbrooke.reparecampus.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cache d'une demande. Les colonnes servent à l'affichage et au filtrage hors ligne ;
// `json` conserve la demande complète (RequestDto sérialisé) pour l'écran de détail.
@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    val status: String,
    val priority: String,
    val categoryName: String,
    val deviceType: String,
    val deviceBrand: String,
    val problemDescription: String,
    val requesterName: String,
    val assignedToName: String,
    val hasEstimate: Boolean,
    val updatedAt: String,
    val json: String
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slug: String,
    val description: String,
    val active: Boolean
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: String,
    val requestId: String?
)

// Brouillon de demande créé hors ligne, non encore envoyé au serveur.
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val categoryId: String,
    val categoryName: String,
    val deviceType: String,
    val deviceBrand: String,
    val deviceModel: String,
    val problemDescription: String,
    val priority: String,
    val contactValue: String,
    val createdAt: Long
)
