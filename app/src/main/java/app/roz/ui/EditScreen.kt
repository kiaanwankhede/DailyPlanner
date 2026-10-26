package app.roz.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.core.Chore
import app.roz.core.ChoreSchedule
import app.roz.core.FocusType
import app.roz.core.GiftIdea
import app.roz.core.Habit
import app.roz.core.HabitKind
import app.roz.core.OccasionKind
import app.roz.core.Person
import app.roz.core.Reminder
import app.roz.core.Reminders
import app.roz.core.Repeat
import app.roz.core.RepeatText
import app.roz.core.dateLabel
import app.roz.data.DeletedItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EditScreen(state: UiState, vm: AppViewModel, initialType: EditType, id: Long?, onDone: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(initialType) }
    Column(Modifier.fillMaxSize().background(palette.card).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (id == null) "New" else "Edit ${type.label.lowercase()}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink, modifier = Modifier.weight(1f))
            IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = palette.ink) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (id == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditType.entries.forEach { t ->
                        Pill(t.label, type == t, Modifier.weight(1f), accent = accentFor(t)) { type = t }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            when (type) {
                EditType.HABIT -> HabitForm(state, vm, state.habits.firstOrNull { it.habit.id == id }?.habit, onDone)
                EditType.CHORE -> ChoreForm(state, vm, state.chores.firstOrNull { it.chore.id == id }?.chore, onDone)
                EditType.REMINDER -> ReminderForm(state, vm, state.reminders.firstOrNull { it.reminder.id == id }?.reminder, onDone)
                EditType.BIRTHDAY -> PersonForm(state, vm, state.upcoming.firstOrNull { it.person.id == id }?.person, onDone)
            }
        }
    }
}

@Composable
private fun accentFor(t: EditType) = when (t) {
    EditType.HABIT -> palette.habit
    EditType.CHORE -> palette.chore
    EditType.REMINDER -> palette.reminder
    EditType.BIRTHDAY -> palette.birthdayStrong
}

// ---------------- Habit ----------------

@Composable
private fun ColumnScope.HabitForm(state: UiState, vm: AppViewModel, existing: Habit?, onDone: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: HabitKind.YES_NO) }
    var target by rememberSaveable { mutableStateOf(existing?.takeIf { it.kind != HabitKind.YES_NO }?.target ?: 8) }
    var weekly by rememberSaveable { mutableStateOf(existing?.perWeek != null) }
    var perWeek by rememberSaveable { mutableStateOf(existing?.perWeek ?: 4) }
    var reminder by rememberSaveable { mutableStateOf(existing?.reminderMinute) }
    var priority by rememberSaveable { mutableStateOf(existing != null && state.focusPick?.let { it.type == FocusType.HABIT && it.id == existing.id } == true) }

    NameField(name, "Name, e.g. Walk 8,000 steps") { name = it }
    FieldLabel("Kind")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Yes / no", kind == HabitKind.YES_NO, Modifier.weight(1f)) { kind = HabitKind.YES_NO }
        Pill("At least", kind == HabitKind.AT_LEAST, Modifier.weight(1f)) { kind = HabitKind.AT_LEAST }
        Pill("At most", kind == HabitKind.AT_MOST, Modifier.weight(1f)) { kind = HabitKind.AT_MOST; weekly = false }
    }
    if (kind != HabitKind.YES_NO) {
        Stepper(if (kind == HabitKind.AT_LEAST) "At least, per day" else "At most, per day", target, 1..99) { target = it }
    }
    if (kind != HabitKind.AT_MOST) {
        FieldLabel("How often")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Every day", !weekly, Modifier.weight(1f)) { weekly = false }
            Pill("Some days a week", weekly, Modifier.weight(1f)) { weekly = true }
        }
        if (weekly) Stepper("Days a week", perWeek, 1..6) { perWeek = it }
    }
    ToggleRow("Remind me", reminder?.let { "Every day at ${Reminders.timeLabel(it)}" } ?: "Off", reminder != null) { on ->
        if (on) {
            pickTime(context, reminder ?: (8 * 60)) { reminder = it }
        } else {
            reminder = null
        }
    }
    if (reminder != null) {
        TextButtonRow("Change time") { pickTime(context, reminder ?: (8 * 60)) { reminder = it } }
    }
    if (kind != HabitKind.AT_MOST) PriorityToggle(state, FocusType.HABIT, existing?.id, priority) { priority = it }
    Hint("Missing one day a week is forgiven automatically. Strength rises with every done day and drops a little with a miss.")
    SaveAndDelete(
        canSave = name.isNotBlank(),
        label = "Save habit",
        onSave = {
            vm.saveHabit(
                Habit(
                    id = existing?.id ?: 0,
                    name = name.trim(),
                    kind = kind,
                    target = if (kind == HabitKind.YES_NO) 1 else target,
                    perWeek = if (weekly && kind != HabitKind.AT_MOST) perWeek else null,
                    createdOn = existing?.createdOn ?: state.today,
                    reminderMinute = reminder,
                ),
                priority = priority && kind != HabitKind.AT_MOST,
            )
            onDone()
        },
        onDelete = existing?.let { { vm.delete(DeletedItem.Type.HABIT, it.id, it.name); onDone() } },
    )
}

