package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.local.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

// Brouillons de demandes saisis hors ligne (file locale avant envoi au serveur).
class DraftRepository(db: AppDatabase) {
    private val dao = db.draftDao()

    val drafts: Flow<List<DraftEntity>> = dao.observeAll()

    suspend fun save(draft: DraftEntity): Long = dao.insert(draft)
    suspend fun delete(draft: DraftEntity) = dao.delete(draft)
}
