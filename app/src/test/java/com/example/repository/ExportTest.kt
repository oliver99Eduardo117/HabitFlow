package com.example.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.database.AppDatabase
import com.example.model.Habit
import com.example.model.HabitLog
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportTest {

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `exportDataJson generates valid parseable JSON with correct structure`() = runTest {
        val habitId = repository.saveHabit(
            Habit(
                title = "Beber Agua",
                description = "2 litros al día",
                category = "Salud",
                unit = "L",
                targetValue = 2f
            )
        )
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = "2026-08-18",
                value = 2f,
                notes = "Cumplido temprano"
            )
        )

        val jsonString = repository.exportDataJson()
        assertNotNull(jsonString)
        assertTrue(jsonString.isNotEmpty())

        val rootObject = JSONObject(jsonString)
        assertEquals(2, rootObject.getInt("version"))
        assertTrue(rootObject.has("exportedAt"))

        val habitsArray = rootObject.getJSONArray("habits")
        assertEquals(1, habitsArray.length())
        val habitJson = habitsArray.getJSONObject(0)
        assertEquals("Beber Agua", habitJson.getString("title"))
        assertEquals("Salud", habitJson.getString("category"))

        val logsArray = rootObject.getJSONArray("logs")
        assertEquals(1, logsArray.length())
        val logJson = logsArray.getJSONObject(0)
        assertEquals("2026-08-18", logJson.getString("date"))
        assertEquals(2.0, logJson.getDouble("value"), 0.001)
        assertEquals("Cumplido temprano", logJson.getString("notes"))
    }

    @Test
    fun `exportDataCsv escapes commas and newlines in habit titles and log notes`() = runTest {
        val habitId = repository.saveHabit(
            Habit(
                title = "Yoga, Meditación y Respiración",
                category = "Salud, Bienestar",
                unit = "min",
                targetValue = 30f
            )
        )
        database.habitLogDao().insertOrUpdateLog(
            HabitLog(
                habitId = habitId,
                date = "2026-08-18",
                value = 30f,
                notes = "Línea 1 con coma, luego\nLínea 2 con otra coma, final."
            )
        )

        val csvString = repository.exportDataCsv()
        assertNotNull(csvString)

        val lines = csvString.trim().split("\n")
        // Should have header line + 1 data line (notes newlines replaced with space)
        assertEquals(2, lines.size)

        val header = lines[0]
        assertEquals("Fecha,Habito,Categoria,Valor,Unidad,Notas", header)

        val dataLine = lines[1]
        val columns = dataLine.split(",")
        // Since commas in title, category, and notes are replaced with spaces, there should be exactly 6 CSV columns
        assertEquals(6, columns.size)
        assertEquals("2026-08-18", columns[0])
        assertEquals("Yoga  Meditación y Respiración", columns[1])
        assertEquals("Salud  Bienestar", columns[2])
        assertEquals("30.0", columns[3])
        assertEquals("min", columns[4])
        // Notes must not contain newline
        assertFalse(columns[5].contains("\n"))
    }
}