// ---------------- Chore ----------------

private enum class ScheduleKind(val label: String) {
    WEEKLY("Weekly"), MONTH_DATE("Monthly on a date"), MONTH_WEEKDAY("Monthly on a day"),
    MONTH_END("Month end"), YEARLY("Yearly"), EVERY_DAYS("Every N days"),
}

private val DEFAULT_AREAS = listOf("Home", "Kitchen", "Bathroom", "Plants", "Car")
private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
private fun ColumnScope.ChoreForm(state: UiState, vm: AppViewModel, existing: Chore?, onDone: () -> Unit) {
    val context = LocalContext.current
    val today = state.today
    val rule = existing?.rule
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var area by rememberSaveable { mutableStateOf(existing?.area ?: "") }
    var afterDone by rememberSaveable { mutableStateOf(rule is Repeat.AfterDone || existing == null) }
    var scheduleKind by rememberSaveable {
        mutableStateOf(
            when (rule) {
                is Repeat.MonthlyOnDate -> ScheduleKind.MONTH_DATE
                is Repeat.MonthlyOnWeekday -> ScheduleKind.MONTH_WEEKDAY
                is Repeat.MonthlyLastDay -> ScheduleKind.MONTH_END
                is Repeat.Yearly -> ScheduleKind.YEARLY
                is Repeat.Daily -> ScheduleKind.EVERY_DAYS
                else -> ScheduleKind.WEEKLY
            },
        )
    }
    val weekDays = remember { mutableStateListOf<Int>().apply { addAll((rule as? Repeat.Weekly)?.days ?: listOf(today.dayOfWeek.value)) } }
    var everyWeeks by rememberSaveable { mutableStateOf((rule as? Repeat.Weekly)?.everyWeeks ?: 1) }
    var monthDay by rememberSaveable { mutableStateOf((rule as? Repeat.MonthlyOnDate)?.day ?: today.dayOfMonth) }
    var everyMonths by rememberSaveable {
        mutableStateOf(
            (rule as? Repeat.MonthlyOnDate)?.everyMonths ?: (rule as? Repeat.MonthlyOnWeekday)?.everyMonths
                ?: (rule as? Repeat.MonthlyLastDay)?.everyMonths ?: 1,
        )
    }
    var weekOrdinal by rememberSaveable { mutableStateOf((rule as? Repeat.MonthlyOnWeekday)?.week ?: 1) }
    var weekday by rememberSaveable { mutableStateOf((rule as? Repeat.MonthlyOnWeekday)?.dayOfWeek ?: 6) }
    var yearlyDate by rememberSaveable { mutableStateOf((rule as? Repeat.Yearly)?.let { LocalDate.of(2000, it.month, it.day) } ?: today) }
    var everyDays by rememberSaveable { mutableStateOf((rule as? Repeat.Daily)?.everyDays ?: 2) }
    var amount by rememberSaveable { mutableStateOf((rule as? Repeat.AfterDone)?.amount ?: 3) }
    var unit by rememberSaveable { mutableStateOf((rule as? Repeat.AfterDone)?.unit ?: Repeat.Unit.DAYS) }
    var lastDone by rememberSaveable { mutableStateOf(existing?.lastDone) }
    var inSummary by rememberSaveable { mutableStateOf(existing?.inMorningSummary ?: true) }
    var priority by rememberSaveable { mutableStateOf(existing != null && state.focusPick?.let { it.type == FocusType.CHORE && it.id == existing.id } == true) }

    val built: Repeat? = if (afterDone) Repeat.AfterDone(amount, unit) else when (scheduleKind) {
        ScheduleKind.WEEKLY -> if (weekDays.isEmpty()) null else Repeat.Weekly(weekDays.sorted(), everyWeeks, today)
        ScheduleKind.MONTH_DATE -> Repeat.MonthlyOnDate(monthDay, everyMonths, today)
        ScheduleKind.MONTH_WEEKDAY -> Repeat.MonthlyOnWeekday(weekOrdinal, weekday, everyMonths, today)
        ScheduleKind.MONTH_END -> Repeat.MonthlyLastDay(everyMonths, today)
        ScheduleKind.YEARLY -> Repeat.Yearly(yearlyDate.monthValue, yearlyDate.dayOfMonth)
        ScheduleKind.EVERY_DAYS -> Repeat.Daily(everyDays, today)
    }
    // Keep the stored due date unless the rule itself changed.
    val sameRule = existing != null && built != null && sameShape(existing.rule, built)
    val due = when {
        built == null -> null
        sameRule -> existing!!.due
        else -> ChoreSchedule.firstDue(built, today, lastDone)
    }

    NameField(name, "Name, e.g. Clean bathroom") { name = it }
    FieldLabel("Area")
    OutlinedTextField(area, { area = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Home, Kitchen, Car…") })
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (state.areas + DEFAULT_AREAS).distinct().forEach { a -> Pill(a, area == a) { area = a } }
    }
    FieldLabel("How does it repeat?")
    OptionCard("On a schedule", "e.g. every Sunday, or the 1st of each month", !afterDone) { afterDone = false }
    Spacer(Modifier.height(8.dp))
    OptionCard("After I do it", "Next date counts from when you last did it", afterDone) { afterDone = true }

    if (afterDone) {
        Stepper("Repeat after", amount, 1..365) { amount = it }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Repeat.Unit.entries.forEach { u -> Pill(u.name.lowercase(), unit == u, Modifier.weight(1f)) { unit = u } }
        }
        TextButtonRow("Last done: ${lastDone?.let { dateLabel(it, today) } ?: "not yet"}") {
            pickDate(context, lastDone ?: today) { lastDone = it }
        }
    } else {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScheduleKind.entries.forEach { k -> Pill(k.label, scheduleKind == k) { scheduleKind = k } }
        }
        when (scheduleKind) {
            ScheduleKind.WEEKLY -> {
                FieldLabel("On")
                DayToggles(weekDays)
                Stepper("Every … weeks", everyWeeks, 1..12) { everyWeeks = it }
            }
            ScheduleKind.MONTH_DATE -> {
                Stepper("Day of the month", monthDay, 1..31) { monthDay = it }
                if (monthDay > 28) Hint("In shorter months it falls on the last day.")
                Stepper("Every … months", everyMonths, 1..12) { everyMonths = it }
            }
            ScheduleKind.MONTH_WEEKDAY -> {
                FieldLabel("Which")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(1 to "First", 2 to "Second", 3 to "Third", 4 to "Fourth", -1 to "Last").forEach { (n, l) ->
                        Pill(l, weekOrdinal == n) { weekOrdinal = n }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { i, l -> Pill(l, weekday == i + 1, Modifier.weight(1f)) { weekday = i + 1 } }
                }
                Stepper("Every … months", everyMonths, 1..12) { everyMonths = it }
            }
            ScheduleKind.MONTH_END -> Stepper("Every … months", everyMonths, 1..12) { everyMonths = it }
            ScheduleKind.YEARLY -> TextButtonRow("On ${yearlyDate.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH))}") {
                pickDate(context, yearlyDate) { yearlyDate = it }
            }
            ScheduleKind.EVERY_DAYS -> Stepper("Every … days", everyDays, 2..365) { everyDays = it }
        }
    }
    ToggleRow("Show in morning summary", "On the day it's due", inSummary) { inSummary = it }
    PriorityToggle(state, FocusType.CHORE, existing?.id, priority) { priority = it }
    if (built != null && due != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            "${RepeatText.describe(built)}. Next due: ${dateLabel(due, today).lowercase().let { if (due.isBefore(today)) "overdue" else it }}.",
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.choreSoft).padding(14.dp),
            color = palette.chore, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        )
    }
    SaveAndDelete(
        canSave = name.isNotBlank() && built != null,
        label = "Save chore",
        onSave = {
            vm.saveChore(
                Chore(
                    id = existing?.id ?: 0,
                    name = name.trim(),
                    area = area.trim(),
                    rule = keepAnchor(existing?.rule, built!!),
                    due = due!!,
                    lastDone = lastDone,
                    paused = existing?.paused ?: false,
                    inMorningSummary = inSummary,
                ),
                priority = priority,
            )
            onDone()
        },
        onDelete = existing?.let { { vm.delete(DeletedItem.Type.CHORE, it.id, it.name); onDone() } },
    )
}

