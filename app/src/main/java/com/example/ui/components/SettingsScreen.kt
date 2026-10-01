package com.example.ui.components

import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.model.ThemeMode
import com.example.model.ViewLayoutMode
import com.example.repository.NOT_A_BACKUP_MESSAGE
import com.example.repository.RestoreSummary
import com.example.util.BackupFileSharer
import com.example.util.BackupStatus
import com.example.viewmodel.HabitUiState
import com.example.viewmodel.HabitViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Si los avisos de los habitos pueden llegar, y a tiempo. */
private enum class ReminderHealth {
    OK,

    /** Android tiene apagadas las notificaciones de la app. */
    NOTIFICATIONS_OFF,

    /** Android 12 o mas sin permiso de alarmas exactas: los avisos pueden llegar tarde. */
    EXACT_ALARMS_OFF
}

/**
 * Pestana Ajustes (Propuesta A, "lista con estado").
 *
 * Arriba va el estado de la copia de seguridad; abajo, grupos cortos con un color cada uno.
 * "Copias de seguridad" abre su propia pantalla aqui mismo (atras regresa).
 * El trabajo con archivos lo hace HabitViewModel; esta pantalla solo emite eventos.
 */
@Composable
fun SettingsScreen(
    uiState: HabitUiState,
    viewModel: HabitViewModel,
    onOpenAiSettings: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onOpenArchivedHabits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val safetySnapshotAt by viewModel.safetySnapshotAt.collectAsStateWithLifecycle()
    val aiConfigured by viewModel.aiConfigured.collectAsStateWithLifecycle()

    var showBackups by rememberSaveable { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    // Recuperacion en curso: texto de la copia, su resumen y lo que hay ahora en el telefono
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var pendingRestoreSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var currentSummary by remember { mutableStateOf<RestoreSummary?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    var reminderHealth by remember { mutableStateOf(readReminderHealth(context)) }

    // La lista principal conserva su posicion al ir y volver de Copias de seguridad
    val mainListState = rememberLazyListState()

    // Al volver a la app (por ejemplo, desde los ajustes de Android) se revisan otra vez los avisos
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val before = reminderHealth
                val now = readReminderHealth(context)
                reminderHealth = now
                // Las alarmas que ya estaban puestas siguen siendo inexactas: se vuelven a programar
                if (before != ReminderHealth.OK && now == ReminderHealth.OK) {
                    viewModel.rescheduleAllReminders()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** Revisa el texto de una copia y, si es valida, abre la confirmacion. */
    fun reviewBackup(json: String) {
        scope.launch {
            val preview = withContext(Dispatchers.Default) { viewModel.parseBackupPreview(json) }
            preview
                .onSuccess { summary ->
                    currentSummary = null
                    pendingRestoreJson = json
                    pendingRestoreSummary = summary
                }
                .onFailure { viewModel.showMessage(NOT_A_BACKUP_MESSAGE) }
        }
    }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.saveBackupTo(uri)
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportCsvTo(uri)
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.readTextFrom(uri)
                if (json.isNullOrBlank()) {
                    viewModel.showMessage("No se pudo leer el archivo")
                } else {
                    reviewBackup(json)
                }
            }
        }
    }

    // Lo que hay ahora en el telefono, para la tabla "Ahora -> Copia"
    LaunchedEffect(pendingRestoreSummary) {
        if (pendingRestoreSummary != null && currentSummary == null) {
            try {
                currentSummary = viewModel.getCurrentDataSummary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.showMessage("No se pudo leer lo que hay ahora en el teléfono")
                pendingRestoreJson = null
                pendingRestoreSummary = null
            }
        }
    }

    val onSaveToDevice: () -> Unit = {
        if (!backupBusy) saveBackupLauncher.launch(BackupFileSharer.backupFileName())
    }

    val onShare: () -> Unit = {
        if (!isSharing) {
            isSharing = true
            scope.launch {
                val chooser = viewModel.prepareBackupShareIntent()
                isSharing = false
                if (chooser == null) {
                    viewModel.showMessage("No se pudo preparar la copia para enviarla")
                } else {
                    try {
                        context.startActivity(chooser)
                        // Android no avisa si el envio se cancela: se cuenta al abrir el menu de compartir
                        viewModel.markBackupDone()
                    } catch (e: Exception) {
                        viewModel.showMessage("No hay apps para enviar la copia")
                    }
                }
            }
        }
    }

    val onCopyAsText: () -> Unit = {
        scope.launch {
            try {
                val json = viewModel.getExportJson()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Copia de HabitFlow", json))
                // Android 13 o mas ya muestra su propio aviso al copiar
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    viewModel.showMessage("Texto de la copia en el portapapeles")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                viewModel.showMessage("La copia es muy grande para copiarla como texto. Usa Guardar copia.")
            }
        }
    }

    val onUndoLastRestore: () -> Unit = {
        scope.launch {
            val json = viewModel.readSafetySnapshot()
            if (json == null) {
                viewModel.showMessage("No hay nada que deshacer")
            } else {
                reviewBackup(json)
            }
        }
    }

    BackHandler(enabled = showBackups) { showBackups = false }

    AnimatedContent(
        targetState = showBackups,
        transitionSpec = {
            // Misma transicion que las pestanas de Progreso: entra desde la derecha al abrir Copias
            val forward = targetState
            (slideInHorizontally(
                animationSpec = tween(durationMillis = 280),
                initialOffsetX = { fullWidth -> if (forward) fullWidth / 4 else -fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(280)))
                .togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 250),
                        targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 }
                    ) + fadeOut(animationSpec = tween(200))
                )
        },
        label = "settings_page_transition",
        modifier = modifier.fillMaxSize()
    ) { backups ->
        if (backups) {
            BackupSettingsScreen(
                status = backupStatus,
                safetySnapshotAt = safetySnapshotAt,
                isBusy = backupBusy || isSharing,
                onBack = { showBackups = false },
                onSaveToDevice = onSaveToDevice,
                onShare = onShare,
                onRestoreFromFile = { openBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onUndoLastRestore = onUndoLastRestore,
                onCopyAsText = onCopyAsText,
                onPasteText = { showPasteDialog = true }
            )
        } else {
            SettingsMainPage(
                uiState = uiState,
                listState = mainListState,
                backupStatus = backupStatus,
                backupBusy = backupBusy,
                aiConfigured = aiConfigured,
                reminderHealth = reminderHealth,
                onCreateBackup = onSaveToDevice,
                onOpenBackups = { showBackups = true },
                onSelectTheme = { mode -> viewModel.setThemeMode(mode) },
                onToggleDynamicColor = { enabled -> viewModel.setDynamicColor(enabled) },
                onOpenLayoutPicker = { showLayoutDialog = true },
                onOpenManageCategories = onOpenManageCategories,
                onOpenArchivedHabits = onOpenArchivedHabits,
                onOpenReminderSettings = { openReminderSettings(context, reminderHealth) },
                onRepairReminders = { viewModel.rescheduleAllReminders() },
                onToggleHardcore = { enabled -> viewModel.toggleHardcoreMode(enabled) },
                onOpenAiSettings = onOpenAiSettings,
                onExportCsv = {
                    if (!backupBusy) exportCsvLauncher.launch(BackupFileSharer.csvFileName())
                }
            )
        }
    }

    if (showLayoutDialog) {
        LayoutModeDialog(
            current = uiState.layoutMode,
            onSelect = { mode ->
                viewModel.setLayoutMode(mode)
                showLayoutDialog = false
            },
            onDismiss = { showLayoutDialog = false }
        )
    }

    if (showPasteDialog) {
        BackupPasteDialog(
            onDismiss = { showPasteDialog = false },
            onContinue = { text ->
                showPasteDialog = false
                reviewBackup(text)
            }
        )
    }

    val restoreJson = pendingRestoreJson
    val restoreSummary = pendingRestoreSummary
    if (restoreJson != null && restoreSummary != null) {
        RestoreConfirmSheet(
            incoming = restoreSummary,
            current = currentSummary,
            isRestoring = isRestoring,
            onConfirm = {
                isRestoring = true
                viewModel.restoreDatabaseFromJson(restoreJson) {
                    isRestoring = false
                    pendingRestoreJson = null
                    pendingRestoreSummary = null
                    currentSummary = null
                }
            },
            onDismiss = {
                // Si ya empezo, la recuperacion sigue en el ViewModel; el aviso dira como termino
                pendingRestoreJson = null
                pendingRestoreSummary = null
                currentSummary = null
            }
        )
    }
}

