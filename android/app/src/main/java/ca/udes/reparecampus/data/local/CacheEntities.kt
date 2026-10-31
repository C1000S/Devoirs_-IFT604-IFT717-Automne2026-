package ca.udes.reparecampus.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Demande en cache. Le document complet (tel que renvoyé par l'API) est stocké en JSON ;
 * quelques colonnes servent au tri et à la gestion de la synchronisation.
 */
@Entity(tableName = "requests")
data class RequestEntity(
    @PrimaryKey val id: String,
    val json: String,
    val createdAt: String?,
    val updatedAt: String?,
    /** Le JSON contient l'historique complet (GET /:id) pour cette version de la demande. */
    val hasDetail: Boolean = false,
    /** Des actions locales sur cette demande attendent d'être envoyées. */
    val pendingSync: Boolean = false,
    /** Créée hors ligne : pas encore d'identifiant serveur. */
    val isLocal: Boolean = false,
)

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id")
    fun observe(id: String): Flow<RequestEntity?>

    @Query("SELECT * FROM requests WHERE id = :id")
    suspend fun get(id: String): RequestEntity?

    @Query("SELECT * FROM requests")
    suspend fun getAll(): List<RequestEntity>

    @Upsert suspend fun upsert(entity: RequestEntity)
    @Upsert suspend fun upsertAll(entities: List<RequestEntity>)

    @Query("DELETE FROM requests WHERE isLocal = 0")
    suspend fun deleteAllRemote()

    @Query("DELETE FROM requests WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE requests SET pendingSync = 0 WHERE id NOT IN (:stillPending)")
    suspend fun clearPendingExcept(stillPending: List<String>)

    @Query("DELETE FROM requests")
    suspend fun clear()
}

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val description: String)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert suspend fun upsertAll(entities: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val requestId: String?,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: String?,
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Upsert suspend fun upsertAll(entities: List<NotificationEntity>)

    @Query("SELECT id FROM notifications")
    suspend fun ids(): List<String>

    @Upsert suspend fun upsert(entity: NotificationEntity)

    @Query("UPDATE notifications SET read = 1 WHERE id IN (:ids)")
    suspend fun markRead(ids: List<String>)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

/** Opération en attente d'envoi à l'API (file d'actions hors ligne). */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SyncOp sérialisée en JSON. */
    val opJson: String,
    /** Description lisible, pour l'écran de synchronisation. */
    val label: String,
    val requestId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    /** "pending" ou "failed" (refusée par le serveur). */
    val state: String = STATE_PENDING,
    val error: String? = null,
) {
    companion object {
        const val STATE_PENDING = "pending"
        const val STATE_FAILED = "failed"
    }
}

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY id")
    fun observeAll(): Flow<List<PendingActionEntity>>

    @Query("SELECT * FROM pending_actions WHERE state = 'pending' ORDER BY id")
    suspend fun getPending(): List<PendingActionEntity>

    @Query("SELECT DISTINCT requestId FROM pending_actions WHERE state = 'pending' AND requestId IS NOT NULL")
    suspend fun pendingRequestIds(): List<String>

    @Insert suspend fun insert(action: PendingActionEntity): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_actions SET state = 'failed', error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM pending_actions WHERE state = 'failed'")
    suspend fun deleteFailed()

    @Query("DELETE FROM pending_actions")
    suspend fun clear()
}

/** Petites valeurs clé/valeur (date de dernière synchronisation…). */
@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert suspend fun put(entity: MetaEntity)

    @Query("DELETE FROM meta")
    suspend fun clear()
}
