package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.local.entity.CategoryEntity
import ca.usherbrooke.reparecampus.data.remote.ApiService
import kotlinx.coroutines.flow.Flow

class CategoryRepository(
    private val api: () -> ApiService,
    private val db: AppDatabase
) {
    private val dao = db.categoryDao()

    val categories: Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun refresh(): Result<Int> = runCatching {
        val list = api().categories()
        dao.upsert(
            list.map {
                CategoryEntity(
                    id = it.id,
                    name = it.name,
                    slug = it.slug,
                    description = it.description ?: "",
                    active = it.active
                )
            }
        )
        list.size
    }
}
