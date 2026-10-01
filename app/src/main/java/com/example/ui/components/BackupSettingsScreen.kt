package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.RestoreSummary
import com.example.util.BackupStatus
import com.example.util.BackupStatusCalculator
import com.example.util.RestoreComparisonBuilder
import com.example.util.RestoreComparisonRow

/**
 * Pantalla "Copias de seguridad" dentro de Ajustes (Propuesta A).
 * No hace trabajo propio: cada accion es un callback que resuelve SettingsScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSettingsScreen(
    status: BackupStatus,
    safetySnapshotAt: Long?,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onRestoreFromFile: () -> Unit,
    onUndoLastRestore: () -> Unit,
    onCopyAsText: () -> Unit,
    onPasteText: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val toneColor = settingsToneColor(status.level.tone())
    val lastBackupLabel = status.lastBackupLabel
    val snapshotLabel = remember(safetySnapshotAt) {
        safetySnapshotAt?.let { BackupStatusCalculator.formatDateTime(it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("backup_settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "top_bar") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("backup_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a Ajustes"
                    )
                }
                Text(
                    text = "Copias de seguridad",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item(key = "hero") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupStatusRing(level = status.level, size = 104.dp, iconSize = 48.dp, borderWidth = 4.dp)
                Spacer(modifier = Modifier.height(4.dp))
                if (lastBackupLabel != null) {
                    Text(
                        text = "Última copia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = lastBackupLabel,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
                    Text(
                        text = "Todavía no hay copias",
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = toneColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = status.badge,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = toneColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item(key = "contents") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Qué se guarda",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BackupContentChip(icon = Icons.Default.TaskAlt, label = "Hábitos")
                    BackupContentChip(icon = Icons.Default.EventAvailable, label = "Registros diarios")
                    BackupContentChip(icon = Icons.Default.Checklist, label = "Pasos")
                    BackupContentChip(icon = Icons.Default.Category, label = "Categorías")
                    BackupContentChip(icon = Icons.Default.MilitaryTech, label = "Nivel y logros")
                }
            }
        }

        item(key = "save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSaveToDevice,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("backup_save_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Guardar copia en el teléfono", fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Tú eliges dónde guardarla, incluso en Google Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(key = "actions") {
            SettingsCard {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    tint = SettingsTint.Data,
                    title = "Enviar a otra app",
                    subtitle = "Correo, WhatsApp o Drive, como archivo",
                    onClick = onShare,
                    modifier = Modifier.testTag("share_backup_button")
                ) {
                    SettingsValue(value = null)
                }
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.SettingsBackupRestore,
                    tint = SettingsTint.Caution,
                    title = "Recuperar una copia",
                    subtitle = "Elige un archivo que guardaste antes",
                    onClick = onRestoreFromFile,
                    modifier = Modifier.testTag("restore_button")
                ) {
                    SettingsValue(value = null)
                }
                if (snapshotLabel != null) {
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        tint = SettingsTint.Caution,
                        title = "Deshacer la última recuperación",
                        subtitle = "Vuelve a como estaba el $snapshotLabel",
                        onClick = onUndoLastRestore,
                        modifier = Modifier.testTag("undo_restore_button")
                    ) {
                        SettingsValue(value = null)
                    }
                }
            }
        }

        item(key = "advanced") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = { showAdvanced = !showAdvanced })
                            .semantics { stateDescription = if (showAdvanced) "Abierto" else "Cerrado" }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Opciones avanzadas",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            AdvancedBackupRow(
                                icon = Icons.Default.ContentCopy,
                                title = "Copiar como texto",
                                subtitle = "Para pegarla en una nota",
                                onClick = onCopyAsText,
                                tag = "copy_json_button"
                            )
                            AdvancedBackupRow(
                                icon = Icons.Default.ContentPaste,
                                title = "Pegar texto de una copia",
                                subtitle = "Si la guardaste como texto",
                                onClick = onPasteText,
                                tag = "manual_paste_restore_button"
                            )
                        }
                    }
                }
            }
        }

        item(key = "tip") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Guarda la copia fuera del teléfono, por ejemplo en Drive. Si lo pierdes o lo cambias, tus hábitos siguen a salvo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Etiqueta de lo que incluye una copia. No responde al toque. */
@Composable
private fun BackupContentChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AdvancedBackupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Confirmacion antes de recuperar una copia: compara lo que hay ahora con la copia.
 * El boton es rojo porque reemplaza datos. Mientras [current] carga, la tabla muestra un indicador.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreConfirmSheet(
    incoming: RestoreSummary,
    current: RestoreSummary?,
    isRestoring: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val comparison = remember(current, incoming) {
        current?.let { RestoreComparisonBuilder.build(it, incoming) }
    }
    val warningColor = settingsToneColor(SettingsTone.WARNING)
    val copyDate = remember(incoming.exportedAt) {
        if (incoming.exportedAt > 0L) BackupStatusCalculator.formatDateTime(incoming.exportedAt) else null
    }
    val warning = comparison?.warning

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(warningColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "¿Reemplazar tus datos?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (copyDate != null) {
                        "Todo lo que hay en este teléfono se cambia por la copia del $copyDate."
                    } else {
                        "Todo lo que hay en este teléfono se cambia por esta copia."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ComparisonHeader()
                    if (comparison == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    } else {
                        comparison.rows.forEach { row ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                            ComparisonLine(row = row, lossColor = warningColor)
                        }
                    }
                }
            }

            if (warning != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = warningColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Antes de cambiar nada guardamos lo que tienes ahora. Podrás deshacerlo en Copias de seguridad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    enabled = !isRestoring && comparison != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_restore_dialog_button")
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Reemplazar mis datos", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(text = "Cancelar")
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f))
        ComparisonHeaderLabel(text = "AHORA")
        Spacer(modifier = Modifier.width(24.dp))
        ComparisonHeaderLabel(text = "COPIA")
    }
}

@Composable
private fun ComparisonHeaderLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.width(64.dp)
    )
}

/** Una fila "Ahora -> Copia". Si la copia trae menos, el numero de la copia va en ambar. */
@Composable
private fun ComparisonLine(row: RestoreComparisonRow, lossColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clearAndSetSemantics {
                contentDescription = "${row.label}: ahora ${row.current}, en la copia ${row.incoming}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.current.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp)
        )
        Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = row.incoming.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.isLoss) FontWeight.Bold else FontWeight.Normal,
            color = if (row.isLoss) lossColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp)
        )
    }
}

/** Para recuperar una copia guardada como texto (opcion avanzada). */
@Composable
fun BackupPasteDialog(
    onDismiss: () -> Unit,
    onContinue: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Pegar texto de una copia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Pega aquí el texto completo de una copia de HabitFlow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(text = "{ \"appName\": \"HabitFlow\", ... }") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("manual_json_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onContinue(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("validate_manual_json_button")
            ) {
                Text(text = "Revisar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}
