package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Habit
import com.example.model.HabitWithStats
import com.example.model.ThemeMode
import com.example.model.ViewLayoutMode
import com.example.ui.components.*
import com.example.util.DateUtils
import com.example.viewmodel.HabitViewModel
import com.example.viewmodel.NavigationTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFlowApp(
    viewModel: HabitViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Dialog States
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var quantitativeHabitTarget by remember { mutableStateOf<HabitWithStats?>(null) }
    var heatmapHabitTarget by remember { mutableStateOf<HabitWithStats?>(null) }
    var pomodoroHabitTarget by remember { mutableStateOf<HabitWithStats?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }
    var showCreateCategoryDialogMain by remember { mutableStateOf(false) }
    var showThemeSwitcherDialog by remember { mutableStateOf(false) }
    var showLayoutDropdown by remember { mutableStateOf(false) }

    // Handle Snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    // Filter habits by category & search query
    val filteredHabits = remember(uiState.habits, uiState.selectedCategory, uiState.searchQuery) {
        uiState.habits.filter { habitStat ->
            val matchesCategory = uiState.selectedCategory == null || habitStat.habit.category == uiState.selectedCategory
            val matchesSearch = uiState.searchQuery.isEmpty() ||
                    habitStat.habit.title.contains(uiState.searchQuery, ignoreCase = true) ||
                    habitStat.habit.description.contains(uiState.searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val completedTodayCount = uiState.habits.count { it.isCompletedToday }
    val totalTodayHabits = maxOf(1, uiState.habits.size)
    val todayCompletionPercentage = (completedTodayCount * 100) / totalTodayHabits

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            AppNavigationDrawerContent(
                activeTab = uiState.activeTab,
                themeMode = uiState.themeMode,
                dynamicColor = uiState.dynamicColor,
                userLevel = uiState.userStats.level,
                currentXp = uiState.userStats.xp,
                streakCount = uiState.userStats.bestStreakAllTime,
                onSelectTab = { tab -> viewModel.setNavigationTab(tab) },
                onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                onToggleDynamicColor = { enabled -> viewModel.setDynamicColor(enabled) },
                onOpenThemeDialog = { showThemeSwitcherDialog = true },
                onOpenTemplates = { showTemplatePicker = true },
                onExportJson = { viewModel.getExportJson() },
                onExportCsv = { viewModel.getExportCsv() },
                onRescheduleReminders = { viewModel.rescheduleAllReminders() },
                onCloseDrawer = {
                    coroutineScope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    // Top Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Hamburger Menu + Logo & Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            // Hamburger Menu Button (Menú del lado izquierdo)
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                },
                                modifier = Modifier.testTag("menu_drawer_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Abrir menú de navegación",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Logo & App Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { viewModel.setNavigationTab(NavigationTab.TODAY) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6366F1), Color(0xFF06B6D4))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Text(
                                        text = "HabitFlow",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$completedTodayCount/$totalTodayHabits hoy ($todayCompletionPercentage%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Right Actions: XP Level Badge, Templates, Search, Layout Switcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            // Level Badge (opens gamification tab)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                modifier = Modifier.clickable {
                                    viewModel.setNavigationTab(NavigationTab.GAMIFICATION)
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                ) {
                                    Text(text = "⭐", fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Lv.${uiState.userStats.level}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6366F1)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { showTemplatePicker = true },
                                modifier = Modifier.testTag("templates_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Plantillas",
                                    tint = Color(0xFFF59E0B)
                                )
                            }

                            IconButton(
                                onClick = { showSearchField = !showSearchField },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar"
                                )
                            }

                            // Layout switch dropdown in TODAY tab
                            if (uiState.activeTab == NavigationTab.TODAY) {
                                Box {
                                    IconButton(
                                        onClick = { showLayoutDropdown = true },
                                        modifier = Modifier.testTag("layout_selector_button")
                                    ) {
                                        Icon(
                                            imageVector = when (uiState.layoutMode) {
                                                ViewLayoutMode.LIST -> Icons.Default.ViewList
                                                ViewLayoutMode.HEATMAP -> Icons.Default.GridOn
                                                ViewLayoutMode.KANBAN -> Icons.Default.ViewKanban
                                                ViewLayoutMode.TIMELINE -> Icons.Default.Timeline
                                            },
                                            contentDescription = "Cambiar vista"
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showLayoutDropdown,
                                        onDismissRequest = { showLayoutDropdown = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Lista (Predeterminado)") },
                                            leadingIcon = { Icon(Icons.Default.ViewList, contentDescription = null) },
                                            trailingIcon = if (uiState.layoutMode == ViewLayoutMode.LIST) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                            onClick = {
                                                viewModel.setLayoutMode(ViewLayoutMode.LIST)
                                                showLayoutDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mapa de calor") },
                                            leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, tint = Color(0xFF6366F1)) },
                                            trailingIcon = if (uiState.layoutMode == ViewLayoutMode.HEATMAP) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                            onClick = {
                                                viewModel.setLayoutMode(ViewLayoutMode.HEATMAP)
                                                showLayoutDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Tablero Kanban") },
                                            leadingIcon = { Icon(Icons.Default.ViewKanban, contentDescription = null) },
                                            trailingIcon = if (uiState.layoutMode == ViewLayoutMode.KANBAN) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                            onClick = {
                                                viewModel.setLayoutMode(ViewLayoutMode.KANBAN)
                                                showLayoutDropdown = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Línea de tiempo") },
                                            leadingIcon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                                            trailingIcon = if (uiState.layoutMode == ViewLayoutMode.TIMELINE) {
                                                { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                            } else null,
                                            onClick = {
                                                viewModel.setLayoutMode(ViewLayoutMode.TIMELINE)
                                                showLayoutDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                // Expandable Search Bar
                AnimatedVisibility(
                    visible = showSearchField,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Buscar hábito o categoría...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                // Date Navigation Bar (if in TODAY tab)
                if (uiState.activeTab == NavigationTab.TODAY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val todayStr = remember { DateUtils.getTodayDateString() }
                        val yesterdayStr = remember { DateUtils.getDaysAgoDateString(1) }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = uiState.selectedDate == yesterdayStr,
                                onClick = { viewModel.setSelectedDate(yesterdayStr) },
                                label = { Text("Ayer") }
                            )
                            FilterChip(
                                selected = uiState.selectedDate == todayStr,
                                onClick = { viewModel.setSelectedDate(todayStr) },
                                label = { Text("Hoy") }
                            )
                        }

                        Text(
                            text = DateUtils.formatDateForDisplay(uiState.selectedDate),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Category Filter Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.setSelectedCategory(null) },
                            label = { Text("Todos (${uiState.habits.size})") }
                        )

                        uiState.categories.forEach { cat ->
                            val count = uiState.habits.count { it.habit.category == cat.name }
                            val catColor = try {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            FilterChip(
                                selected = uiState.selectedCategory == cat.name,
                                onClick = { viewModel.setSelectedCategory(cat.name) },
                                label = { Text("${cat.name} ($count)") },
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

                        // Add Custom Category Chip
                        AssistChip(
                            onClick = { showCreateCategoryDialogMain = true },
                            label = { Text("+ Nueva", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Crear Categoría",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.TODAY,
                    onClick = { viewModel.setNavigationTab(NavigationTab.TODAY) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Hoy") },
                    label = { Text("Hoy") }
                )

                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.CALENDAR,
                    onClick = { viewModel.setNavigationTab(NavigationTab.CALENDAR) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendario") },
                    label = { Text("Calendario") }
                )

                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.TIMER,
                    onClick = { viewModel.setNavigationTab(NavigationTab.TIMER) },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Enfoque") },
                    label = { Text("Enfoque") }
                )

                NavigationBarItem(
                    selected = uiState.activeTab == NavigationTab.GAMIFICATION,
                    onClick = { viewModel.setNavigationTab(NavigationTab.GAMIFICATION) },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Logros") },
                    label = { Text("Logros") }
                )
            }
        },
        floatingActionButton = {
            if (uiState.activeTab == NavigationTab.TODAY) {
                FloatingActionButton(
                    onClick = {
                        editingHabit = null
                        showAddEditDialog = true
                    },
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("add_habit_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nuevo Hábito"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (uiState.activeTab) {
                NavigationTab.TODAY -> {
                    if (filteredHabits.isEmpty()) {
                        EmptyHabitsState(
                            onAddHabit = {
                                editingHabit = null
                                showAddEditDialog = true
                            },
                            onPickTemplate = { showTemplatePicker = true }
                        )
                    } else {
                        when (uiState.layoutMode) {
                            ViewLayoutMode.LIST -> {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredHabits, key = { it.habit.id }) { habitStat ->
                                        HabitTileCard(
                                            habitWithStats = habitStat,
                                            isGridView = false,
                                            onToggleCompletion = { viewModel.toggleHabitCompletion(habitStat.habit.id) },
                                            onOpenProgressDialog = { quantitativeHabitTarget = habitStat },
                                            onStartTimer = { pomodoroHabitTarget = habitStat },
                                            onToggleSubTask = { st, completed -> viewModel.toggleSubTask(st.id, completed) },
                                            onEditHabit = {
                                                editingHabit = habitStat.habit
                                                showAddEditDialog = true
                                            },
                                            onArchiveHabit = { viewModel.setArchived(habitStat.habit.id, true) },
                                            onTestReminder = { viewModel.testHabitReminder(habitStat.habit) }
                                        )
                                    }
                                }
                            }

                            ViewLayoutMode.HEATMAP -> {
                                HabitsHeatmapLayout(
                                    habits = filteredHabits,
                                    allLogs = uiState.allLogs,
                                    onToggleCompletion = { viewModel.toggleHabitCompletion(it) },
                                    onOpenProgressDialog = { quantitativeHabitTarget = it },
                                    onToggleDateCompletion = { habitId, dateStr ->
                                        viewModel.toggleHabitCompletion(habitId, dateStr)
                                    },
                                    onEditHabit = {
                                        editingHabit = it.habit
                                        showAddEditDialog = true
                                    }
                                )
                            }

                            ViewLayoutMode.KANBAN -> {
                                KanbanView(
                                    habits = filteredHabits,
                                    onToggleCompletion = { viewModel.toggleHabitCompletion(it) },
                                    onOpenProgressDialog = { quantitativeHabitTarget = it },
                                    onStartTimer = { pomodoroHabitTarget = it },
                                    onEditHabit = {
                                        editingHabit = it.habit
                                        showAddEditDialog = true
                                    },
                                    onArchiveHabit = { viewModel.setArchived(it, true) }
                                )
                            }

                            ViewLayoutMode.TIMELINE -> {
                                TimelineView(
                                    habits = filteredHabits,
                                    onToggleCompletion = { viewModel.toggleHabitCompletion(it) },
                                    onOpenProgressDialog = { quantitativeHabitTarget = it },
                                    onStartTimer = { pomodoroHabitTarget = it },
                                    onEditHabit = {
                                        editingHabit = it.habit
                                        showAddEditDialog = true
                                    },
                                    onArchiveHabit = { viewModel.setArchived(it, true) }
                                )
                            }
                        }
                    }
                }

                NavigationTab.HEATMAP -> {
                    HeatmapView(
                        habits = uiState.habits.map { it.habit },
                        allLogs = uiState.allLogs,
                        onSelectDate = { dateStr ->
                            viewModel.setSelectedDate(dateStr)
                            viewModel.setNavigationTab(NavigationTab.TODAY)
                        }
                    )
                }

                NavigationTab.CALENDAR -> {
                    CalendarMonthView(
                        selectedDate = uiState.selectedDate,
                        habitsWithStats = uiState.habits,
                        allLogs = uiState.allLogs,
                        onSelectDate = { dateStr ->
                            viewModel.setSelectedDate(dateStr)
                        },
                        onToggleHabitCompletion = { habitId ->
                            viewModel.toggleHabitCompletion(habitId, uiState.selectedDate)
                        }
                    )
                }

                NavigationTab.TIMER -> {
                    FocusTimerSheet(
                        timerState = uiState.activeTimer,
                        habits = uiState.habits.map { it.habit },
                        onTogglePlayPause = { viewModel.toggleTimerPlayPause() },
                        onResetTimer = { viewModel.resetTimer() },
                        onCompleteEarly = { viewModel.completeTimerEarly() },
                        onSelectHabitForTimer = { h, isPomodoro ->
                            viewModel.startTimerForHabit(h, isPomodoro)
                        }
                    )
                }

                NavigationTab.ANALYTICS -> {
                    AnalyticsScreen(
                        habits = uiState.habits.map { it.habit },
                        allLogs = uiState.allLogs,
                        insights = uiState.insights,
                        themeMode = uiState.themeMode,
                        dynamicColor = uiState.dynamicColor,
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onToggleDynamicColor = { viewModel.setDynamicColor(it) },
                        onExportJson = { viewModel.getExportJson() },
                        onExportCsv = { viewModel.getExportCsv() }
                    )
                }

                NavigationTab.GAMIFICATION -> {
                    GamificationDashboard(
                        userStats = uiState.userStats,
                        onToggleHardcoreMode = { viewModel.toggleHardcoreMode(it) }
                    )
                }
            }
        }
    }
    }

    // Add / Edit Habit Dialog
    if (showAddEditDialog) {
        AddEditHabitDialog(
            initialHabit = editingHabit,
            categories = uiState.categories,
            allHabits = uiState.habits.map { it.habit },
            onDismiss = { showAddEditDialog = false },
            onSaveHabit = { habit, subTasks ->
                viewModel.saveHabit(habit, subTasks)
            },
            onCreateCategory = { newCategory ->
                viewModel.addCategory(newCategory)
            },
            onDeleteHabit = { habitId ->
                viewModel.deleteHabit(habitId)
            },
            onTestReminder = { habit ->
                viewModel.testHabitReminder(habit)
            }
        )
    }

    // Main Screen Create Category Dialog
    if (showCreateCategoryDialogMain) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialogMain = false },
            onCategoryCreated = { newCategory ->
                viewModel.addCategory(newCategory)
                viewModel.setSelectedCategory(newCategory.name)
                showCreateCategoryDialogMain = false
            }
        )
    }

    // Quantitative Progress Dialog
    quantitativeHabitTarget?.let { hStat ->
        QuantitativeLogDialog(
            habitWithStats = hStat,
            onDismiss = { quantitativeHabitTarget = null },
            onSaveProgress = { valAmount, notes ->
                viewModel.recordQuantitativeProgress(hStat.habit.id, valAmount, notes)
            }
        )
    }

    // Template Picker Dialog
    if (showTemplatePicker) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onSelectTemplate = { template ->
                viewModel.applyTemplate(template)
            }
        )
    }

    // Individual Habit Heatmap Dialog
    heatmapHabitTarget?.let { habitStat ->
        HabitDetailHeatmapDialog(
            habitWithStats = habitStat,
            allLogs = uiState.allLogs,
            onDismiss = { heatmapHabitTarget = null },
            onToggleDateCompletion = { dateStr ->
                viewModel.toggleHabitCompletion(habitStat.habit.id, dateStr)
            }
        )
    }

    // Individual Habit Pomodoro & Stopwatch Dialog
    pomodoroHabitTarget?.let { habitStat ->
        HabitPomodoroDialog(
            habitWithStats = habitStat,
            timerState = uiState.activeTimer,
            onDismiss = { pomodoroHabitTarget = null },
            onStartTimer = { durationMins, isPomodoro ->
                viewModel.startTimerForHabit(
                    habit = habitStat.habit,
                    isPomodoro = isPomodoro,
                    durationMinutes = durationMins,
                    switchTab = false
                )
            },
            onTogglePlayPause = { viewModel.toggleTimerPlayPause() },
            onResetTimer = { viewModel.resetTimer() },
            onCompleteAndSave = { viewModel.completeTimerEarly() },
            onOpenFullScreen = {
                viewModel.setNavigationTab(NavigationTab.TIMER)
            }
        )
    }

    // Dynamic Theme Switcher Dialog
    if (showThemeSwitcherDialog) {
        ThemeSwitcherDialog(
            currentThemeMode = uiState.themeMode,
            isDynamicColor = uiState.dynamicColor,
            onSelectThemeMode = { mode ->
                viewModel.setThemeMode(mode)
            },
            onToggleDynamicColor = { dyn ->
                viewModel.setDynamicColor(dyn)
            },
            onDismiss = { showThemeSwitcherDialog = false }
        )
    }
}

@Composable
private fun EmptyHabitsState(
    onAddHabit: () -> Unit,
    onPickTemplate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🚀", fontSize = 36.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "¡Empieza tu Viaje de Hábitos!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Crea un hábito personalizado o añade una rutina probada con nuestras plantillas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAddHabit,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Crear Hábito")
            }

            OutlinedButton(
                onClick = onPickTemplate,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFF59E0B))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Plantillas")
            }
        }
    }
}
