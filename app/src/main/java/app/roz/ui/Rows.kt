@file:OptIn(ExperimentalFoundationApi::class)

package app.roz.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.roz.core.ChoreSchedule
import app.roz.core.ChoreToday
import app.roz.core.Reminders
import app.roz.core.RepeatText
import app.roz.core.dateLabel
import app.roz.data.DeletedItem
import app.roz.data.StoredReminder
import java.time.LocalDate

/** A chore: tick to do it, tap to edit, long-press for skip / pause / delete. */
@Composable
fun ChoreRow(item: ChoreToday, today: LocalDate, vm: AppViewModel, onEdit: (EditType, Long?) -> Unit, showKind: Boolean = false) {
    val chore = item.chore
    var menu by remember { mutableStateOf(false) }
    Box {
        ItemCard(
            border = if (item.status.isOverdue && !chore.paused) palette.lateLine else null,
            modifier = Modifier.combinedClickable(onClick = { onEdit(EditType.CHORE, chore.id) }, onLongClick = { menu = true }),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(palette.chore, false, "Mark ${chore.name} done") { vm.completeChore(chore) }
                TitleAndMeta(chore.name) {
                    if (showKind) KindChip("Chore", palette.choreSoft, palette.chore)
                    Icon(Icons.Outlined.Repeat, null, tint = palette.muted, modifier = Modifier.size(13.dp))
                    MetaText(RepeatText.describe(chore.rule))
                }
                val label = if (chore.paused) "Paused" else ChoreSchedule.dueLabel(item.status, today)
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.status.isOverdue && !chore.paused) palette.late else palette.ink,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Skip this time") }, onClick = { menu = false; vm.skipChore(chore) })
            DropdownMenuItem(text = { Text(if (chore.paused) "Resume" else "Pause") }, onClick = { menu = false; vm.pauseChore(chore, !chore.paused) })
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(EditType.CHORE, chore.id) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.delete(DeletedItem.Type.CHORE, chore.id, chore.name) })
        }
    }
}

/** A reminder: tick when done; [expanded] shows Done / In 1 hour / Tomorrow buttons. */
@Composable
fun ReminderRow(
    stored: StoredReminder,
    today: LocalDate,
    vm: AppViewModel,
    onEdit: (EditType, Long?) -> Unit,
    expanded: Boolean = false,
    showKind: Boolean = false,
) {
    val r = stored.reminder
    var menu by remember { mutableStateOf(false) }
    val ringAt = Reminders.ringAt(r)
    val overdue = ringAt != null && ringAt.toLocalDate().isBefore(today)
    Box {
        ItemCard(
            border = if (expanded) palette.lateLine else null,
            modifier = Modifier.combinedClickable(onClick = { onEdit(EditType.REMINDER, r.id) }, onLongClick = { menu = true }),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckCircle(palette.reminder, r.doneAt != null, "Mark ${r.title} done", round = false) {
                    if (r.doneAt == null) vm.completeReminder(stored)
                }
                TitleAndMeta(r.title) {
                    if (showKind) KindChip("Reminder", palette.reminderSoft, palette.reminder)
                    MetaText(r.rule?.let { RepeatText.describe(it) } ?: "Once")
                }
                val whenText = when {
                    ringAt == null -> "Done"
                    ringAt.toLocalDate() == today -> Reminders.timeLabel(ringAt.hour * 60 + ringAt.minute)
                    overdue -> dateLabel(ringAt.toLocalDate(), today) + " · late"
                    else -> dateLabel(ringAt.toLocalDate(), today)
                }
                Text(whenText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (overdue) palette.late else palette.ink)
            }
            if (expanded && r.doneAt == null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.completeReminder(stored) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.ink, contentColor = palette.ground),
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                    OutlinedButton(onClick = { vm.snoozeReminder(stored, tomorrow = false) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("In 1 hour", color = palette.ink, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(onClick = { vm.snoozeReminder(stored, tomorrow = true) }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Tomorrow", color = palette.ink, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (r.doneAt == null) {
                DropdownMenuItem(text = { Text("In 1 hour") }, onClick = { menu = false; vm.snoozeReminder(stored, tomorrow = false) })
                DropdownMenuItem(text = { Text("Tomorrow") }, onClick = { menu = false; vm.snoozeReminder(stored, tomorrow = true) })
            }
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit(EditType.REMINDER, r.id) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.delete(DeletedItem.Type.REMINDER, r.id, r.title) })
        }
    }
}

@Composable
fun SmallGap() = Spacer(Modifier.height(8.dp).padding(0.dp))
