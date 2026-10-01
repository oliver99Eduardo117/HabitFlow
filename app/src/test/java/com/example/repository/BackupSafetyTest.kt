package com.example.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.model.HabitLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupSafetyTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: HabitRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HabitRepository(database, context)
        File(context.filesDir, "copia_antes_de_recuperar.json").delete()
        File(context.filesDir, "copia_antes_de_recuperar.tmp").delete()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `preview rejects json that is not a HabitFlow backup`() {
        assertTrue(repository.parseBackupPreview("{}").isFailure)
        assertTrue(repository.parseBackupPreview("{\"appName\":\"HabitFlow\"}").isFailure)
        assertTrue(repository.parseBackupPreview("{\"habits\":[]}").isFailure)
        assertTrue(repository.parseBackupPreview("[]").isFailure)
        assertTrue(repository.parseBackupPreview("no es json").isFailure)
        assertEquals(NOT_A_BACKUP_MESSAGE, repository.parseBackupPreview("{}").exceptionOrNull()?.message)
    }

    @Test
    fun `preview accepts an exported backup and keeps an unknown date at zero`() = runTest {
        repository.saveHabit(Habit(title = "Leer", category = "Estudio"))
        val json = repository.exportDataJson()

        val summary = repository.parseBackupPreview(json).getOrThrow()
        assertEquals(1, summary.habitsCount)
        assertTrue(summary.exportedAt > 0L)

        val withoutDate = JSONObject(json).apply { remove("exportedAt") }.toString()
        assertEquals(0L, repository.parseBackupPreview(withoutDate).getOrThrow().exportedAt)
    }

    @Test
    fun `current summary counts the same things as the export`() = runTest {
        val habitId = repository.saveHabit(Habit(title = "Correr", category = "Salud"))
        repository.saveHabit(Habit(title = "Viejo", category = "Salud", isArchived = true))
        database.habitLogDao().insertOrUpdateLog(HabitLog(habitId = habitId, date = "2026-09-29", value = 1f))

        val current = repository.currentDataSummary()
        val fromExport = repository.parseBackupPreview(repository.exportDataJson()).getOrThrow()

        assertEquals(2, current.habitsCount)
        assertEquals(1, current.logsCount)
        assertEquals(fromExport.habitsCount, current.habitsCount)
        assertEquals(fromExport.logsCount, current.logsCount)
        assertEquals(fromExport.subTasksCount, current.subTasksCount)
        assertEquals(fromExport.categoriesCount, current.categoriesCount)
    }

    @Test
    fun `restoring something that is not a backup changes nothing`() = runTest {
        repository.saveHabit(Habit(title = "Meditar", category = "Mente"))

        val result = repository.restoreWithSafetySnapshot("{}")

        assertTrue(result.isFailure)
        assertEquals(1, database.habitDao().getAllHabits().first().size)
        assertNull(repository.safetySnapshotTime())
    }

    @Test
    fun `restore keeps what was there before so it can be undone`() = runTest {
        repository.saveHabit(Habit(title = "A", category = "General"))
        val backupWithA = repository.exportDataJson()
        repository.saveHabit(Habit(title = "B", category = "General"))

        assertTrue(repository.restoreWithSafetySnapshot(backupWithA).isSuccess)
        assertEquals(listOf("A"), database.habitDao().getAllHabits().first().map { it.title })
        assertNotNull(repository.safetySnapshotTime())

        val snapshot = repository.readSafetySnapshot()
        assertNotNull(snapshot)
        assertEquals(2, repository.parseBackupPreview(snapshot!!).getOrThrow().habitsCount)

        // Deshacer: recuperar la copia interna devuelve los dos habitos
        assertTrue(repository.restoreWithSafetySnapshot(snapshot).isSuccess)
        assertEquals(setOf("A", "B"), database.habitDao().getAllHabits().first().map { it.title }.toSet())
    }

    @Test
    fun `a failed restore keeps the previous safety copy`() = runTest {
        repository.saveHabit(Habit(title = "A", category = "General"))
        val backup = repository.exportDataJson()
        assertTrue(repository.restoreWithSafetySnapshot(backup).isSuccess)
        val before = repository.readSafetySnapshot()

        assertTrue(repository.restoreWithSafetySnapshot("{}").isFailure)

        assertEquals(before, repository.readSafetySnapshot())
        assertTrue(!File(context.filesDir, "copia_antes_de_recuperar.tmp").exists())
    }
}
