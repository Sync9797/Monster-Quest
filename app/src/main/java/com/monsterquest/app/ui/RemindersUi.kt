package com.monsterquest.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Reminder
import com.monsterquest.core.Reminders

/**
 * Daily reminders on the Quests screen: each with its time, days and streak.
 * One that has rung today shows Done and Skip; the rest have an on/off switch.
 */
@Composable
fun RemindersSection(vm: GameViewModel, now: Long) {
    val reminders = vm.state.reminders
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Reminders" + if (reminders.isNotEmpty()) " (${reminders.size})" else "")
            Spacer(Modifier.weight(1f))
            SmallButton("+ Reminder", {
                // Reminders arrive as notifications: ask once.
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                adding = true
            }, outlined = true)
        }
        if (reminders.isEmpty()) {
            Text(
                "Gentle daily alarms, like \"6:00, make the bed, every day but Saturday\". Mark them done on time for a small reward and a streak.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        reminders.sortedBy { it.hour * 60 + it.minute }.forEach { r ->
            val open = Reminders.isOpen(r, now)
            val streak = Reminders.streak(r, now)
            GameCard(Modifier.padding(vertical = 4.dp).clickable { editing = r }, border = if (open) HpYellow else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${r.timeLabel} • ${r.daysLabel}",
                            fontSize = 12.sp,
                            color = if (r.enabled) MpBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (streak > 0 || r.bestStreak > 0) {
                            Text(
                                "Streak $streak day${if (streak == 1) "" else "s"}" + if (r.bestStreak > streak) " • best ${r.bestStreak}" else "",
                                fontSize = 12.sp,
                                color = Gold,
                            )
                        }
                    }
                    if (open) {
                        TextButton(onClick = { vm.skipReminder(r.id) }) { Text("Skip") }
                        SmallButton("Done", { vm.completeReminder(r.id) })
                    } else {
                        Switch(checked = r.enabled, onCheckedChange = { vm.setReminderEnabled(r.id, it) })
                    }
                }
            }
        }
    }
    if (adding) {
        ReminderEditor(null, onDismiss = { adding = false }, onDelete = null) { t, h, m, d ->
            vm.addReminder(t, h, m, d)
            adding = false
        }
    }
    editing?.let { r ->
        ReminderEditor(r, onDismiss = { editing = null }, onDelete = { vm.deleteReminder(r.id); editing = null }) { t, h, m, d ->
            vm.editReminder(r.id, t, h, m, d)
            editing = null
        }
    }
}

/** Name, time and days for a reminder. */
@Composable
private fun ReminderEditor(
    r: Reminder?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Int, Int, Set<Int>) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(r?.title ?: "") }
    var hour by remember { mutableStateOf(r?.hour ?: 7) }
    var minute by remember { mutableStateOf(r?.minute ?: 0) }
    var days by remember { mutableStateOf(r?.days ?: (1..7).toSet()) }
    val preview = Reminder("", title, hour, minute, days)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (r == null) "New reminder" else "Edit reminder") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it.take(60) }, label = { Text("What to do") }, singleLine = true)
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = {
                    android.app.TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Time: ${preview.timeLabel}") }
                Spacer(Modifier.padding(4.dp))
                // Days of the week, Monday first.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
                        val d = i + 1
                        val on = d in days
                        SmallButton(label, { days = if (on) days - d else days + d }, Modifier.weight(1f), outlined = !on)
                    }
                }
                Text(preview.daysLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Mark it done within 3 hours of its time for +${Reminders.XP} XP and +${Reminders.GOLD} gold. A 7-day streak gives a Monster Treat; " +
                        "every 30 days, a Restore Potion and ${Reminders.MONTH_GOLD} gold. Skipping a busy day doesn't break the streak.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.padding(top = 4.dp)) { Text("Delete reminder", color = HpRed) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, hour, minute, days) }, enabled = title.isNotBlank() && days.isNotEmpty()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
