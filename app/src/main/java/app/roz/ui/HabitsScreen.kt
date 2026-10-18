package app.roz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.core.DayMark
import app.roz.core.HabitKind
import app.roz.core.HabitMath
import app.roz.data.DeletedItem
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

@Composable
fun HabitsScreen(state: UiState, vm: AppViewModel, onHabit: (Long) -> Unit) {
    val done = state.habits.count { it.stats.doneToday }
    val freeSkip = state.habits.any { it.habit.perWeek == null && it.stats.freeSkipLeft }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item {
            ScreenHeader(
                "Habits",
                if (state.habits.isEmpty()) null
                else "$done of ${state.habits.size} done today" + if (freeSkip) " · free skip left this week" else "",
            )
        }
        if (state.loaded && state.habits.isEmpty()) {
            item { EmptyNote("No habits yet. Tap ＋ to add one, like “Walk 8,000 steps” or “Read 20 minutes”.") }
        }
        items(state.habits, key = { it.habit.id }) { row ->
            HabitCard(row, vm, onClick = { onHabit(row.habit.id) })
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HabitCard(row: HabitRow, vm: AppViewModel, onClick: () -> Unit) {
    val habit = row.habit
    val stats = row.stats
    ItemCard(modifier = Modifier.clickable(onClick = onClick), contentPadding = PaddingValues(14.dp, 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(habit.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                Text(HabitMath.targetLabel(habit, stats), fontSize = 12.sp, color = palette.muted)
            }
            StreakAndStrength(row)
            if (habit.kind != HabitKind.YES_NO) {
                IconButton(onClick = { vm.untickHabit(habit) }, enabled = stats.todayValue > 0) {
                    Icon(Icons.Filled.Remove, contentDescription = "One less", tint = palette.muted)
                }
            }
            HabitButton(row) { vm.tickHabit(habit) }
        }
        Spacer(Modifier.height(10.dp))
        WeekDots(stats.week)
    }
}

@Composable
private fun StreakAndStrength(row: HabitRow) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = palette.flame, modifier = Modifier.size(16.dp))
            Text(
                if (row.stats.streakInWeeks) "${row.stats.streak} wk" else "${row.stats.streak}",
                fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.flame,
            )
        }
        Text("${row.stats.strength}% strong", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.habit)
    }
}

@Composable
private fun HabitButton(row: HabitRow, onClick: () -> Unit) {
    val done = row.stats.doneToday
    val counting = row.habit.kind != HabitKind.YES_NO
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (done) palette.habit else Color.Transparent)
            .border(2.dp, if (done) palette.habit else palette.muted.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${row.habit.name}: ${if (done) "done today" else "mark done"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (done || !counting) {
            Icon(Icons.Filled.Check, null, tint = if (done) Color.White else palette.muted, modifier = Modifier.size(20.dp))
        } else {
            Text("${row.stats.todayValue}/${row.habit.target}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = palette.habit)
        }
    }
}

@Composable
fun WeekDots(week: List<DayMark>) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, mark ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DayDot(mark)
                Spacer(Modifier.height(4.dp))
                Text(letters[i], fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.muted)
            }
        }
    }
}

@Composable
private fun DayDot(mark: DayMark) {
    val m = Modifier.size(22.dp).clip(CircleShape)
    when (mark) {
        DayMark.DONE -> Box(m.background(palette.habit))
        DayMark.FORGIVEN -> Box(m.background(palette.habitSoft).border(2.dp, palette.habit.copy(alpha = 0.45f), CircleShape))
        DayMark.MISSED -> Box(m.border(2.dp, palette.line, CircleShape))
        DayMark.PENDING -> Box(m.border(2.dp, palette.habit, CircleShape))
        DayMark.EMPTY -> Box(m.background(palette.ground))
    }
}

@Composable
fun HabitDetailScreen(state: UiState, vm: AppViewModel, id: Long, onBack: () -> Unit, onEdit: (EditType, Long?) -> Unit) {
    val row = state.habits.firstOrNull { it.habit.id == id }
    if (row == null) {
        LaunchedEffect(id) { onBack() }
        return
    }
    val days = row.days.associateBy { it.date }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = palette.ink) }
            }
            Text(row.habit.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
            Text(HabitMath.targetLabel(row.habit, row.stats), fontSize = 14.sp, color = palette.muted)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Streak", HabitMath.streakLabel(row.stats), Modifier.weight(1f))
                StatTile("Strength", "${row.stats.strength}%", Modifier.weight(1f))
                StatTile("Done", "${row.days.count { !it.skipped && HabitMath.isMet(row.habit, it.value) }} days", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            ItemCard(contentPadding = PaddingValues(14.dp)) {
                Text("Last 15 weeks", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                Spacer(Modifier.height(10.dp))
                val monday = state.today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(14)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (w in 0 until 15) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (d in 0 until 7) {
                                val date = monday.plusWeeks(w.toLong()).plusDays(d.toLong())
                                val entry = days[date]
                                val level = when {
                                    date.isAfter(state.today) || date.isBefore(row.habit.createdOn) -> -1
                                    entry == null -> if (row.habit.kind == HabitKind.AT_MOST && date.isBefore(state.today)) 3 else 0
                                    entry.skipped -> 1
                                    HabitMath.isMet(row.habit, entry.value) -> 3
                                    entry.value > 0 -> 2
                                    else -> 0
                                }
                                val color = when (level) {
                                    -1 -> Color.Transparent
                                    0 -> palette.line
                                    1 -> palette.habitSoft
                                    2 -> palette.habit.copy(alpha = 0.55f)
                                    else -> palette.habit
                                }
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(color))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.skipHabit(row.habit) }, modifier = Modifier.weight(1f)) { Text("Skip today", color = palette.ink) }
                OutlinedButton(onClick = { onEdit(EditType.HABIT, id) }, modifier = Modifier.weight(1f)) { Text("Edit", color = palette.ink) }
                OutlinedButton(
                    onClick = { vm.delete(DeletedItem.Type.HABIT, id, row.habit.name); onBack() },
                    modifier = Modifier.weight(1f),
                ) { Text("Delete", color = palette.late) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Strength rises with every done day and drops a little with a miss, so one bad day never wipes out your progress. " +
                    "One missed day a week is forgiven automatically.",
                fontSize = 12.sp, color = palette.muted,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(palette.card).padding(12.dp)) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
        Text(label, fontSize = 12.sp, color = palette.muted)
    }
}
