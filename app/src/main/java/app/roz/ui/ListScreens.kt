package app.roz.ui

import app.roz.core.OccasionKind
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.core.ChoreGroup
import app.roz.core.ChoreSchedule
import app.roz.core.Occasions
import app.roz.core.Reminders
import app.roz.core.Upcoming
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------- Chores ----------------

@Composable
fun ChoresScreen(state: UiState, vm: AppViewModel, onEdit: (EditType, Long?) -> Unit) {
    var area by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = state.chores.filter { area == null || it.chore.area == area }
    val active = shown.filter { !it.chore.paused }
    val grouped = active.groupBy { ChoreSchedule.group(it.status, state.today) }
    val overdue = grouped[ChoreGroup.OVERDUE].orEmpty().size
    val week = grouped[ChoreGroup.TODAY].orEmpty().size + grouped[ChoreGroup.THIS_WEEK].orEmpty().size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item { ScreenHeader("Chores", if (state.chores.isEmpty()) null else "$overdue overdue · $week due this week") }
        if (state.areas.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AreaChip("All", area == null) { area = null }
                    state.areas.forEach { a -> AreaChip(a, area == a) { area = if (area == a) null else a } }
                }
            }
        }
        if (state.loaded && state.chores.isEmpty()) {
            item { EmptyNote("No chores yet. Tap ＋ to add one, like “Clean bathroom every Sunday” or “Water plants 3 days after last done”.") }
        }
        val labels = mapOf(
            ChoreGroup.OVERDUE to "Overdue", ChoreGroup.TODAY to "Today",
            ChoreGroup.THIS_WEEK to "This week", ChoreGroup.LATER to "Later",
        )
        ChoreGroup.entries.forEach { group ->
            val list = grouped[group].orEmpty().sortedWith(compareBy({ it.status.due }, { it.chore.name }))
            if (list.isNotEmpty()) {
                item(key = "h$group") { SectionLabel(labels.getValue(group), if (group == ChoreGroup.OVERDUE) palette.late else palette.muted) }
                items(list, key = { "c${it.chore.id}" }) {
                    ChoreRow(it, state.today, vm, onEdit)
                    SmallGap()
                }
            }
        }
        val paused = shown.filter { it.chore.paused }
        if (paused.isNotEmpty()) {
            item(key = "hp") { SectionLabel("Paused") }
            items(paused, key = { "p${it.chore.id}" }) {
                ChoreRow(it, state.today, vm, onEdit)
                SmallGap()
            }
        }
    }
}

@Composable
private fun AreaChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.ink,
            selectedLabelColor = palette.ground,
            labelColor = palette.ink,
            containerColor = palette.card,
        ),
    )
}

// ---------------- Birthdays ----------------

