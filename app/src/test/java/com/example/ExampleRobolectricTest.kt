package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ThemeMode
import com.example.util.ThemePreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("HabitFlow", appName)
  }

  @Test
  fun `theme preferences saving and retrieval`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val themePrefs = ThemePreferences.getInstance(context)

    themePrefs.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, themePrefs.themeMode.value)

    themePrefs.setThemeMode(ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, themePrefs.themeMode.value)

    themePrefs.setThemeMode(ThemeMode.SYSTEM)
    assertEquals(ThemeMode.SYSTEM, themePrefs.themeMode.value)
  }
}
