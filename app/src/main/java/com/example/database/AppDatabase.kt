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
        UserStats::class,
        SubTaskLog::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun subTaskDao(): SubTaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun subTaskLogDao(): SubTaskLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN lastMilestoneStreakClaimed INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sub_task_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subTaskId` INTEGER NOT NULL, `habitId` INTEGER NOT NULL, `date` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sub_task_logs_subTaskId_date` ON `sub_task_logs` (`subTaskId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sub_task_logs_habitId_date` ON `sub_task_logs` (`habitId`, `date`)")

                // 1. Estado heredado de sub_tasks (ese XP ya se otorgo en su momento)
                db.execSQL("INSERT OR IGNORE INTO sub_task_logs (subTaskId, habitId, date) SELECT id, habitId, date FROM sub_tasks WHERE isCompleted = 1 AND date != ''")
                val before = countSubTaskLogs(db)

                // 2. Dias en que un habito sin unidad quedo completado: todas sus sub-rutinas cuentan como hechas
                db.execSQL(
                    "INSERT OR IGNORE INTO sub_task_logs (subTaskId, habitId, date) " +
                        "SELECT st.id, st.habitId, hl.date FROM sub_tasks st " +
                        "JOIN habits h ON h.id = st.habitId " +
                        "JOIN habit_logs hl ON hl.habitId = st.habitId " +
                        "WHERE h.unit = '' AND hl.value >= h.targetValue"
                )
                val added = countSubTaskLogs(db) - before

                // 3. Acreditar el XP de las filas agregadas en el paso 2, para que el XP siga siendo funcion del estado
                if (added > 0) {
                    db.query("SELECT xp FROM user_stats WHERE id = 1").use { cursor ->
                        if (cursor.moveToFirst()) {
                            val newXp = cursor.getInt(0) + added * GamificationConfig.XP_SUBTASK_COMPLETION
                            db.execSQL(
                                "UPDATE user_stats SET xp = ?, level = ? WHERE id = 1",
                                arrayOf<Any>(newXp, GamificationConfig.calculateLevel(newXp))
                            )
                        }
                    }
                }
            }

            private fun countSubTaskLogs(db: SupportSQLiteDatabase): Int =
                db.query("SELECT COUNT(*) FROM sub_task_logs").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "habitflow_database.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
