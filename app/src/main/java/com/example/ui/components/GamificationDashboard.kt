package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AllBadges
import com.example.model.Badge
import com.example.model.GamificationConfig
import com.example.model.LevelTier
import com.example.model.UserStats
import com.example.util.IconHelper

@Composable
fun GamificationDashboard(
    userStats: UserStats,
    onToggleHardcoreMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val levelProgress = remember(userStats.xp) {
        GamificationConfig.getProgress(userStats.xp)
    }

    var selectedBadgeCategory by remember { mutableStateOf<String?>(null) }
    var showLevelRoadmapDialog by remember { mutableStateOf(false) }
    var showXpGuideDialog by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue = levelProgress.progressFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "xp_progress"
    )

    val badgeCategories = remember {
        listOf("Todos", "Rachas", "Pomodoro", "Experiencia", "Dedicatoria", "Maestría")
    }

    val filteredBadges = remember(selectedBadgeCategory, userStats.unlockedBadgeIds) {
        if (selectedBadgeCategory == null || selectedBadgeCategory == "Todos") {
            AllBadges
        } else {
            AllBadges.filter { it.category.equals(selectedBadgeCategory, ignoreCase = true) }
        }
    }

    val primaryGradColor = try {
        Color(android.graphics.Color.parseColor(levelProgress.primaryColorHex))
    } catch (_: Exception) {
        Color(0xFF6366F1)
    }

    val secondaryGradColor = try {
        Color(android.graphics.Color.parseColor(levelProgress.secondaryColorHex))
    } catch (_: Exception) {
        Color(0xFFEC4899)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Level & XP Progress Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gamification_hero_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Rank Badge & Roadmap button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryGradColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = primaryGradColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = levelProgress.rankName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryGradColor
                            )
                        }
                    }

                    TextButton(
                        onClick = { showLevelRoadmapDialog = true },
                        modifier = Modifier.testTag("view_levels_roadmap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListNumbered,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Niveles", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Avatar / Level Circle
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(primaryGradColor, secondaryGradColor)
                            )
                        )
                        .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Lv.${levelProgress.currentLevel}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = levelProgress.levelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "${levelProgress.currentXp} XP Totales Acumulados",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Perk badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = primaryGradColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = levelProgress.perk,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Progress Info & Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Progreso hacia Nivel ${levelProgress.currentLevel + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Faltan ${levelProgress.xpNeededForNextLevel} XP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryGradColor
                        )
                    }

                    Text(
                        text = "${levelProgress.xpInCurrentLevel} / ${levelProgress.maxXpForLevel - levelProgress.minXpForLevel} XP (${(levelProgress.progressFraction * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = primaryGradColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        // Stats Quadrant
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "Check-ins",
                value = "${userStats.totalCheckIns}",
                icon = Icons.Default.TrackChanges,
                color = Color(0xFF6366F1),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Mejor Racha",
                value = "${userStats.bestStreakAllTime}d",
                icon = Icons.Default.LocalFireDepartment,
                color = Color(0xFFF97316),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Enfoque",
                value = "${userStats.totalFocusMinutes}m",
                icon = Icons.Default.Timer,
                color = Color(0xFF06B6D4),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Insignias",
                value = "${userStats.unlockedBadgeIds.size}/${AllBadges.size}",
                icon = Icons.Default.WorkspacePremium,
                color = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
        }

        // XP Reward Guide Quick Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showXpGuideDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Guía de Recompensas XP",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Aprende cómo acumular más XP y subir de rango",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hardcore Mode Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (userStats.isHardcoreMode) Color(0xFFF43F5E).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (userStats.isHardcoreMode) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Modo Hardcore (+25% XP)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (userStats.isHardcoreMode) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gana un +25% de XP adicional en cada acción. Si fallas un día, la racha se reinicia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = userStats.isHardcoreMode,
                    onCheckedChange = onToggleHardcoreMode,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFF43F5E),
                        checkedTrackColor = Color(0xFFF43F5E).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("hardcore_mode_switch")
                )
            }
        }

        // Badges Section Header & Filter Chips
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insignias y Logros (${userStats.unlockedBadgeIds.size}/${AllBadges.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(badgeCategories) { category ->
                    val isSelected = (selectedBadgeCategory == null && category == "Todos") || selectedBadgeCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedBadgeCategory = if (category == "Todos") null else category
                        },
                        label = { Text(category, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Badges List with Dynamic Progress
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filteredBadges.forEach { badge ->
                val isUnlocked = userStats.unlockedBadgeIds.contains(badge.id)

                // Calculate progress towards unlocking this badge
                val (currentVal, maxVal, unitLabel) = when {
                    badge.requiredCompletions > 0 -> Triple(userStats.totalCheckIns, badge.requiredCompletions, "check-ins")
                    badge.requiredStreak > 0 -> Triple(userStats.bestStreakAllTime, badge.requiredStreak, "días de racha")
                    badge.requiredFocusMinutes > 0 -> Triple(userStats.totalFocusMinutes, badge.requiredFocusMinutes, "minutos")
                    badge.requiredXp > 0 -> Triple(userStats.xp, badge.requiredXp, "XP")
                    else -> Triple(1, 1, "")
                }

                val badgeProgressFraction = if (isUnlocked) 1f else (currentVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = if (isUnlocked) CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899)))) else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(if (isUnlocked) Color(0xFFF59E0B).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isUnlocked,
                                    transitionSpec = {
                                        (fadeIn() + scaleIn(
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
                                            initialScale = 0.5f
                                        )) togetherWith (fadeOut() + scaleOut(targetScale = 0.5f))
                                    },
                                    label = "badge_icon_anim"
                                ) { unlocked ->
                                    if (unlocked) {
                                        Icon(
                                            imageVector = IconHelper.getIconByName(badge.icon),
                                            contentDescription = badge.title,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Insignia bloqueada",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = badge.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (isUnlocked) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "Desbloqueado",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = badge.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Progress Bar for Locked Badges
                        if (!isUnlocked && maxVal > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Progreso:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$currentVal / $maxVal $unitLabel (${(badgeProgressFraction * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { badgeProgressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Level Roadmap Dialog
    if (showLevelRoadmapDialog) {
        LevelRoadmapDialog(
            currentLevel = levelProgress.currentLevel,
            currentXp = userStats.xp,
            onDismiss = { showLevelRoadmapDialog = false }
        )
    }

    // XP Guide Dialog
    if (showXpGuideDialog) {
        XpRewardsGuideDialog(
            onDismiss = { showXpGuideDialog = false }
        )
    }
}

@Composable
fun LevelRoadmapDialog(
    currentLevel: Int,
    currentXp: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escalera de Niveles y Rangos", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Avanza ganando XP completando hábitos, sub-rutinas y sesiones de enfoque Pomodoro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                GamificationConfig.LevelTiers.forEach { tier ->
                    val isCompleted = currentLevel > tier.level
                    val isCurrent = currentLevel == tier.level
                    val isLocked = currentLevel < tier.level

                    val tierColor = try {
                        Color(android.graphics.Color.parseColor(tier.primaryColorHex))
                    } catch (_: Exception) {
                        Color(0xFF6366F1)
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrent -> tierColor.copy(alpha = 0.15f)
                                isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            }
                        ),
                        border = if (isCurrent) BorderStroke(2.dp, tierColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> Color(0xFF10B981).copy(alpha = 0.2f)
                                            isCurrent -> tierColor.copy(alpha = 0.25f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    isCompleted -> Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Nivel completado",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    isCurrent -> Icon(
                                        imageVector = IconHelper.getIconByName(tier.iconName),
                                        contentDescription = "Nivel actual",
                                        tint = tierColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    else -> Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Nivel bloqueado",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Nivel ${tier.level}: ${tier.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = tier.rank,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = tierColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "${tier.minXp} - ${tier.maxXp} XP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = tier.perk,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun XpRewardsGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFF6366F1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cómo Ganar Experiencia (XP)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                XpRuleItem(
                    icon = Icons.Default.TrackChanges,
                    title = "Check-in Diario de Hábito",
                    xp = "+25 XP",
                    description = "Por cada hábito completado en su día programado."
                )

                XpRuleItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = "Superar Meta Cuantitativa",
                    xp = "+15 XP Bonus",
                    description = "Cuando registras un valor superior al objetivo establecido."
                )

                XpRuleItem(
                    icon = Icons.Default.CheckCircle,
                    title = "Sub-tarea / Mini Hito",
                    xp = "+5 XP",
                    description = "Por cada paso o subtarea completada dentro de tu rutina."
                )

                XpRuleItem(
                    icon = Icons.Default.Timer,
                    title = "Sesión de Enfoque Pomodoro",
                    xp = "+2 XP / min",
                    description = "Gana experiencia por cada minuto de concentración profunda activa."
                )

                XpRuleItem(
                    icon = Icons.Default.LocalFireDepartment,
                    title = "Modo Hardcore Activo",
                    xp = "+25% XP Total",
                    description = "Multiplicador del 25% extra en todas las acciones para usuarios de alta disciplina."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("¡A por ello!")
            }
        }
    )
}

@Composable
private fun XpRuleItem(
    icon: ImageVector,
    title: String,
    xp: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = xp,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6366F1),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