/** True when two rules repeat the same way (ignoring the anchor date, which is just "when it was set up"). */
private fun sameShape(a: Repeat, b: Repeat): Boolean = when {
    a is Repeat.Weekly && b is Repeat.Weekly -> a.days.sorted() == b.days.sorted() && a.everyWeeks == b.everyWeeks
    a is Repeat.MonthlyOnDate && b is Repeat.MonthlyOnDate -> a.day == b.day && a.everyMonths == b.everyMonths
    a is Repeat.MonthlyOnWeekday && b is Repeat.MonthlyOnWeekday -> a.copy(anchor = b.anchor) == b
    a is Repeat.MonthlyLastDay && b is Repeat.MonthlyLastDay -> a.everyMonths == b.everyMonths
    a is Repeat.Daily && b is Repeat.Daily -> a.everyDays == b.everyDays
    else -> a == b
}

/** Puts the rule's anchor back to the original one when only the anchor differs. */
private fun keepAnchor(old: Repeat?, new: Repeat): Repeat = if (old != null && sameShape(old, new)) old else new

// ---------------- Reminder ----------------

private enum class ReminderRepeat(val label: String) { ONCE("Once"), DAILY("Every day"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

@Composable
private fun ColumnScope.ReminderForm(state: UiState, vm: AppViewModel, existing: Reminder?, onDone: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: state.today) }
    var minute by rememberSaveable { mutableStateOf(existing?.minute ?: (9 * 60)) }
    var repeat by rememberSaveable {
        mutableStateOf(
            when (existing?.rule) {
                null -> ReminderRepeat.ONCE
                is Repeat.Daily -> ReminderRepeat.DAILY
                is Repeat.Weekly -> ReminderRepeat.WEEKLY
                is Repeat.Yearly -> ReminderRepeat.YEARLY
                else -> ReminderRepeat.MONTHLY
            },
        )
    }
    val weekDays = remember { mutableStateListOf<Int>().apply { addAll((existing?.rule as? Repeat.Weekly)?.days ?: listOf(date.dayOfWeek.value)) } }
    var priority by rememberSaveable { mutableStateOf(existing != null && state.focusPick?.let { it.type == FocusType.REMINDER && it.id == existing.id } == true) }

