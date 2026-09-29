package com.optisuite.optiplay.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        VideoProgressEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        /** v1 → v2: tabla de progreso de vídeos. Conserva listas, favoritos e historial. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_progress` (`uri` TEXT NOT NULL, " +
                        "`positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uri`))"
                )
            }
        }

        // Sin fallbackToDestructiveMigration: una actualización NUNCA debe borrar las listas del usuario.
        // Cada cambio de esquema necesita su Migration (el esquema se exporta a app/schemas).
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "optiplay.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
