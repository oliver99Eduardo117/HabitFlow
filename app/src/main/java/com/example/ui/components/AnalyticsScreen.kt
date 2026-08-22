package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.ThemeMode
import com.example.util.DateUtils
import com.example.util.WeeklySuccessStats
import kotlinx.coroutines.launch

@Composable
fun AnalyticsScreen(
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    insights: List<String>,
    isLoadingInsights: Boolean = false,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onExportJson: suspend () -> String,
    onExportCsv: suspend () -> String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }
    var exportType by remember { mutableStateOf("JSON") }
    var isCloudSynced by remember { mutableStateOf(true) }

    // Calculate real-time weekly success rate and analytics
    val weeklyStats = remember(habits, allLogs) {
        DateUtils.calculateWeeklySuccessRate(habits, allLogs)
    }

    val animatedOverallPercentage by animateIntAsState(
        targetValue = weeklyStats.overallPercentage,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "weekly_percentage"
    )

    val animatedProgressFraction by animateFloatAsState(
        targetValue = weeklyStats.overallPercentage / 100f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "progress_fraction"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Smart Insights Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF6366F1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Análisis & IA de Hábitos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Optimización de rutinas y correlaciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isLoadingInsights,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6366F1)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoadingInsights && insights.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Analizando tus patrones...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    insights.forEach { insight ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = insight,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Weekly Success Rate & Consistency Visual Analysis Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("weekly_success_rate_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Porcentaje de Éxito Semanal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Efectividad en los últimos 7 días",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delta comparison badge with previous week
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            weeklyStats.percentageDelta > 0 -> Color(0xFF10B981).copy(alpha = 0.15f)
                            weeklyStats.percentageDelta < 0 -> Color(0xFFF43F5E).copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    weeklyStats.percentageDelta > 0 -> Icons.Default.TrendingUp
                                    weeklyStats.percentageDelta < 0 -> Icons.Default.TrendingDown
                                    else -> Icons.Default.TrendingFlat
                                },
                                contentDescription = null,
                                tint = when {
                                    weeklyStats.percentageDelta > 0 -> Color(0xFF10B981)
                                    weeklyStats.percentageDelta < 0 -> Color(0xFFF43F5E)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when {
                                    weeklyStats.percentageDelta > 0 -> "+${weeklyStats.percentageDelta}%"
                                    weeklyStats.percentageDelta < 0 -> "${weeklyStats.percentageDelta}%"
                                    else -> "0%"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    weeklyStats.percentageDelta > 0 -> Color(0xFF10B981)
                                    weeklyStats.percentageDelta < 0 -> Color(0xFFF43F5E)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Hero Circular Progress Gauge & Summary Metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Circular Progress Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(100.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgressFraction },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 10.dp,
                            strokeCap = StrokeCap.Round,
                            color = when {
                                animatedOverallPercentage >= 80 -> Color(0xFF10B981)
                                animatedOverallPercentage >= 50 -> Color(0xFF6366F1)
                                animatedOverallPercentage > 0 -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$animatedOverallPercentage%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("weekly_success_percentage")
                            )
                            Text(
                                text = "Éxito",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Key Metrics Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Metric 1: Completions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Text("Completados:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${weeklyStats.totalCompletions} de ${weeklyStats.totalScheduled}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Metric 2: Perfect Days
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Text("Días Perfectos:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${weeklyStats.perfectDaysCount} de 7",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Metric 3: Best Day
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(16.dp))
                                Text("Mejor Día:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                weeklyStats.bestDayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Desglose Diario de Rendimiento",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Daily Breakdown Bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyStats.dailyBreakdown.forEach { day ->
                        val ratio = (day.successRate / 100f).coerceIn(0f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Completion Rate or Ratio Text
                            Text(
                                text = "${day.successRate.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (day.successRate >= 100f) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Vertical Progress Bar
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((ratio * 80).coerceAtLeast(6f).dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        when {
                                            day.successRate >= 100f -> Brush.verticalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                                            day.successRate >= 60f -> Brush.verticalGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                                            day.successRate > 0f -> Brush.verticalGradient(listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
                                            else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = day.dayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (day.successRate >= 100f) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Monthly Compliance Trend Line Chart Component
        MonthlyTrendLineChart(
            habits = habits,
            allLogs = allLogs
        )

        // Theme & Visual Appearance Setting Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            },
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tema & Apariencia",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = themeMode.displayName + " • " + themeMode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector Segmented Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Claro") },
                        modifier = Modifier.weight(1f).testTag("analytics_theme_light")
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onSelectThemeMode(ThemeMode.DARK) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Oscuro") },
                        modifier = Modifier.weight(1f).testTag("analytics_theme_dark")
                    )
                    FilterChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.BrightnessAuto,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Sistema") },
                        modifier = Modifier.weight(1f).testTag("analytics_theme_system")
                    )
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Colores Dinámicos Material You",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Adaptar a la paleta del fondo de pantalla",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = onToggleDynamicColor,
                            modifier = Modifier.testTag("analytics_dynamic_color_switch")
                        )
                    }
                }
            }
        }

        // Cloud Synchronization Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF06B6D4).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sincronización Cloud",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isCloudSynced) "Base de datos en la nube activa" else "Modo local offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isCloudSynced,
                    onCheckedChange = { isCloudSynced = it }
                )
            }
        }

        // Data Export & Backup Section
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Copia de Seguridad y Exportación",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Exporta tus datos a CSV (Excel) o JSON seguro",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                exportContent = onExportCsv()
                                exportType = "CSV (Excel / Sheets)"
                                showExportDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar CSV")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                exportContent = onExportJson()
                                exportType = "JSON Backup"
                                showExportDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup JSON")
                    }
                }
            }
        }
    }

    // Export Dialog with Copy to Clipboard
    if (showExportDialog) {
        Dialog(onDismissRequest = { showExportDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exportación: $exportType",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showExportDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = exportContent,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("HabitFlow Export", exportContent)
                            clipboard.setPrimaryClip(clip)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar al Portapapeles")
                    }
                }
            }
        }
    }
}