@Composable
private fun SettingsMainPage(
    uiState: HabitUiState,
    listState: LazyListState,
    backupStatus: BackupStatus,
    backupBusy: Boolean,
    aiConfigured: Boolean,
    reminderHealth: ReminderHealth,
    onCreateBackup: () -> Unit,
    onOpenBackups: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onOpenLayoutPicker: () -> Unit,
    onOpenManageCategories: () -> Unit,
    onOpenArchivedHabits: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRepairReminders: () -> Unit,
    onToggleHardcore: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onExportCsv: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item(key = "title") {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item(key = "backup_status") {
            BackupStatusCard(
                status = backupStatus,
                isBusy = backupBusy,
                onCreateBackup = onCreateBackup,
                onOpenBackups = onOpenBackups
            )
        }

        item(key = "appearance") {
            SettingsSection(title = "Apariencia") {
                SettingsRow(
                    icon = Icons.Default.Palette,
                    tint = SettingsTint.Appearance,
                    title = "Tema",
                    subtitle = "Auto sigue el modo de tu teléfono"
                )
                ThemeModeSelector(current = uiState.themeMode, onSelect = onSelectTheme)
                // Colores del fondo de pantalla (Material You) solo existen desde Android 12
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Default.Wallpaper,
                        tint = SettingsTint.Appearance,
                        title = "Colores de tu fondo",
                        subtitle = "La app toma los tonos de tu fondo de pantalla",
                        checked = uiState.dynamicColor,
                        onCheckedChange = onToggleDynamicColor,
                        modifier = Modifier.testTag("settings_dynamic_color_switch")
                    )
                }
                SettingsDivider()
                SettingsRow(
                    icon = layoutIcon(uiState.layoutMode),
                    tint = SettingsTint.Appearance,
                    title = "Vista de Hoy",
                    subtitle = "Cómo se acomodan tus hábitos",
                    onClick = onOpenLayoutPicker,
                    modifier = Modifier.testTag("settings_layout_mode")
                ) {
                    SettingsValue(value = layoutShortLabel(uiState.layoutMode))
                }
            }
        }

        item(key = "habits") {
            SettingsSection(title = "Hábitos") {
                SettingsRow(
                    icon = Icons.Default.Category,
                    tint = SettingsTint.Habits,
                    title = "Categorías",
                    subtitle = "Agrupa tus hábitos por tema",
                    onClick = onOpenManageCategories,
                    modifier = Modifier.testTag("settings_manage_categories")
                ) {
                    SettingsValue(value = uiState.categories.size.toString())
                }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Inventory2,
                    tint = SettingsTint.Habits,
                    title = "Hábitos archivados",
                    subtitle = "En pausa, sin perder su historial",
                    onClick = onOpenArchivedHabits,
                    modifier = Modifier.testTag("settings_archived_habits")
                ) {
                    SettingsValue(value = uiState.archivedHabits.size.toString())
                }
            }
        }

        item(key = "reminders") {
            val (pillText, pillTone, subtitle) = when (reminderHealth) {
                ReminderHealth.OK -> Triple("Activos", SettingsTone.GOOD, "Te avisamos a la hora de cada hábito")
                ReminderHealth.NOTIFICATIONS_OFF -> Triple("Apagados", SettingsTone.DANGER, "Toca para activarlos en Android")
                ReminderHealth.EXACT_ALARMS_OFF -> Triple("Con retraso", SettingsTone.WARNING, "Toca para que lleguen a la hora exacta")
            }
            SettingsSection(title = "Recordatorios") {
                SettingsRow(
                    icon = if (reminderHealth == ReminderHealth.NOTIFICATIONS_OFF) {
                        Icons.Default.NotificationsOff
                    } else {
                        Icons.Default.Notifications
                    },
                    tint = SettingsTint.Reminders,
                    title = "Avisos",
                    subtitle = subtitle,
                    onClick = onOpenReminderSettings,
                    modifier = Modifier.testTag("settings_notifications")
                ) {
                    SettingsStatusPill(text = pillText, tone = pillTone)
                }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Build,
                    tint = SettingsTint.Reminders,
                    title = "Reparar recordatorios",
                    subtitle = "Úsalo si un aviso no llegó",
                    onClick = onRepairReminders,
                    modifier = Modifier.testTag("settings_resync_reminders")
                ) {
                    SettingsValue(value = null)
                }
            }
        }

        item(key = "motivation") {
            SettingsSection(title = "Motivación") {
                SettingsSwitchRow(
                    icon = Icons.Default.Bolt,
                    tint = SettingsTint.Motivation,
                    title = "Modo difícil",
                    subtitle = "Ganas 25% más XP. Tus rachas y metas no cambian.",
                    checked = uiState.userStats.isHardcoreMode,
                    onCheckedChange = onToggleHardcore,
                    modifier = Modifier.testTag("settings_hardcore_mode")
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.AutoAwesome,
                    tint = SettingsTint.Assistant,
                    title = "Asistente con IA",
                    subtitle = "Consejos sobre tu progreso",
                    onClick = onOpenAiSettings,
                    modifier = Modifier.testTag("settings_ai_config")
                ) {
                    SettingsValue(value = if (aiConfigured) "Activo" else "Apagado")
                }
            }
        }

        item(key = "data") {
            SettingsSection(title = "Tus datos") {
                SettingsRow(
                    icon = Icons.Default.Backup,
                    tint = SettingsTint.Data,
                    title = "Copias de seguridad",
                    subtitle = "Guardar y recuperar todo",
                    onClick = onOpenBackups,
                    modifier = Modifier.testTag("settings_backups")
                ) {
                    SettingsValue(value = backupStatus.shortLabel)
                }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.TableChart,
                    tint = SettingsTint.Data,
                    title = "Exportar a Excel",
                    subtitle = "Tus registros en una hoja de cálculo",
                    onClick = onExportCsv,
                    modifier = Modifier.testTag("settings_export_csv")
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item(key = "footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Sin cuentas. Tus hábitos se guardan en este teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "HabitFlow · versión ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Claro / Oscuro / Auto. El elegido muestra una palomita en lugar de su icono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.LIGHT, "Claro", Icons.Default.LightMode),
        Triple(ThemeMode.DARK, "Oscuro", Icons.Default.DarkMode),
        Triple(ThemeMode.SYSTEM, "Auto", Icons.Default.BrightnessAuto)
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        options.forEachIndexed { index, (mode, label, icon) ->
            val selected = mode == current
            SegmentedButton(
                selected = selected,
                onClick = { if (!selected) onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
            ) {
                Text(text = label, maxLines = 1)
            }
        }
    }
}

private fun layoutShortLabel(mode: ViewLayoutMode): String = when (mode) {
    ViewLayoutMode.LIST -> "Lista"
    ViewLayoutMode.HEATMAP -> "Mapa de calor"
    ViewLayoutMode.KANBAN -> "Tablero"
    ViewLayoutMode.TIMELINE -> "Línea de tiempo"
}

/** Los mismos iconos que el menu de vistas de la pestana Hoy. */
private fun layoutIcon(mode: ViewLayoutMode): ImageVector = when (mode) {
    ViewLayoutMode.LIST -> Icons.Default.ViewList
    ViewLayoutMode.HEATMAP -> Icons.Default.GridOn
    ViewLayoutMode.KANBAN -> Icons.Default.ViewKanban
    ViewLayoutMode.TIMELINE -> Icons.Default.Timeline
}

/** Elegir como se ve la pestana Hoy. Al tocar una opcion se aplica y se cierra. */
@Composable
private fun LayoutModeDialog(
    current: ViewLayoutMode,
    onSelect: (ViewLayoutMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(ViewLayoutMode.LIST, "Lista", "Tus hábitos del día, uno debajo de otro"),
        Triple(ViewLayoutMode.HEATMAP, "Mapa de calor", "El historial de cada hábito en cuadritos"),
        Triple(ViewLayoutMode.KANBAN, "Tablero Kanban", "Columnas: por hacer, en curso y hechos"),
        Triple(ViewLayoutMode.TIMELINE, "Línea de tiempo", "Por hora del recordatorio: mañana, tarde y noche")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Vista de Hoy") },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { (mode, title, description) ->
                    val selected = mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(mode) })
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("layout_option_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Icon(
                            imageVector = layoutIcon(mode),
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cerrar")
            }
        }
    )
}

/** Lee si Android deja que los avisos lleguen, y si llegan a la hora exacta. */
private fun readReminderHealth(context: Context): ReminderHealth {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return ReminderHealth.NOTIFICATIONS_OFF
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
            return ReminderHealth.EXACT_ALARMS_OFF
        }
    }
    return ReminderHealth.OK
}

/** Abre la pantalla de Android que arregla el problema: alarmas exactas o notificaciones de la app. */
private fun openReminderSettings(context: Context, health: ReminderHealth) {
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    val intent = when {
        health == ReminderHealth.EXACT_ALARMS_OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> appDetails
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        runCatching { context.startActivity(appDetails) }
    }
}
