package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("habitflow_backup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `starts with no backup`() {
        assertNull(BackupPreferences(context).lastBackupAt.value)
    }

    @Test
    fun `markBackupDone updates the flow and survives a new instance`() {
        val prefs = BackupPreferences(context)
        prefs.markBackupDone(1_758_000_000_000L)
        assertEquals(1_758_000_000_000L, prefs.lastBackupAt.value)
        assertEquals(1_758_000_000_000L, BackupPreferences(context).lastBackupAt.value)
    }
}
