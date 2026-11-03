package ca.usherbrooke.reparecampus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ca.usherbrooke.reparecampus.data.local.dao.CategoryDao
import ca.usherbrooke.reparecampus.data.local.dao.DraftDao
import ca.usherbrooke.reparecampus.data.local.dao.NotificationDao
import ca.usherbrooke.reparecampus.data.local.dao.RequestDao
import ca.usherbrooke.reparecampus.data.local.entity.CategoryEntity
import ca.usherbrooke.reparecampus.data.local.entity.DraftEntity
import ca.usherbrooke.reparecampus.data.local.entity.NotificationEntity
import ca.usherbrooke.reparecampus.data.local.entity.RequestEntity

@Database(
    entities = [
        RequestEntity::class,
        CategoryEntity::class,
        NotificationEntity::class,
        DraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun requestDao(): RequestDao
    abstract fun categoryDao(): CategoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun draftDao(): DraftDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "repare-campus.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
