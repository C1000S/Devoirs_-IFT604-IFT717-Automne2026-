package ca.udes.reparecampus.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Brouillon de demande, enregistré localement au fil de la saisie (par personne). */
@Entity(tableName = "drafts", indices = [Index("userId")])
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val category: String = "",
    val deviceType: String = "",
    val brand: String = "",
    val model: String = "",
    val problemDescription: String = "",
    val priority: String = "normal",
    val contactMethod: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeAll(userId: String): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun get(id: Long): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity): Long

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: Long)
}
