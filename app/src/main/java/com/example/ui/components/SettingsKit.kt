package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPurple
import com.example.ui.theme.BrandSecondary
import com.example.ui.theme.BrandTertiary
import com.example.util.BackupLevel
import com.example.util.BackupStatus

/*
 * Piezas de la pantalla de Ajustes (Propuesta A, "lista con estado").
 * Las usan SettingsScreen.kt y BackupSettingsScreen.kt.
 */

/** Un color por grupo de Ajustes. Solo tine el cuadro del icono; el resto de la pantalla es neutro. */
internal object SettingsTint {
    val Appearance = BrandPurple
    val Habits = BrandPrimary
    val Reminders = BrandAmber
    val Motivation = BrandTertiary
    val Assistant = BrandSecondary
    val Data = BrandAccent

    /** Acciones que reemplazan datos, como recuperar una copia. */
    val Caution = BrandAmber
}

/** Tono de un estado: verde, ambar o rojo. */
internal enum class SettingsTone { GOOD, WARNING, DANGER }

/** true si el tema actual es oscuro. Funciona tambien con colores dinamicos porque mira la superficie. */
@Composable
internal fun isSettingsDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Color del estado, legible en tema claro y oscuro (los mismos tonos que usa el Calendario). */
@Composable
internal fun settingsToneColor(tone: SettingsTone): Color {
    val dark = isSettingsDark()
    return when (tone) {
        SettingsTone.GOOD -> if (dark) HabitDoneAccent else HabitDoneFill
        SettingsTone.WARNING -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        SettingsTone.DANGER -> MaterialTheme.colorScheme.error
    }
}

internal fun BackupLevel.tone(): SettingsTone = when (this) {
    BackupLevel.GOOD -> SettingsTone.GOOD
    BackupLevel.WARNING -> SettingsTone.WARNING
    BackupLevel.DANGER -> SettingsTone.DANGER
}

internal fun BackupLevel.icon(): ImageVector = when (this) {
    BackupLevel.GOOD -> Icons.Default.CloudDone
    BackupLevel.WARNING -> Icons.Default.History
    BackupLevel.DANGER -> Icons.Default.CloudOff
}

/** Cuadro redondeado con el icono del ajuste, en el color de su grupo. */
@Composable
internal fun SettingsIconTile(icon: ImageVector, tint: Color) {
    // Sobre fondo oscuro el icono se aclara y sobre fondo claro se oscurece un poco, para que se lea
    val iconColor = if (isSettingsDark()) lerp(tint, Color.White, 0.35f) else lerp(tint, Color.Black, 0.12f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Titulo del grupo y su tarjeta. */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        SettingsCard(content = content)
    }
}

/** Tarjeta de un grupo. Sobre el fondo, la elevacion se marca con el color de superficie, sin sombra. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** Linea entre filas, alineada con el texto (no con el icono). */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
    )
}

/**
 * Fila de ajuste: icono, titulo, explicacion corta y, a la derecha, el valor actual o un control.
 * Si [onClick] es null la fila no responde al toque. Toda la fila mide al menos 72 dp de alto.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIconTile(icon = icon, tint = tint)
        SettingsTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Fila con interruptor. Toda la fila responde al toque, no solo el interruptor. */
@Composable
internal fun SettingsSwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsIconTile(icon = icon, tint = tint)
        SettingsTexts(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        // onCheckedChange = null: el toque lo maneja la fila completa
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsTexts(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Valor actual a la derecha de una fila, con flecha. Con [value] null solo muestra la flecha. */
@Composable
internal fun SettingsValue(value: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Pastilla de estado con punto de color: "Activos", "Con retraso", "Apagados". */
@Composable
internal fun SettingsStatusPill(text: String, tone: SettingsTone) {
    val color = settingsToneColor(tone)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

/** Circulo con borde del color del estado de la copia y su icono. */
@Composable
internal fun BackupStatusRing(level: BackupLevel, size: Dp, iconSize: Dp, borderWidth: Dp) {
    val color = settingsToneColor(level.tone())
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(width = borderWidth, color = color, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = level.icon(),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Tarjeta superior de Ajustes: como esta la copia de seguridad y el boton para hacer una. */
@Composable
internal fun BackupStatusCard(
    status: BackupStatus,
    isBusy: Boolean,
    onCreateBackup: () -> Unit,
    onOpenBackups: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toneColor = settingsToneColor(status.level.tone())
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_restore_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BackupStatusRing(level = status.level, size = 56.dp, iconSize = 28.dp, borderWidth = 3.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "COPIA DE SEGURIDAD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = toneColor
                    )
                    Text(
                        text = status.headline,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = status.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCreateBackup,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("backup_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Crear copia", fontWeight = FontWeight.Bold)
                    }
                }
                FilledTonalButton(
                    onClick = onOpenBackups,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("backup_options_button")
                ) {
                    Text(text = "Opciones", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
