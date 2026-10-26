@file:OptIn(ExperimentalFoundationApi::class)

package app.roz.ui

import app.roz.core.FocusView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.core.HabitKind
import app.roz.core.HabitToday
import app.roz.core.Occasions
import app.roz.core.Upcoming
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    state: UiState,
    vm: AppViewModel,
    onEdit: (EditType, Long?) -> Unit,
    onSettings: () -> Unit,
    onTab: (Tab) -> Unit,
    onHabit: (Long) -> Unit,
) {
    val plan = state.plan
    var checkPassphrase by remember { mutableStateOf(false) }
    val remindersById = state.reminders.associateBy { it.reminder.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)).uppercase(),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.muted, letterSpacing = 0.3.sp,
                    )
                    Text("Today", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
                }
                ProgressRing(plan.done, plan.total)
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = palette.muted) }
            }
        }

        if (state.loaded) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !state.backupSetUp -> Banner("Your data isn't backed up yet.", "Set up", palette.reminderSoft, palette.reminder, onSettings)
                        state.backupStale -> Banner("No backup for 3 days or more.", "Back up now", palette.reminderSoft, palette.reminder) { vm.backUpNow() }
                        state.passphraseCheckDue -> Banner("Do you still know your backup passphrase?", "Check", palette.choreSoft, palette.chore) { checkPassphrase = true }
                    }
                }
            }
        }

        state.focus?.let { f ->
            item(key = "focus") {
                FocusCard(f, onDone = { vm.completeFocus() }, onRemove = { vm.clearFocus() })
                Spacer(Modifier.height(4.dp))
            }
        }

        if (plan.habits.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Habits", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.ink, modifier = Modifier.weight(1f))
                    val done = plan.habits.count { it.stats.doneToday }
                    Text(
                        "$done of ${plan.habits.size} · See all",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.habit,
                        modifier = Modifier.clickable { onTab(Tab.HABITS) }.padding(vertical = 8.dp),
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    plan.habits.forEach { h -> HabitBubble(h, onTick = { vm.tickHabit(h.habit) }, onLong = { onHabit(h.habit.id) }) }
                }
            }
        }

        if (plan.overdue.isNotEmpty()) {
            item { SectionLabel("Overdue", palette.late) }
            items(plan.overdue, key = { "o${it.chore.id}" }) {
                ChoreRow(it, state.today, vm, onEdit, showKind = true, focus = state.focusPick)
                SmallGap()
            }
        }

        if (plan.choresToday.isNotEmpty() || plan.remindersToday.isNotEmpty()) {
            item { SectionLabel("Today") }
            items(plan.remindersToday, key = { "r${it.id}" }) { r ->
                remindersById[r.id]?.let { ReminderRow(it, state.today, vm, onEdit, showKind = true, focus = state.focusPick) }
                SmallGap()
            }
            items(plan.choresToday, key = { "c${it.chore.id}" }) {
                ChoreRow(it, state.today, vm, onEdit, showKind = true, focus = state.focusPick)
                SmallGap()
            }
        }

        if (plan.birthdays.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Birthdays this week", modifier = Modifier.weight(1f))
                    Text("All", color = palette.habit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.clickable { onTab(Tab.BIRTHDAYS) }.padding(8.dp))
                }
            }
            items(plan.birthdays, key = { "b${it.person.id}" }) {
                BirthdayStrip(it) { onTab(Tab.BIRTHDAYS) }
                SmallGap()
            }
        }

        if (state.loaded && plan.habits.isEmpty() && plan.overdue.isEmpty() && plan.choresToday.isEmpty() &&
            plan.remindersToday.isEmpty() && plan.birthdays.isEmpty()
        ) {
            item {
                EmptyNote(
                    if (state.habits.isEmpty() && state.chores.isEmpty() && state.reminders.isEmpty() && state.upcoming.isEmpty()) {
                        "Nothing here yet. Tap ＋ to add a habit, chore, reminder or birthday."
                    } else {
                        "All clear for today."
                    },
                )
            }
        }
    }

    if (checkPassphrase) {
        PassphraseCheckDialog(vm, onDismiss = { checkPassphrase = false })
    }
}

/** The top-priority item, big and dark so it can't be missed (SPEC.md §11). */
@Composable
private fun FocusCard(view: FocusView, onDone: () -> Unit, onRemove: () -> Unit) {
    ItemCard(fill = palette.ink, contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = palette.ground, modifier = Modifier.size(16.dp))
            Text(
                "TOP PRIORITY",
                modifier = Modifier.padding(start = 6.dp).weight(1f),
                fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = palette.ground.copy(alpha = 0.8f),
            )
            Text(view.kindLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.ground.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(8.dp))
        Text(view.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.ground)
        Text(view.detail, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.ground.copy(alpha = 0.85f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (view.canComplete) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.ground, contentColor = palette.ink),
                ) { Text(view.doneLabel, fontWeight = FontWeight.ExtraBold) }
            }
            TextButton(onClick = onRemove, modifier = Modifier.height(44.dp)) {
                Text("Remove", color = palette.ground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProgressRing(done: Int, total: Int) {
    val ring = palette.habit
    val track = palette.line
    Box(Modifier.size(64.dp).semantics { contentDescription = "$done of $total done" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke)
            if (total > 0 && done > 0) drawArc(ring, -90f, 360f * done / total, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$total", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
            Text("done", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = palette.muted)
        }
    }
}

@Composable
private fun HabitBubble(h: HabitToday, onTick: () -> Unit, onLong: () -> Unit) {
    val done = h.stats.doneToday
    val counting = h.habit.kind != HabitKind.YES_NO
    val partial = counting && !done && h.stats.todayValue > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (done) palette.habit else if (partial) palette.habitSoft else Color.Transparent)
                .border(2.dp, if (done || partial) palette.habit else palette.muted.copy(alpha = 0.6f), CircleShape)
                .combinedClickable(onClick = onTick, onLongClick = onLong)
                .semantics { contentDescription = "${h.habit.name}, ${if (done) "done" else "not done"}" },
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Filled.Check, null, tint = Color.White)
                counting -> Text("${h.stats.todayValue}/${h.habit.target}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = palette.habit)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(h.habit.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun BirthdayStrip(u: Upcoming, onClick: () -> Unit) {
    ItemCard(fill = palette.birthdaySoft, modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(palette.birthdayStrong), contentAlignment = Alignment.Center) {
                Text(u.person.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(Occasions.headline(u), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                val gifts = u.person.gifts.size
                Text(
                    listOfNotNull(
                        u.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)),
                        Occasions.daysAwayLabel(u.daysAway).let { if (u.daysAway > 1) "in $it" else it },
                        if (gifts > 0) "$gifts gift idea${if (gifts == 1) "" else "s"}" else null,
                    ).joinToString(" · "),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.birthday,
                )
            }
            Icon(Icons.Outlined.CardGiftcard, null, tint = palette.birthday)
        }
    }
}
