package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ProgressTab

private data class ProgressSection(
    val tab: ProgressTab,
    val label: String,
    val icon: ImageVector,
    val question: String
)

private val ProgressSections = listOf(
    ProgressSection(
        ProgressTab.ANALYTICS,
        "Resumen",
        Icons.Default.DonutLarge,
        "Cuánto cumpliste y qué hábito necesita atención."
    ),
    ProgressSection(
        ProgressTab.HEATMAP,
        "Constancia",
        Icons.Default.GridView,
        "Cada cuadro es un día. Toca uno para ver qué pasó."
    ),
    ProgressSection(
        ProgressTab.ACHIEVEMENTS,
        "Logros",
        Icons.Default.EmojiEvents,
        "Tu nivel, tus medallas y lo que sigue."
    )
)

/**
 * Pestana Progreso (Propuesta D): tres secciones, cada una con una sola pregunta.
 * No calcula nada; cada seccion llega armada desde HabitFlowApp.
 */
@Composable
fun ProgressScreen(
    activeTab: ProgressTab,
    onTabSelected: (ProgressTab) -> Unit,
    resumen: @Composable () -> Unit,
    constancia: @Composable () -> Unit,
    logros: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = ProgressSections.first { it.tab == activeTab }
    // Conserva el estado de cada seccion (periodo, dia elegido, scroll) al cambiar entre ellas
    val sectionStates = rememberSaveableStateHolder()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Progreso",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
        )
        SectionSwitcher(
            activeTab = activeTab,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .padding(start = 16.dp, top = 10.dp, end = 16.dp)
                .testTag("progress_tab_row")
        )
        Text(
            text = current.question,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 2.dp)
        )

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
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
            label = "progress_tab_transition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { tab ->
            sectionStates.SaveableStateProvider(tab.name) {
                when (tab) {
                    ProgressTab.ANALYTICS -> resumen()
                    ProgressTab.HEATMAP -> constancia()
                    ProgressTab.ACHIEVEMENTS -> logros()
                }
            }
        }
    }
}

@Composable
private fun SectionSwitcher(
    activeTab: ProgressTab,
    onTabSelected: (ProgressTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(progressCardColor())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ProgressSections.forEach { section ->
            val isSelected = section.tab == activeTab
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable(role = Role.Tab, onClick = { onTabSelected(section.tab) })
                    .semantics { selected = isSelected }
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = section.label,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
