package ca.usherbrooke.reparecampus.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.usherbrooke.reparecampus.data.local.entity.CategoryEntity
import ca.usherbrooke.reparecampus.data.local.entity.DraftEntity
import ca.usherbrooke.reparecampus.data.local.entity.NotificationEntity
import ca.usherbrooke.reparecampus.data.local.entity.RequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RequestDao {
    @Query("SELECT * FROM requests ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM requests WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(requests: List<RequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(request: RequestEntity)

    @Query("DELETE FROM requests")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notifications: List<NotificationEntity>)

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DraftEntity>>

    @Insert
    suspend fun insert(draft: DraftEntity): Long

    @Delete
    suspend fun delete(draft: DraftEntity)

    @Query("SELECT COUNT(*) FROM drafts")
    suspend fun count(): Int
}
