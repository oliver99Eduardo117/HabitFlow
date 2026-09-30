package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Habit
import com.example.util.IconHelper
import java.util.Locale

/*
 * Piezas compartidas de Progreso (Resumen, Constancia y Logros).
 * Verde = cumplido. Primary (indigo) = hoy, XP y nivel. Ambar = a medias e insignias.
 * Cada habito usa su propio color solo dentro de sus filas e historial.
 */

/** true con el tema oscuro (se decide por la superficie, igual que el Calendario). */
@Composable
internal fun isProgressDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Ambar de las insignias: mas oscuro en el tema claro para que se vea sobre blanco. */
@Composable
internal fun progressBadgeAmber(): Color = if (isProgressDark()) Color(0xFFFBBF24) else Color(0xFFD97706)

/**
 * Fondo de las tarjetas. Con colores dinamicos (Material You), surface y background son iguales
 * y las tarjetas se perderian en el fondo; en ese caso se usa surfaceContainerHigh.
 */
@Composable
internal fun progressCardColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.surface == colors.background) colors.surfaceContainerHigh else colors.surface
}

/** Fondo de las filas dentro de una tarjeta, para que se distingan de ella. */
@Composable
internal fun progressInsetColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.surface == colors.background) colors.surfaceContainerLow else colors.background
}

/** Texto o icono legible encima de [color]: oscuro sobre colores claros, blanco sobre oscuros. */
internal fun contentOn(color: Color): Color = if (color.luminance() > 0.2f) Color(0xFF0F172A) else Color.White

/** Verde para textos: mas claro en oscuro, mas oscuro en claro para que se lea sobre blanco. */
@Composable
internal fun progressGreenText(): Color = if (isProgressDark()) Color(0xFF34D399) else HabitDoneFill

/** Ambar para textos. */
@Composable
internal fun progressAmberText(): Color = if (isProgressDark()) Color(0xFFFBBF24) else Color(0xFFB45309)

/** Color del habito, con primary como respaldo si el hex no se puede leer. */
@Composable
internal fun habitAccent(habit: Habit): Color = habitColorOf(habit.colorHex, MaterialTheme.colorScheme.primary)

/** 10725 -> "10,725". */
internal fun formatThousands(value: Int): String = String.format(Locale.US, "%,d", value)

internal fun daysText(days: Int): String = if (days == 1) "1 día" else "$days días"

/** Tarjeta base: superficie lisa, esquinas de 24dp, sin borde ni sombra. */
@Composable
internal fun ProgressCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = progressCardColor(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
internal fun ProgressCardTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Anillo: pista completa y arco que empieza arriba. [fraction] de 0 a 1. */
internal fun DrawScope.drawProgressRing(fraction: Float, color: Color, track: Color, strokeWidth: Dp) {
    val stroke = strokeWidth.toPx()
    val topLeft = Offset(stroke / 2f, stroke / 2f)
    val arcSize = Size(size.width - stroke, size.height - stroke)
    drawArc(
        color = track,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke)
    )
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    if (sweep > 0f) {
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** Mosaico con icono, numero grande y etiqueta de dos lineas. */
@Composable
internal fun ProgressStatTile(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = progressCardColor(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Row {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline()
                )
                if (unit != null) {
                    Text(
                        text = " $unit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Cuadro redondeado con el icono del habito sobre su color al 16%. */
@Composable
internal fun HabitIconTile(habit: Habit, size: Dp = 40.dp) {
    val color = habitAccent(habit)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = IconHelper.getIconByName(habit.iconName),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** Barra horizontal de avance. */
@Composable
internal fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(height / 2))
                    .background(color)
            )
        }
    }
}

/** Etiqueta chica con icono opcional, sobre el mismo color al 14%. Si no cabe, pasa a una segunda linea. */
@Composable
internal fun ProgressChip(text: String, color: Color, icon: ImageVector? = null) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Muestra de color para las leyendas. */
@Composable
internal fun ProgressSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(3.dp),
    size: Dp = 12.dp
) {
    val base = modifier
        .size(size)
        .clip(shape)
        .background(color)
    Box(modifier = if (borderColor != null) base.border(borderWidth, borderColor, shape) else base)
}

@Composable
internal fun ProgressLegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        swatch()
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Estado vacio de una seccion de Progreso. */
@Composable
internal fun ProgressEmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Insights,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
