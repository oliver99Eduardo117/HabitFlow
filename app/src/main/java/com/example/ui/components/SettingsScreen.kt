package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ThemeMode
import com.example.model.ViewLayoutMode
import com.example.repository.RestoreSummary
import com.example.ui.theme.Motion
import com.example.util.DateUtils
import com.example.viewmodel.HabitUiState
import com.example.viewmodel.HabitViewModel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: HabitUiState,
    viewModel: HabitViewModel,
    onOpenThemeDialog: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onOpenArchivedHabits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var pendingRestoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var showManualJsonDialog by remember { mutableStateOf(false) }
    var manualJsonText by remember { mutableStateOf("") }
    var showSuccessToastMsg by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // File Picker for Backup (Export JSON)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    isExporting = true
                    val jsonContent = viewModel.getExportJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    showSuccessToastMsg = "Copia de seguridad guardada con éxito en el archivo seleccionado."
                    Toast.makeText(context, "Copia de seguridad guardada", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    // File Picker for Restore (Import JSON)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonContent = context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }

                    if (!jsonContent.isNullOrBlank()) {
                        val previewResult = viewModel.parseBackupPreview(jsonContent)
                        previewResult.onSuccess { summary ->
                            pendingRestoreJson = jsonContent
                            pendingRestoreSummary = summary
                        }.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Archivo no válido: ${error.localizedMessage ?: "Formato JSON incompatible"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(context, "El archivo seleccionado está vacío", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al leer archivo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ajustes & Copias de Seguridad",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Preserva tu historial y personaliza el funcionamiento de HabitFlow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. BACKUP & RESTORE HERO CARD (Primary Feature)
        item {
            BackupAndRestoreCard(
                uiState = uiState,
                isExporting = isExporting,
                onBackupClick = {
                    val defaultFileName = "habitflow_backup_${DateUtils.getTodayDateString().replace("-", "")}.json"
                    createDocumentLauncher.launch(defaultFileName)
                },
                onRestoreClick = {
                    openDocumentLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                onShareBackup = {
                    scope.launch {
                        try {
                            val jsonContent = viewModel.getExportJson()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Copia de Seguridad HabitFlow")
                                putExtra(Intent.EXTRA_TEXT, jsonContent)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir Copia JSON"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCopyJson = {
                    scope.launch {
                        try {
                            val jsonContent = viewModel.getExportJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("HabitFlow Backup", jsonContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "JSON copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenManualRestore = {
                    manualJsonText = ""
                    showManualJsonDialog = true
                }
            )
        }

        // 3. Database Statistics Card
        item {
            LocalDatabaseStatsCard(uiState = uiState)
        }

        // 4. Appearance & Themes Section
        item {
            AppearanceSettingsCard(
                themeMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
                layoutMode = uiState.layoutMode,
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onToggleDynamicColor = { viewModel.setDynamicColor(it) },
                onSelectLayoutMode = { viewModel.setLayoutMode(it) },
                onOpenVisualCustomization = onOpenThemeDialog
            )
        }

        // 5. System Management & Tools
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "GESTIÓN & HERRAMIENTAS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingsActionRow(
                        title = "Gestión de Categorías",
                        subtitle = "${uiState.categories.size} categorías configuradas",
                        icon = Icons.Default.Category,
                        testTag = "settings_manage_categories",
                        onClick = onOpenManageCategories
                    )

                    SettingsActionRow(
                        title = "Hábitos Archivados",
                        subtitle = "${uiState.archivedHabits.size} hábitos en pausa",
                        icon = Icons.Default.Archive,
                        testTag = "settings_archived_habits",
                        onClick = onOpenArchivedHabits
                    )

                    SettingsActionRow(
                        title = "Proveedor de Inteligencia Artificial",
                        subtitle = "Gemini API, OpenAI o IA local",
                        icon = Icons.Default.SmartToy,
                        testTag = "settings_ai_config",
                        onClick = onOpenAiSettings
                    )

                    SettingsActionRow(
                        title = "Resincronizar Recordatorios",
                        subtitle = "Reconstruir todas las alarmas de hábitos",
                        icon = Icons.Default.NotificationsActive,
                        testTag = "settings_resync_reminders",
                        onClick = { viewModel.rescheduleAllReminders() }
                    )
                }
            }
        }

        // 6. Offline-First Privacy Guarantee Footer
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Privacidad 100% Local (Room Database)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tus datos viven únicamente en este dispositivo en SQLite. Usa los botones de Backup y Restore para crear copias independientes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // RESTORE CONFIRMATION DIALOG
    if (pendingRestoreJson != null && pendingRestoreSummary != null) {
        val summary = pendingRestoreSummary!!
        val jsonToRestore = pendingRestoreJson!!

        RestoreConfirmationDialog(
            summary = summary,
            onDismiss = {
                pendingRestoreJson = null
                pendingRestoreSummary = null
            },
            onConfirmRestore = {
                viewModel.restoreDatabaseFromJson(jsonToRestore) {
                    pendingRestoreJson = null
                    pendingRestoreSummary = null
                }
            }
        )
    }

    // MANUAL JSON PASTE RESTORE DIALOG
    if (showManualJsonDialog) {
        ManualJsonPasteDialog(
            initialText = manualJsonText,
            onDismiss = { showManualJsonDialog = false },
            onValidateAndRestore = { rawJson ->
                showManualJsonDialog = false
                val preview = viewModel.parseBackupPreview(rawJson)
                preview.onSuccess { summary ->
                    pendingRestoreJson = rawJson
                    pendingRestoreSummary = summary
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        "Error de formato JSON: ${err.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

/**
 * High-visibility Backup & Restore Card with primary interactive triggers.
 */
@Composable
private fun BackupAndRestoreCard(
    uiState: HabitUiState,
    isExporting: Boolean,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onShareBackup: () -> Unit,
    onCopyJson: () -> Unit,
    onOpenManualRestore: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_restore_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Copias de Seguridad (JSON)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exporta o restaura tu base de datos completa",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "Guarda todos tus hábitos, registros diarios, rachas, categorías y XP acumulado en un archivo estándar JSON. Puedes restaurarlo en cualquier momento sin depender de servidores externos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 20.sp
            )

            // Primary Action Buttons (Backup & Restore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // BACKUP BUTTON
                Button(
                    onClick = onBackupClick,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("backup_button")
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Backup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                // RESTORE BUTTON
                FilledTonalButton(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("restore_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restore",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Secondary Quick Actions (Share, Copy, Manual Paste)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onShareBackup,
                    modifier = Modifier.testTag("share_backup_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartir JSON", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = onCopyJson,
                    modifier = Modifier.testTag("copy_json_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copiar texto", style = MaterialTheme.typography.labelMedium)
                }

                TextButton(
                    onClick = onOpenManualRestore,
                    modifier = Modifier.testTag("manual_paste_restore_button")
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pegar JSON", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Displays live counts of Room database entities.
 */
@Composable
private fun LocalDatabaseStatsCard(uiState: HabitUiState) {
    val totalCheckIns by animateIntAsState(
        targetValue = uiState.allLogs.size,
        animationSpec = Motion.springValues(),
        label = "totalCheckIns"
    )
    val totalHabits by animateIntAsState(
        targetValue = uiState.habits.size,
        animationSpec = Motion.springValues(),
        label = "totalHabits"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ESTADO DE LA BASE DE DATOS LOCAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SQLite v3 Activo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCounterPill(
                    label = "Hábitos Activos",
                    value = "$totalHabits",
                    icon = Icons.Default.CheckCircle,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.weight(1f)
                )

                StatCounterPill(
                    label = "Check-ins Registrados",
                    value = "$totalCheckIns",
                    icon = Icons.Default.History,
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.weight(1f)
                )

                StatCounterPill(
                    label = "Nivel / XP",
                    value = "Lv.${uiState.userStats.level}",
                    icon = Icons.Default.EmojiEvents,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCounterPill(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Appearance settings card (Theme, Dynamic Colors, Default Layout).
 */
@Composable
private fun AppearanceSettingsCard(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    layoutMode: ViewLayoutMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectLayoutMode: (ViewLayoutMode) -> Unit,
    onOpenVisualCustomization: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "APARIENCIA Y TEMA",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onOpenVisualCustomization,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Personalizar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Theme Mode Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ThemeChip(
                    label = "Claro",
                    icon = Icons.Default.LightMode,
                    isSelected = themeMode == ThemeMode.LIGHT,
                    onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )

                ThemeChip(
                    label = "Oscuro",
                    icon = Icons.Default.DarkMode,
                    isSelected = themeMode == ThemeMode.DARK,
                    onClick = { onSelectThemeMode(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )

                ThemeChip(
                    label = "Sistema",
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = themeMode == ThemeMode.SYSTEM,
                    onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Material You Switch (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = Color(0xFFEC4899),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Colores Dinámicos (Material You)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Extraer tonalidades del fondo de pantalla",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                        modifier = Modifier.testTag("settings_dynamic_color_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (isSelected) null else CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Confirmation dialog before overwriting the Room database with a restored JSON backup.
 */
@Composable
private fun RestoreConfirmationDialog(
    summary: RestoreSummary,
    onDismiss: () -> Unit,
    onConfirmRestore: () -> Unit
) {
    val formattedDate = remember(summary.exportedAt) {
        if (summary.exportedAt > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(summary.exportedAt))
        } else "Fecha no especificada"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Restaurar Copia de Seguridad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Se ha validado el archivo de copia de seguridad con la siguiente información:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RestoreSummaryItem("Hábitos:", "${summary.habitsCount} hábitos")
                        RestoreSummaryItem("Registros históricos:", "${summary.logsCount} check-ins")
                        RestoreSummaryItem("Subtareas:", "${summary.subTasksCount} subtareas")
                        RestoreSummaryItem("Categorías:", "${summary.categoriesCount} categorías")
                        RestoreSummaryItem("Nivel / XP:", "Lv.${summary.userLevel} (${summary.userXp} XP)")
                        RestoreSummaryItem("Fecha del respaldo:", formattedDate)
                    }
                }

                Text(
                    text = "ADVERTENCIA: Esta acción reemplazará la base de datos local actual con los datos del respaldo seleccionado.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRestore,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_restore_dialog_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirmar y Restaurar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun RestoreSummaryItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Modal dialog for pasting raw JSON backup text manually.
 */
@Composable
private fun ManualJsonPasteDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onValidateAndRestore: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Pegar Copia JSON",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Pega aquí el contenido de un archivo de copia de seguridad JSON generado previamente por HabitFlow:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("manual_json_text_input"),
                    placeholder = { Text("{\n  \"appName\": \"HabitFlow\",\n  \"habits\": [...]\n}") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onValidateAndRestore(text.trim())
                            }
                        },
                        enabled = text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("validate_manual_json_button")
                    ) {
                        Text("Validar y Continuar")
                    }
                }
            }
        }
    }
}
