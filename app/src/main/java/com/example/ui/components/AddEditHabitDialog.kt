package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Category
import com.example.model.Habit
import com.example.notification.NotificationHelper
import com.example.ui.theme.HabitColorOptions
import com.example.util.IconHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditHabitDialog(
    initialHabit: Habit? = null,
    categories: List<Category>,
    allHabits: List<Habit>,
    onDismiss: () -> Unit,
    onSaveHabit: (Habit, List<String>) -> Unit,
    onCreateCategory: (Category) -> Unit = {},
    onOpenManageCategories: () -> Unit = {},
    onDeleteHabit: ((Long) -> Unit)? = null,
    onTestReminder: ((Habit) -> Unit)? = null
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialHabit?.title ?: "") }
    var description by remember { mutableStateOf(initialHabit?.description ?: "") }
    var selectedCategory by remember { mutableStateOf(initialHabit?.category ?: (categories.firstOrNull()?.name ?: "General")) }
    var selectedColorHex by remember { mutableStateOf(initialHabit?.colorHex ?: HabitColorOptions.first()) }
    var selectedIconName by remember { mutableStateOf(initialHabit?.iconName ?: "check_circle") }
    var frequencyDays by remember { mutableStateOf(initialHabit?.frequencyDays ?: listOf(1, 2, 3, 4, 5, 6, 7)) }

    // Metric Quantitative tracking
    var hasMetric by remember { mutableStateOf(initialHabit?.unit?.isNotEmpty() == true) }
    var unit by remember { mutableStateOf(initialHabit?.unit ?: "") }
    var targetValueText by remember { mutableStateOf((initialHabit?.targetValue ?: 1f).toInt().toString()) }

    // Timer
    var hasTimer by remember { mutableStateOf(initialHabit?.hasTimer ?: false) }
    var timerMinutesText by remember { mutableStateOf((initialHabit?.timerDurationMinutes ?: 25).toString()) }

    // Reminder Time (HH:mm), Advance & Custom Message
    var hasReminder by remember { mutableStateOf(initialHabit?.reminderTime != null) }
    var reminderHour by remember { mutableStateOf(initialHabit?.reminderTime?.split(":")?.getOrNull(0) ?: "08") }
    var reminderMinute by remember { mutableStateOf(initialHabit?.reminderTime?.split(":")?.getOrNull(1) ?: "30") }
    var reminderMinutesAdvance by remember { mutableIntStateOf(initialHabit?.reminderMinutesAdvance ?: 0) }
    var reminderCustomMessage by remember { mutableStateOf(initialHabit?.reminderCustomMessage ?: "") }

    // Sub-tasks list
    var subTaskInput by remember { mutableStateOf("") }
    val subTasks = remember { mutableStateListOf<String>() }

    // Dependency
    var selectedDependencyId by remember { mutableStateOf<Long?>(initialHabit?.dependencyHabitId) }

    var titleError by remember { mutableStateOf(false) }

    var showIconGalleryDialog by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }

    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onCategoryCreated = { newCat ->
                onCreateCategory(newCat)
                selectedCategory = newCat.name
                showCreateCategoryDialog = false
            }
        )
    }

    val currentIconItem = remember(selectedIconName) {
        IconHelper.getIconItemByName(selectedIconName)
    }

    if (showIconGalleryDialog) {
        val activeColor = try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.primary
        }
        IconGalleryDialog(
            selectedIconId = selectedIconName,
            accentColor = activeColor,
            onDismiss = { showIconGalleryDialog = false },
            onSelectIcon = { newIconId ->
                selectedIconName = newIconId
                showIconGalleryDialog = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialHabit == null) "Nuevo Hábito / Tarea" else "Editar Hábito",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Title Input
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (it.isNotBlank()) titleError = false
                        },
                        label = { Text("Nombre del hábito *") },
                        placeholder = { Text("Ej. Meditar, Lectura, Cardio...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = titleError
                    )

                    AnimatedVisibility(
                        visible = titleError,
                        enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "El nombre del hábito es obligatorio",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción o motivación") },
                        placeholder = { Text("¿Por qué es importante para ti?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Icon & Color Selection Row
                    Text(
                        text = "Icono y Color Visual",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(selectedColorHex))
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    // Card showing selected icon and button to open full gallery
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Current Icon Avatar with press feedback
                                val avatarInteractionSource = remember { MutableInteractionSource() }
                                val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
                                val avatarScale by animateFloatAsState(
                                    targetValue = if (isAvatarPressed) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
                                    label = "avatar_press_scale"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .graphicsLayer {
                                            scaleX = avatarScale
                                            scaleY = avatarScale
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(parsedColor.copy(alpha = 0.2f))
                                        .border(2.dp, parsedColor, RoundedCornerShape(16.dp))
                                        .clickable(
                                            interactionSource = avatarInteractionSource,
                                            indication = null
                                        ) { showIconGalleryDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = IconHelper.getIconByName(selectedIconName),
                                        contentDescription = "Seleccionar icono",
                                        tint = parsedColor,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentIconItem?.name ?: "Icono Personalizado",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Categoría: ${currentIconItem?.category ?: "General"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showIconGalleryDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Galería", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick suggestion icons row
                            Text(
                                text = "Sugerencias rápidas:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("fitness", "water", "meditation", "book", "code", "timer", "sun", "moon", "savings", "brush", "directions_run", "cleaning_services").forEach { iconId ->
                                    val isSelected = (selectedIconName == iconId)
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) parsedColor else MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) parsedColor else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedIconName = iconId },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = IconHelper.getIconByName(iconId),
                                            contentDescription = iconId,
                                            tint = if (isSelected) Color.White else parsedColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Color Palette Selector
                    Text(
                        text = "Color Temático:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HabitColorOptions.forEach { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            val isSelected = (selectedColorHex == colorHex)

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Categoría",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { showCreateCategoryDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nueva Categoría", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { showCreateCategoryDialog = true },
                            label = { Text("+ Crear", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        AssistChip(
                            onClick = onOpenManageCategories,
                            label = { Text("Gestionar", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Gestionar categorías",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )

                        categories.forEach { cat ->
                            val catColor = try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            val isSelected = (selectedCategory == cat.name)

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat.name },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(catColor)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Frequency Days of Week
                    Text(
                        text = "Días de la semana",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dayMap = listOf(
                            1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D"
                        )
                        dayMap.forEach { (dayNum, label) ->
                            val isSelected = frequencyDays.contains(dayNum)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        frequencyDays = if (isSelected) {
                                            if (frequencyDays.size > 1) frequencyDays - dayNum else frequencyDays
                                        } else {
                                            frequencyDays + dayNum
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Metric / Quantitative Tracking Toggle
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tracking Métrico Cuantitativo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Registrar páginas, minutos, litros, etc.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hasMetric,
                                    onCheckedChange = {
                                        hasMetric = it
                                        if (!it) unit = ""
                                    }
                                )
                            }

                            AnimatedVisibility(
                                visible = hasMetric,
                                enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = unit,
                                            onValueChange = { unit = it },
                                            label = { Text("Unidad (ej. min, pág)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = targetValueText,
                                            onValueChange = { targetValueText = it },
                                            label = { Text("Meta diaria") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Timer / Pomodoro Switch
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Integrar Cronómetro / Pomodoro",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Permite iniciar sesiones de enfoque directo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hasTimer,
                                    onCheckedChange = { hasTimer = it }
                                )
                            }

                            AnimatedVisibility(
                                visible = hasTimer,
                                enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = timerMinutesText,
                                        onValueChange = { timerMinutesText = it },
                                        label = { Text("Duración objetivo (minutos)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily Reminder & Local Notification Configuration
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (hasReminder) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                            contentDescription = null,
                                            tint = if (hasReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Recordatorios & Notificaciones",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Alerta diaria local antes de realizarlo",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = hasReminder,
                                    onCheckedChange = { hasReminder = it }
                                )
                            }

                            AnimatedVisibility(
                                visible = hasReminder,
                                enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Horarios Rápidos",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Quick Presets Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val presets = listOf(
                                            Triple("Mañana", "07" to "00", Icons.Default.WbSunny),
                                            Triple("Mediodía", "13" to "00", Icons.Default.LightMode),
                                            Triple("Tarde", "18" to "30", Icons.Default.WbTwilight),
                                            Triple("Noche", "21" to "30", Icons.Default.DarkMode)
                                        )
                                        presets.forEach { (label, time, icon) ->
                                            val isSelected = reminderHour == time.first && reminderMinute == time.second
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    reminderHour = time.first
                                                    reminderMinute = time.second
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                },
                                                label = { Text(label, fontSize = 12.sp) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Time Input (Hour : Minute)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = reminderHour,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }
                                                if (filtered.length <= 2) {
                                                    val num = filtered.toIntOrNull()
                                                    if (num == null || num in 0..23) reminderHour = filtered
                                                }
                                            },
                                            label = { Text("Hora (00-23)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            leadingIcon = {
                                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                                            }
                                        )
                                        Text(text = ":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(
                                            value = reminderMinute,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }
                                                if (filtered.length <= 2) {
                                                    val num = filtered.toIntOrNull()
                                                    if (num == null || num in 0..59) reminderMinute = filtered
                                                }
                                            },
                                            label = { Text("Minuto (00-59)") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Advance / Anticipation selector
                                    Text(
                                        text = "Anticipación del Recordatorio (Avisar antes)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val advanceOptions = listOf(
                                            Triple(0, "A la hora", Icons.Default.AlarmOn),
                                            Triple(5, "5 min antes", Icons.Default.Timer),
                                            Triple(10, "10 min antes", Icons.Default.Timer),
                                            Triple(15, "15 min antes", Icons.Default.Timer),
                                            Triple(30, "30 min antes", Icons.Default.Timer),
                                            Triple(60, "1 hora antes", Icons.Default.HourglassTop)
                                        )
                                        advanceOptions.forEach { (mins, label, icon) ->
                                            val isSelected = reminderMinutesAdvance == mins
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { reminderMinutesAdvance = mins },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                },
                                                label = { Text(label, fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Custom reminder message
                                    OutlinedTextField(
                                        value = reminderCustomMessage,
                                        onValueChange = { reminderCustomMessage = it },
                                        label = { Text("Mensaje o motivación personalizada (opcional)") },
                                        placeholder = { Text("Ej: Prepárate, es momento de cumplir tu meta diaria.") },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 2,
                                        leadingIcon = {
                                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Dynamic calculated notification summary banner
                                    val calcHour = reminderHour.toIntOrNull() ?: 8
                                    val calcMin = reminderMinute.toIntOrNull() ?: 30
                                    val totalMin = (calcHour * 60 + calcMin) - reminderMinutesAdvance
                                    val adjustedTotalMin = if (totalMin < 0) totalMin + 1440 else totalMin
                                    val notifHour = (adjustedTotalMin / 60).toString().padStart(2, '0')
                                    val notifMin = (adjustedTotalMin % 60).toString().padStart(2, '0')

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (reminderMinutesAdvance > 0) {
                                                    "La alerta sonará a las $notifHour:$notifMin ($reminderMinutesAdvance min antes de tu hábito a las ${reminderHour.padStart(2, '0')}:${reminderMinute.padStart(2, '0')})."
                                                } else {
                                                    "La alerta sonará exactamente a las ${reminderHour.padStart(2, '0')}:${reminderMinute.padStart(2, '0')}."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Test notification button
                                    OutlinedButton(
                                        onClick = {
                                            val previewHabit = Habit(
                                                id = initialHabit?.id ?: 9999L,
                                                title = if (title.isNotBlank()) title else "Nuevo Hábito",
                                                description = description,
                                                category = selectedCategory,
                                                colorHex = selectedColorHex,
                                                reminderTime = "${reminderHour.padStart(2, '0')}:${reminderMinute.padStart(2, '0')}",
                                                reminderMinutesAdvance = reminderMinutesAdvance,
                                                reminderCustomMessage = reminderCustomMessage.ifBlank { null },
                                                hasTimer = hasTimer,
                                                timerDurationMinutes = timerMinutesText.toIntOrNull() ?: 25
                                            )
                                            if (onTestReminder != null) {
                                                onTestReminder(previewHabit)
                                            } else {
                                                NotificationHelper.showTestReminderNotification(context, previewHabit)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Probar notificación en este dispositivo", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sub-tasks / Sub-habits nested routines builder
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Sub-rutinas Hijas (Rutina Matutina / Pasos)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = subTaskInput,
                                    onValueChange = { subTaskInput = it },
                                    placeholder = { Text("Añadir paso hijo...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (subTaskInput.isNotBlank()) {
                                            subTasks.add(subTaskInput.trim())
                                            subTaskInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Añadir", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (subTasks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                subTasks.forEachIndexed { idx, st ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "• $st", style = MaterialTheme.typography.bodySmall)
                                        IconButton(onClick = { subTasks.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom Action Buttons: Delete (if edit) & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialHabit != null && onDeleteHabit != null) {
                        TextButton(
                            onClick = {
                                onDeleteHabit(initialHabit.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            val target = targetValueText.toFloatOrNull() ?: 1f
                            val timerMins = timerMinutesText.toIntOrNull() ?: 25
                            val reminder = if (hasReminder) {
                                val h = reminderHour.padStart(2, '0')
                                val m = reminderMinute.padStart(2, '0')
                                "$h:$m"
                            } else null

                            val habitToSave = (initialHabit ?: Habit(title = title)).copy(
                                title = title.trim(),
                                description = description.trim(),
                                category = selectedCategory,
                                colorHex = selectedColorHex,
                                iconName = selectedIconName,
                                frequencyDays = frequencyDays,
                                unit = if (hasMetric) unit.trim() else "",
                                targetValue = if (hasMetric) target else 1f,
                                hasTimer = hasTimer,
                                timerDurationMinutes = timerMins,
                                reminderTime = reminder,
                                reminderMinutesAdvance = if (hasReminder) reminderMinutesAdvance else 0,
                                reminderCustomMessage = if (hasReminder && reminderCustomMessage.isNotBlank()) reminderCustomMessage.trim() else null,
                                dependencyHabitId = selectedDependencyId
                            )
                            onSaveHabit(habitToSave, subTasks.toList())
                            onDismiss()
                        }
                    ) {
                        Text("Guardar Hábito")
                    }
                }
            }
        }
    }
}
