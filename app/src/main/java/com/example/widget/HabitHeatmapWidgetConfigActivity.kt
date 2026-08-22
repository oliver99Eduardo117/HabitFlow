package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.example.model.HabitWithStats
import com.example.repository.HabitRepository
import com.example.ui.theme.HabitFlowTheme
import com.example.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HabitHeatmapWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Read appWidgetId from intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Set default result to CANCELED in case user cancels or navigates back
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        val repository = WidgetRepositoryProvider.getRepository(this)

        setContent {
            HabitFlowTheme(darkTheme = false) {
                HabitHeatmapWidgetConfigScreen(
                    repository = repository,
                    onClose = { finish() },
                    onHabitSelected = { habitId ->
                        selectHabitAndFinish(habitId)
                    }
                )
            }
        }
    }

    private fun selectHabitAndFinish(habitId: Long) {
        lifecycleScope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(this@HabitHeatmapWidgetConfigActivity)
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)

                updateAppWidgetState(
                    this@HabitHeatmapWidgetConfigActivity,
                    PreferencesGlanceStateDefinition,
                    glanceId
                ) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[longPreferencesKey("selected_habit_id")] = habitId
                    }
                }
                ConsistencyWidget().update(this@HabitHeatmapWidgetConfigActivity, glanceId)
            } catch (_: Exception) { }

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitHeatmapWidgetConfigScreen(
    repository: HabitRepository,
    onClose: () -> Unit,
    onHabitSelected: (Long) -> Unit
) {
    val today = remember { DateUtils.getTodayDateString() }
    var habitsList by remember { mutableStateOf<List<HabitWithStats>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            habitsList = repository.getHabitsWithStats(today).first()
        } catch (_: Exception) {
            habitsList = emptyList()
        } finally {
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Selecciona un Hábito",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Para el mapa de constancia del widget",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (habitsList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No hay hábitos activos disponibles",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Crea un hábito en la app antes de agregar este widget.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(habitsList, key = { it.habit.id }) { item ->
                            HabitPickerCard(
                                habitWithStats = item,
                                onSelect = { selectedHabitId ->
                                    onHabitSelected(selectedHabitId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitPickerCard(
    habitWithStats: HabitWithStats,
    onSelect: (Long) -> Unit
) {
    val habit = habitWithStats.habit
    val habitColor = try {
        Color(android.graphics.Color.parseColor(habit.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(habit.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(habitColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = habit.title.take(1).uppercase(),
                    color = habitColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = habit.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (habitWithStats.currentStreak > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${habitWithStats.currentStreak} días",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF97316),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
