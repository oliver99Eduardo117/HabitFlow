package com.example.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test de referencia para validación de migraciones de Room Database.
 * Sirve como plantilla para verificar migraciones futuras (ej. de v2 a v3)
 * usando MigrationTestHelper y los esquemas JSON generados en app/schemas/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun createDatabase_currentVersion_opensSuccessfully() {
        // Crea la base de datos en la versión actual (versión 3)
        val db = helper.createDatabase(TEST_DB, 4)
        assertNotNull(db)
        db.close()

        // Valida que volver a abrirla con Room.databaseBuilder no lance excepciones
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            TEST_DB
        )
        .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

        assertNotNull(appDb)
        val writableDb = appDb.openHelper.writableDatabase
        assertNotNull(writableDb)
        appDb.close()
    }

    @Test
    fun migrate2To3_addsLastMilestoneStreakClaimedColumn() {
        // Crea la base de datos en versión 2
        var db = helper.createDatabase(TEST_DB, 2)
        assertNotNull(db)
        db.close()

        // Ejecuta y valida la migración a versión 3
        db = helper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)
        assertNotNull(db)

        // Verifica que la nueva columna exista
        val cursor = db.query("PRAGMA table_info(habits)")
        var foundColumn = false
        while (cursor.moveToNext()) {
            val nameColumnIndex = cursor.getColumnIndex("name")
            if (nameColumnIndex >= 0 && cursor.getString(nameColumnIndex) == "lastMilestoneStreakClaimed") {
                foundColumn = true
                break
            }
        }
        cursor.close()
        assertTrue("Column lastMilestoneStreakClaimed should exist after migration", foundColumn)
        db.close()
    }

    @Test
    fun migrate3To4_createsSubTaskLogsAndBackfillsHistory() {
        var db = helper.createDatabase(TEST_DB, 3)
        db.execSQL("INSERT INTO habits (id, title, description, category, colorHex, iconName, frequencyDays, isArchived, orderIndex, unit, targetValue, progressiveIncrease, hasTimer, timerDurationMinutes, reminderMinutesAdvance, lastMilestoneStreakClaimed, createdAt) VALUES (1, 'Rutina', '', 'General', '#6366F1', 'check_circle', '1,2,3,4,5,6,7', 0, 0, '', 1.0, 0.0, 0, 25, 0, 0, 0)")
        db.execSQL("INSERT INTO sub_tasks (id, habitId, title, isCompleted, date) VALUES (10, 1, 'Agua', 1, '2026-09-28'), (11, 1, 'Estirar', 0, ''), (12, 1, 'Prioridades', 0, '')")
        db.execSQL("INSERT INTO habit_logs (habitId, date, value, notes, timestamp) VALUES (1, '2026-09-25', 1.0, '', 0), (1, '2026-09-26', 0.5, '', 0)")
        db.execSQL("INSERT INTO user_stats (id, xp, level, totalCheckIns, bestStreakAllTime, isHardcoreMode, unlockedBadgeIds, totalFocusMinutes, lastActiveDate) VALUES (1, 100, 1, 0, 0, 0, '', 0, '')")
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        fun count(sql: String): Int = db.query(sql).use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals(4, count("SELECT COUNT(*) FROM sub_task_logs"))
        assertEquals(3, count("SELECT COUNT(*) FROM sub_task_logs WHERE date = '2026-09-25'"))
        assertEquals(0, count("SELECT COUNT(*) FROM sub_task_logs WHERE date = '2026-09-26'"))
        assertEquals(1, count("SELECT COUNT(*) FROM sub_task_logs WHERE date = '2026-09-28'"))
        assertEquals(115, count("SELECT xp FROM user_stats WHERE id = 1"))
        db.close()
    }
}
