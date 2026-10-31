package ca.udes.reparecampus.data.repository

import ca.udes.reparecampus.data.local.DraftDao
import ca.udes.reparecampus.data.local.DraftEntity
import kotlinx.coroutines.flow.Flow

class DraftRepository(private val dao: DraftDao) {
    fun observeDrafts(userId: String): Flow<List<DraftEntity>> = dao.observeAll(userId)
    suspend fun get(id: Long): DraftEntity? = dao.get(id)

    /** Insère ou met à jour ; retourne l'identifiant du brouillon. */
    suspend fun save(draft: DraftEntity): Long {
        val stamped = draft.copy(updatedAt = System.currentTimeMillis())
        val rowId = dao.upsert(stamped)
        return if (draft.id == 0L) rowId else draft.id
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
