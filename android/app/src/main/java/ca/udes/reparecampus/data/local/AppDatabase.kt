package ca.udes.reparecampus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DraftEntity::class,
        RequestEntity::class,
        CategoryEntity::class,
        NotificationEntity::class,
        PendingActionEntity::class,
        MetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun requestDao(): RequestDao
    abstract fun categoryDao(): CategoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun metaDao(): MetaDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "reparecampus.db")
                // Projet de cours : pas de migrations écrites, le schéma est recréé s'il change.
                .fallbackToDestructiveMigration()
                .build()
    }
}
