package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CRITERIO DE EVOLUCIÓN Y MIGRACIÓN DEL ESQUEMA:
 * - Cualquier cambio futuro en las entidades (agregar/eliminar campos, crear nuevas tablas,
 *   modificar tipos de datos o relaciones de clave foránea) DEBE venir acompañado de un
 *   objeto Migration explícito (ejemplo: MIGRATION_2_3 = Migration(2, 3) { ... }) registrado
 *   mediante .addMigrations(MIGRATION_X_Y) en el builder de la base de datos.
 * - NO usar ni depender de fallbackToDestructiveMigration() en actualizaciones normales (upgrades),
 *   para garantizar la preservación de los datos históricos del usuario (hábitos, logs diarios,
 *   rachas, puntos XP, estadísticas y logros desbloqueados).
 * - El fallback destructivo solo está permitido ante degradaciones de versión
 *   (.fallbackToDestructiveMigrationOnDowngrade()).
 * - Cada versión de esquema se exporta automáticamente como JSON en app/schemas/ para validación
 *   y testing automatizado de migraciones.
 */
@Database(
    entities = [
        Habit::class,
        HabitLog::class,
        SubTask::class,
        Category::class,
        UserStats::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun subTaskDao(): SubTaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userStatsDao(): UserStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN lastMilestoneStreakClaimed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habitflow_database.db"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Pre-populate defaults
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            database.categoryDao().insertCategories(DefaultCategories)
                            database.userStatsDao().insertOrUpdate(
                                UserStats(
                                    id = 1,
                                    xp = 120,
                                    level = 1,
                                    totalCheckIns = 0,
                                    bestStreakAllTime = 0,
                                    isHardcoreMode = false
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
