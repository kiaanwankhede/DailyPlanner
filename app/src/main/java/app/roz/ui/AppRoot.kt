package app.roz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.CalendarToday),
    HABITS("Habits", Icons.Outlined.LocalFireDepartment),
    CHORES("Chores", Icons.Outlined.Home),
    BIRTHDAYS("Birthdays", Icons.Outlined.CardGiftcard),
    REMINDERS("Reminders", Icons.Outlined.Notifications),
}

enum class EditType(val label: String) { HABIT("Habit"), CHORE("Chore"), REMINDER("Reminder"), BIRTHDAY("Birthday") }

sealed interface Route {
    data object Tabs : Route
    data class Edit(val type: EditType, val id: Long? = null) : Route
    data object Settings : Route
    data class HabitDetail(val id: Long) : Route
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var route by remember { mutableStateOf<Route>(Route.Tabs) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDay() }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                val result = snackbar.showSnackbar(
                    message.text,
                    actionLabel = if (message.undo != null) "Undo" else null,
                    withDismissAction = false,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.undo?.let { scope.launch { it() } }
            }
        }
    }

    BackHandler(enabled = route != Route.Tabs) { route = Route.Tabs }

    val newType = when (tab) {
        Tab.HABITS -> EditType.HABIT
        Tab.BIRTHDAYS -> EditType.BIRTHDAY
        Tab.REMINDERS -> EditType.REMINDER
        else -> EditType.CHORE
    }

    Scaffold(
        containerColor = palette.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == Route.Tabs) {
                NavigationBar(containerColor = palette.card) {
                    Tab.entries.forEach { t ->
                        val accent = when (t) {
                            Tab.TODAY, Tab.HABITS -> palette.habit
                            Tab.CHORES -> palette.chore
                            Tab.BIRTHDAYS -> palette.birthday
                            Tab.REMINDERS -> palette.reminder
                        }
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, fontWeight = if (tab == t) FontWeight.ExtraBold else FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Route.Tabs) {
                FloatingActionButton(
                    onClick = { route = Route.Edit(newType) },
                    containerColor = palette.ink,
                    contentColor = palette.ground,
                    shape = RoundedCornerShape(20),
                ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val edit: (EditType, Long?) -> Unit = { type, id -> route = Route.Edit(type, id) }
            when (val r = route) {
                Route.Tabs -> when (tab) {
                    Tab.TODAY -> TodayScreen(state, vm, onEdit = edit, onSettings = { route = Route.Settings }, onTab = { tab = it }, onHabit = { route = Route.HabitDetail(it) })
                    Tab.HABITS -> HabitsScreen(state, vm, onHabit = { route = Route.HabitDetail(it) })
                    Tab.CHORES -> ChoresScreen(state, vm, onEdit = edit)
                    Tab.BIRTHDAYS -> BirthdaysScreen(state, vm, onEdit = edit)
                    Tab.REMINDERS -> RemindersScreen(state, vm, onEdit = edit)
                }
                is Route.Edit -> EditScreen(state, vm, r.type, r.id, onDone = { route = Route.Tabs })
                Route.Settings -> SettingsScreen(state, vm, onBack = { route = Route.Tabs })
                is Route.HabitDetail -> HabitDetailScreen(state, vm, r.id, onBack = { route = Route.Tabs }, onEdit = edit)
            }
        }
    }
}