@Composable
fun BirthdaysScreen(state: UiState, vm: AppViewModel, onEdit: (EditType, Long?) -> Unit) {
    val context = LocalContext.current
    val next = state.upcoming.firstOrNull()
    val anniversaries = state.upcoming.count { it.person.kind == OccasionKind.ANNIVERSARY }
    var importing by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item {
            ScreenHeader(
                "Birthdays",
                if (state.upcoming.isEmpty()) null else "${state.upcoming.size - anniversaries} people · $anniversaries anniversaries",
            ) {
                OutlinedButton(onClick = { importing = true }, shape = RoundedCornerShape(12.dp)) {
                    Text("Import contacts", color = palette.ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (state.loaded && next == null) {
            item { EmptyNote("No birthdays yet. Tap ＋ to add one, or import them from your contacts.") }
        }
        if (next != null) {
            item {
                NextUpCard(next, vm, onWish = { sendWish(context, next) }, onEdit = { onEdit(EditType.BIRTHDAY, next.person.id) })
                Spacer(Modifier.height(8.dp))
            }
        }
        val rest = state.upcoming.drop(1)
        if (rest.isNotEmpty()) {
            item { SectionLabel("Coming up") }
            items(rest, key = { it.person.id }) { u ->
                ItemCard(modifier = Modifier.clickable { onEdit(EditType.BIRTHDAY, u.person.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.size(44.dp, 46.dp).clip(RoundedCornerShape(10.dp)).background(palette.ground),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("%02d".format(u.date.dayOfMonth), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
                            Text(u.date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.muted)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(u.person.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                            Text(Occasions.detail(u).ifBlank { "Birthday" }, fontSize = 12.sp, color = palette.muted)
                        }
                        Text(Occasions.daysAwayLabel(u.daysAway), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    }
                }
                SmallGap()
            }
        }
    }

    if (importing) ImportContactsDialog(vm, onDismiss = { importing = false })
}

@Composable
private fun NextUpCard(u: Upcoming, vm: AppViewModel, onWish: () -> Unit, onEdit: () -> Unit) {
    ItemCard(fill = palette.birthdaySoft, contentPadding = PaddingValues(18.dp), modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(palette.birthdayStrong), contentAlignment = Alignment.Center) {
                Text(u.person.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    "NEXT UP · ${Occasions.daysAwayLabel(u.daysAway).uppercase().let { if (u.daysAway > 1) "IN $it" else it }}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.birthday, letterSpacing = 0.6.sp,
                )
                Text(Occasions.headline(u), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink)
                Text(
                    listOfNotNull(u.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), u.person.relation.ifBlank { null }).joinToString(" · "),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.birthday,
                )
            }
        }
        if (u.person.gifts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.card).padding(horizontal = 6.dp, vertical = 8.dp)) {
                Text("GIFT IDEAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.muted, modifier = Modifier.padding(start = 8.dp))
                u.person.gifts.forEachIndexed { i, gift ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.toggleGift(u.person, i) }) {
                        Checkbox(
                            checked = gift.done,
                            onCheckedChange = { vm.toggleGift(u.person, i) },
                            colors = CheckboxDefaults.colors(checkedColor = palette.birthdayStrong),
                        )
                        Text(gift.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onWish,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.ground),
        ) { Text("Send a wish", fontWeight = FontWeight.Bold) }
    }
}

/** WhatsApp straight to their number if we have it, otherwise the share sheet. */
private fun sendWish(context: Context, u: Upcoming) {
    val text = Occasions.wish(u)
    val digits = u.person.phone?.filter { it.isDigit() }?.let { if (it.length == 10) "91$it" else it }
    if (!digits.isNullOrEmpty()) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=${Uri.encode(text)}")))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Send a wish"))
}

// ---------------- Reminders ----------------

@Composable
fun RemindersScreen(state: UiState, vm: AppViewModel, onEdit: (EditType, Long?) -> Unit) {
    var showDone by rememberSaveable { mutableStateOf(false) }
    val open = state.reminders.filter { it.reminder.doneAt == null }.sortedBy { Reminders.ringAt(it.reminder) }
    val done = state.reminders.filter { it.reminder.doneAt != null }.sortedByDescending { it.reminder.doneAt }
    val today = state.today
    val todayList = open.filter { Reminders.ringAt(it.reminder)?.toLocalDate()?.isAfter(today) == false }
    val tomorrowList = open.filter { it !in todayList && Reminders.ringAt(it.reminder)?.toLocalDate() == today.plusDays(1) }
    val later = open.filter { it !in todayList && it !in tomorrowList }
    val next = todayList.firstOrNull()?.reminder?.let(Reminders::ringAt)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)) {
        item {
            ScreenHeader(
                "Reminders",
                if (open.isEmpty()) null
                else "${todayList.size} today" + (next?.let { " · next at ${Reminders.timeLabel(it.hour * 60 + it.minute)}" } ?: ""),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                AreaChip("Upcoming", !showDone) { showDone = false }
                AreaChip("Done", showDone) { showDone = true }
            }
        }
        if (showDone) {
            if (done.isEmpty()) item { EmptyNote("Finished one-off reminders show up here.") }
            items(done, key = { it.reminder.id }) {
                ReminderRow(it, today, vm, onEdit)
                SmallGap()
            }
        } else {
            if (state.loaded && open.isEmpty()) item { EmptyNote("No reminders. Tap ＋ to add one — bills, renewals, medicines.") }
            listOf("Today" to todayList, "Tomorrow" to tomorrowList, "Later" to later).forEach { (label, list) ->
                if (list.isNotEmpty()) {
                    item(key = "h$label") { SectionLabel(label) }
                    items(list, key = { it.reminder.id }) {
                        ReminderRow(it, today, vm, onEdit, expanded = label == "Today" && it == todayList.first())
                        SmallGap()
                    }
                }
            }
        }
    }
}