    NameField(title, "What to remember, e.g. Pay electricity bill") { title = it }
    FieldLabel("When")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.weight(1f).height(48.dp)) {
            Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), color = palette.ink, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { pickTime(context, minute) { minute = it } }, modifier = Modifier.weight(1f).height(48.dp)) {
            Text(Reminders.timeLabel(minute), color = palette.ink, fontWeight = FontWeight.Bold)
        }
    }
    FieldLabel("Repeat")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReminderRepeat.entries.forEach { r -> Pill(r.label, repeat == r) { repeat = r } }
    }
    if (repeat == ReminderRepeat.WEEKLY) {
        Spacer(Modifier.height(10.dp))
        DayToggles(weekDays)
    }
    val rule: Repeat.Fixed? = when (repeat) {
        ReminderRepeat.ONCE -> null
        ReminderRepeat.DAILY -> Repeat.Daily(1, date)
        ReminderRepeat.WEEKLY -> if (weekDays.isEmpty()) null else Repeat.Weekly(weekDays.sorted(), 1, date)
        ReminderRepeat.MONTHLY -> Repeat.MonthlyOnDate(date.dayOfMonth, 1, date)
        ReminderRepeat.YEARLY -> Repeat.Yearly(date.monthValue, date.dayOfMonth)
    }
    PriorityToggle(state, FocusType.REMINDER, existing?.id, priority) { priority = it }
    if (rule != null) Hint(RepeatText.describe(rule) + " at " + Reminders.timeLabel(minute))
    SaveAndDelete(
        canSave = title.isNotBlank() && !(repeat == ReminderRepeat.WEEKLY && weekDays.isEmpty()),
        label = "Save reminder",
        onSave = {
            vm.saveReminder(
                Reminder(
                    id = existing?.id ?: 0,
                    title = title.trim(),
                    date = Reminders.firstDate(rule, date),
                    minute = minute,
                    rule = rule,
                ),
                priority = priority,
            )
            onDone()
        },
        onDelete = existing?.let { { vm.delete(DeletedItem.Type.REMINDER, it.id, it.title); onDone() } },
    )
}

