package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.Habit
import com.example.model.HabitLog
import com.example.model.ThemeMode
import com.example.model.UserStats
import com.example.viewmodel.ProgressTab

@Composable
fun ProgressScreen(
    activeTab: ProgressTab,
    onTabSelected: (ProgressTab) -> Unit,
    // Analytics params
    habits: List<Habit>,
    allLogs: List<HabitLog>,
    insights: List<String>,
    isLoadingInsights: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onExportJson: suspend () -> String,
    onExportCsv: suspend () -> String,
    // Heatmap params
    onSelectDate: (String) -> Unit,
    // Gamification params
    userStats: UserStats,
    onToggleHardcoreMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Bar for Progress sub-sections
        TabRow(
            selectedTabIndex = activeTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (activeTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                        color = Color(0xFF6366F1),
                        height = 3.dp
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("progress_tab_row")
        ) {
            Tab(
                selected = activeTab == ProgressTab.ANALYTICS,
                onClick = { onTabSelected(ProgressTab.ANALYTICS) },
                text = {
                    Text(
                        text = "Análisis",
                        fontWeight = if (activeTab == ProgressTab.ANALYTICS) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = "Análisis",
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = Color(0xFF6366F1),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Tab(
                selected = activeTab == ProgressTab.HEATMAP,
                onClick = { onTabSelected(ProgressTab.HEATMAP) },
                text = {
                    Text(
                        text = "Constancia",
                        fontWeight = if (activeTab == ProgressTab.HEATMAP) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Constancia",
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = Color(0xFF6366F1),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Tab(
                selected = activeTab == ProgressTab.ACHIEVEMENTS,
                onClick = { onTabSelected(ProgressTab.ACHIEVEMENTS) },
                text = {
                    Text(
                        text = "Logros",
                        fontWeight = if (activeTab == ProgressTab.ACHIEVEMENTS) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Logros",
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = Color(0xFF6366F1),
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Animated Content with fluid horizontal slide & fade transition
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                val targetIndex = targetState.ordinal
                val initialIndex = initialState.ordinal
                if (targetIndex > initialIndex) {
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 280),
                        initialOffsetX = { fullWidth -> fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(280)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 250),
                                targetOffsetX = { fullWidth -> -fullWidth / 4 }
                            ) + fadeOut(animationSpec = tween(200))
                        )
                } else {
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 280),
                        initialOffsetX = { fullWidth -> -fullWidth / 4 }
                    ) + fadeIn(animationSpec = tween(280)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 250),
                                targetOffsetX = { fullWidth -> fullWidth / 4 }
                            ) + fadeOut(animationSpec = tween(200))
                        )
                }
            },
            label = "progress_tab_transition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { tab ->
            when (tab) {
                ProgressTab.ANALYTICS -> {
                    AnalyticsScreen(
                        habits = habits,
                        allLogs = allLogs,
                        insights = insights,
                        isLoadingInsights = isLoadingInsights,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onSelectThemeMode = onSelectThemeMode,
                        onToggleDynamicColor = onToggleDynamicColor,
                        onExportJson = onExportJson,
                        onExportCsv = onExportCsv
                    )
                }

                ProgressTab.HEATMAP -> {
                    HeatmapView(
                        habits = habits,
                        allLogs = allLogs,
                        onSelectDate = onSelectDate
                    )
                }

                ProgressTab.ACHIEVEMENTS -> {
                    GamificationDashboard(
                        userStats = userStats,
                        onToggleHardcoreMode = onToggleHardcoreMode
                    )
                }
            }
        }
    }
}