// ---------------- Birthday ----------------

@Composable
private fun ColumnScope.PersonForm(state: UiState, vm: AppViewModel, existing: Person?, onDone: () -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var relation by rememberSaveable { mutableStateOf(existing?.relation ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: OccasionKind.BIRTHDAY) }
    var month by rememberSaveable { mutableStateOf(existing?.month ?: LocalDate.now().monthValue) }
    var day by rememberSaveable { mutableStateOf(existing?.day ?: LocalDate.now().dayOfMonth) }
    var year by rememberSaveable { mutableStateOf(existing?.year) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    val gifts = remember { mutableStateListOf<GiftIdea>().apply { addAll(existing?.gifts.orEmpty()) } }
    var newGift by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(existing != null && state.focusPick?.let { it.type == FocusType.PERSON && it.id == existing.id } == true) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Birthday", kind == OccasionKind.BIRTHDAY, Modifier.weight(1f)) { kind = OccasionKind.BIRTHDAY }
        Pill("Anniversary", kind == OccasionKind.ANNIVERSARY, Modifier.weight(1f)) { kind = OccasionKind.ANNIVERSARY }
    }
    Spacer(Modifier.height(12.dp))
    NameField(name, if (kind == OccasionKind.BIRTHDAY) "Name" else "Names, e.g. Aai & Baba") { name = it }
    FieldLabel("Relation (optional)")
    OutlinedTextField(relation, { relation = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Niece, college friend…") })
    FieldLabel("Date")
    val shown = LocalDate.of(year ?: 2000, month, minOf(day, java.time.YearMonth.of(year ?: 2000, month).lengthOfMonth()))
    OutlinedButton(onClick = { pickDate(context, shown) { month = it.monthValue; day = it.dayOfMonth; if (year != null) year = it.year } }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(shown.format(DateTimeFormatter.ofPattern(if (year != null) "d MMMM yyyy" else "d MMMM", Locale.ENGLISH)), color = palette.ink, fontWeight = FontWeight.Bold)
    }
    ToggleRow("I know the year", if (kind == OccasionKind.BIRTHDAY) "Shows the age they turn" else "Shows which anniversary it is", year != null) { on ->
        if (on) {
            pickDate(context, LocalDate.of(1990, month, minOf(day, 28))) { month = it.monthValue; day = it.dayOfMonth; year = it.year }
        } else {
            year = null
        }
    }
    FieldLabel("Phone (optional, for WhatsApp wishes)")
    OutlinedTextField(phone, { phone = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    FieldLabel("Gift ideas")
    gifts.forEachIndexed { i, g ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• ${g.text}", modifier = Modifier.weight(1f), color = palette.ink, fontSize = 15.sp)
            IconButton(onClick = { gifts.removeAt(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${g.text}", tint = palette.muted) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newGift, { newGift = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Add an idea") })
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { if (newGift.isNotBlank()) { gifts += GiftIdea(newGift.trim()); newGift = "" } }) { Text("Add", color = palette.ink) }
    }
    PriorityToggle(state, FocusType.PERSON, existing?.id, priority) { priority = it }
    Hint("You'll get an alert 7 days before, 1 day before and on the day.")
    SaveAndDelete(
        canSave = name.isNotBlank(),
        label = "Save",
        onSave = {
            val pending = if (newGift.isNotBlank()) gifts + GiftIdea(newGift.trim()) else gifts.toList()
            vm.savePerson(Person(existing?.id ?: 0, name.trim(), relation.trim(), kind, month, day, year, phone.trim().ifBlank { null }, pending), priority)
            onDone()
        },
        onDelete = existing?.let { { vm.delete(DeletedItem.Type.PERSON, it.id, it.name); onDone() } },
    )
}

// ---------------- pieces ----------------

/** The "Top priority" switch at the end of each form. Only one item can hold it at a time. */
@Composable
private fun PriorityToggle(state: UiState, type: FocusType, id: Long?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val current = state.focus
    val mine = id != null && current != null && current.pick.type == type && current.pick.id == id
    val subtitle = if (checked && current != null && !mine) {
        "Replaces “${current.title}” on Today and your lock screen"
    } else {
        "Shows first on Today and on your lock screen"
    }
    ToggleRow("Top priority", subtitle, checked, onChange)
}

@Composable
private fun NameField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.muted, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = palette.muted, modifier = Modifier.padding(top = 12.dp))
}

@Composable
fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, accent: Color = palette.chore, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else palette.card)
            .border(1.5.dp, if (selected) accent else palette.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = if (selected) "$label, selected" else label },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun OptionCard(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.choreSoft else palette.card)
            .border(2.dp, if (selected) palette.chore else palette.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(if (selected) 6.dp else 2.dp, if (selected) palette.chore else palette.muted, CircleShape))
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.ink)
            Text(hint, fontSize = 12.sp, color = palette.muted)
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.ink, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(palette.ground).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "Less", value > range.first) { onChange(value - 1) }
            Text("$value", modifier = Modifier.width(56.dp), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepButton("+", "More", value < range.last) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(palette.card)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enabled) palette.ink else palette.line) }
}

@Composable
private fun DayToggles(days: MutableList<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DAY_LETTERS.forEachIndexed { i, l ->
            val d = i + 1
            Pill(l, d in days, Modifier.weight(1f)) { if (d in days) days.remove(d) else days.add(d) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
            Text(subtitle, fontSize = 12.sp, color = palette.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextButtonRow(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp)) {
        Text(text, color = palette.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SaveAndDelete(canSave: Boolean, label: String, onSave: () -> Unit, onDelete: (() -> Unit)?) {
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onSave,
        enabled = canSave,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.ground),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
    if (onDelete != null) {
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Delete", color = palette.late, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(Modifier.height(24.dp))
}

fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(context: Context, minute: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, minute / 60, minute % 60, false).show()
}
