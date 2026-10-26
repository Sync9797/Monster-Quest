package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.IconText
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Difficulty
import com.monsterquest.core.QuestTask
import kotlinx.coroutines.delay
import com.monsterquest.core.Priority
import com.monsterquest.core.Planner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import android.app.TimePickerDialog
import android.app.DatePickerDialog
import com.monsterquest.core.TaskSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QuestsScreen(vm: GameViewModel) {
    val context = LocalContext.current
    val state = vm.state
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<QuestTask?>(null) }
    // Tick so "due in" times and the within-the-hour highlight stay current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var showDone by remember { mutableStateOf(false) }

    // Events from the phone's calendar become quests automatically.
    var hasCalendar by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        if (granted) vm.syncCalendar() else vm.toast("Without calendar access you can still add to-dos by hand.")
    }
    LaunchedEffect(hasCalendar) {
        if (hasCalendar && state.settings.calendarAutoSync) vm.syncCalendar(quiet = true)
    }

    val pending = state.tasks.filter { !it.completed }
    val sections = Planner.sections(state.tasks, now)
    val dueSoon = pending.filter { Planner.isDueSoon(it, now) }
    val done = state.tasks.filter { it.completed }.sortedByDescending { it.completedAtMs }

    // One column on phones; more side by side when unfolded.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Grid items stack their children on top of each other, so wrap in a Column.
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Left: a to-do that isn't on the calendar. Right: open the calendar app.
                    SmallButton("+ To-do", { showAdd = true }, Modifier.weight(1f))
                    SmallButton(
                        "Calendar",
                        { if (!openCalendar(context)) vm.toast("No calendar app found.") },
                        Modifier.weight(1f),
                        outlined = true,
                    )
                }
                // Anything due within the hour is the first thing you see.
                if (dueSoon.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    DueSoonBanner(dueSoon, now)
                }
                if (!hasCalendar) {
                    Spacer(Modifier.height(10.dp))
                    GameCard(border = MpBlue) {
                        IconText(GameIcon.CALENDAR, "Sync your calendar", tint = MpBlue, bold = true)
                        Text(
                            "Events from your phone's calendar become quests automatically.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        SmallButton("Connect calendar", { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth())
                    }
                } else if (vm.syncingCalendar) {
                    Text("Syncing calendar…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                WeeklyCard(vm)
                Spacer(Modifier.height(10.dp))
                DashboardCard(vm)
                Spacer(Modifier.height(10.dp))
                FocusCard(vm)
                SectionTitle("Active quests (${pending.size})")
            }
        }
        if (pending.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No quests yet. Add a to-do, or sync your calendar so events become quests.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        sections.forEach { sec ->
            item(key = "sec_${sec.group}", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeader(sec.group, sec.tasks.size)
            }
            items(sec.tasks, key = { it.id }) { task ->
                QuestRow(
                    task, now,
                    onClick = { editing = task },
                    onComplete = { vm.completeTask(task.id) },
                    onDelete = { vm.deleteTask(task.id) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Completed (${done.size})")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "Hide" else "Show") }
            }
        }
        if (showDone) {
            items(done.take(50), key = { "done_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { task ->
                Text(
                    task.title,
                    modifier = Modifier.padding(vertical = 4.dp),
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAdd) {
        QuestEditor(null, onDismiss = { showAdd = false }) { t, d, due, p -> vm.addTask(t, d, due, p); showAdd = false }
    }
    editing?.let { task ->
        QuestEditor(task, onDismiss = { editing = null }) { t, d, due, p -> vm.editTask(task.id, t, d, due, p); editing = null }
    }
}

private val timeFmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun whenText(start: Long): String {
    val today = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    val sameDay = today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val nextDay = tomorrow.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        tomorrow.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(start))
    return when {
        sameDay -> "today $clock"
        nextDay -> "tomorrow $clock"
        else -> timeFmt.format(Date(start))
    }
}

private fun Priority.color(): Color = when (this) {
    Priority.HIGH -> HpRed
    Priority.NORMAL -> Gold
    Priority.LOW -> Color(0xFF7D8A87)
}

private fun groupColor(g: Planner.Group): Color? = when (g) {
    Planner.Group.OVERDUE -> HpRed
    Planner.Group.SOON, Planner.Group.HAPPENING -> HpYellow
    else -> null
}

@Composable
private fun GroupHeader(g: Planner.Group, count: Int) {
    val color = groupColor(g) ?: Gold
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (g == Planner.Group.SOON || g == Planner.Group.OVERDUE) {
            GIcon(GameIcon.HOURGLASS, Modifier.size(16.dp), color)
            Spacer(Modifier.width(6.dp))
        }
        Text(g.label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("  $count", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

/** Top-of-list reminder for anything due within the hour. */
@Composable
private fun DueSoonBanner(tasks: List<QuestTask>, now: Long) {
    GameCard(border = HpYellow) {
        IconText(
            GameIcon.HOURGLASS,
            if (tasks.size == 1) "1 quest due within the hour" else "${tasks.size} quests due within the hour",
            tint = HpYellow, textColor = HpYellow, bold = true,
        )
        tasks.sortedBy { it.dueAt }.take(4).forEach { t ->
            Text("• ${t.title} (${Planner.relative(t.dueAt ?: now, now)})", fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun dueText(task: QuestTask, now: Long): String? {
    val due = task.dueAt ?: return null
    val label = whenText(due)
    return when {
        Planner.isOverdue(task, now) -> "Overdue • was due $label"
        task.source == TaskSource.CALENDAR && due <= now -> "Happening now • ends ${task.endMs?.let { whenText(it) } ?: ""}".trim()
        else -> (if (task.source == TaskSource.CALENDAR) "Starts $label" else "Due $label") + " • " + Planner.relative(due, now)
    }
}

@Composable
private fun QuestRow(task: QuestTask, now: Long, onClick: () -> Unit, onComplete: () -> Unit, onDelete: () -> Unit) {
    val soon = Planner.isDueSoon(task, now) || (task.source == TaskSource.CALENDAR && Planner.groupOf(task, now) == Planner.Group.HAPPENING)
    val overdue = Planner.isOverdue(task, now)
    // Due within the hour: an amber card that gently pulses. Overdue: red.
    val pulse by rememberInfiniteTransition(label = "due").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "duePulse",
    )
    val border = when {
        overdue -> HpRed
        soon -> HpYellow.copy(alpha = pulse)
        else -> null
    }
    GameCard(Modifier.padding(vertical = 4.dp).clickable(onClick = onClick), border = border) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Priority stripe.
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(task.priority.color()))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.priority == Priority.HIGH) {
                        Text("!", color = HpRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(task.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                dueText(task, now)?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        fontWeight = if (soon || overdue) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            overdue -> HpRed
                            soon -> HpYellow
                            else -> MpBlue
                        },
                    )
                }
                val sub = buildList {
                    if (task.source == TaskSource.CALENDAR) add(task.calendarName ?: "Calendar")
                    if (task.priority != Priority.NORMAL) add("${task.priority.label} priority")
                    add(task.difficulty.label)
                    add("+${task.difficulty.xp} XP")
                    add("+${task.difficulty.gold} gold")
                }.joinToString(" • ")
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.source == TaskSource.MANUAL) {
                TextButton(onClick = onDelete, contentPadding = PaddingValues(4.dp)) {
                    GIcon(GameIcon.CLOSE, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SmallButton("Done", onComplete)
        }
    }
}

/** Quick due-date choices: in an hour, this evening, tomorrow morning. */
private fun quickDue(kind: Int, now: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    when (kind) {
        0 -> return now + 3_600_000L
        1 -> {
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.HOUR_OF_DAY, if (c.get(Calendar.HOUR_OF_DAY) < 17) 18 else 21)
        }
        else -> {
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 9)
            c.set(Calendar.MINUTE, 0)
        }
    }
    return c.timeInMillis
}

/** Add a to-do, or edit one: name, priority, difficulty and due date. Calendar quests can only change priority. */
@Composable
private fun QuestEditor(
    task: QuestTask?,
    onDismiss: () -> Unit,
    onSave: (String, Difficulty, Long?, Priority) -> Unit,
) {
    val context = LocalContext.current
    val calendar = task?.source == TaskSource.CALENDAR
    var title by remember { mutableStateOf(task?.title ?: "") }
    var difficulty by remember { mutableStateOf(task?.difficulty ?: Difficulty.NORMAL) }
    var priority by remember { mutableStateOf(task?.priority ?: Priority.NORMAL) }
    var due by remember { mutableStateOf(task?.dueMs) }
    val now = System.currentTimeMillis()
    fun pickDateTime() {
        val c = Calendar.getInstance().apply { timeInMillis = due ?: quickDue(1, now) }
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, hh, mm ->
                due = Calendar.getInstance().apply { set(y, m, d, hh, mm, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "New to-do" else if (calendar) "Calendar quest" else "Edit to-do") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (calendar) {
                    Text(task!!.title, fontWeight = FontWeight.SemiBold)
                    task.dueAt?.let { Text("Starts ${whenText(it)}", fontSize = 13.sp, color = MpBlue) }
                    Text("Time and name come from your calendar; you can set its priority.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What do you need to do?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Priority", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Priority.entries.reversed().forEach { p ->
                        SmallButton(p.label, { priority = p }, Modifier.weight(1f), outlined = p != priority)
                    }
                }
                if (!calendar) {
                    Text("Due", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Text(
                        due?.let { "${whenText(it)} (${Planner.relative(it, now)})" } ?: "No due date",
                        color = if (due != null) MpBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallButton("1 hour", { due = quickDue(0, now) }, Modifier.weight(1f), outlined = true)
                        SmallButton(if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 17) "6 PM" else "9 PM", { due = quickDue(1, now) }, Modifier.weight(1f), outlined = true)
                        SmallButton("Tmrw 9 AM", { due = quickDue(2, now) }, Modifier.weight(1f), outlined = true)
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SmallButton("Pick date & time", ::pickDateTime, Modifier.weight(2f), outlined = true)
                        SmallButton("None", { due = null }, Modifier.weight(1f), outlined = true)
                    }
                    Text("Difficulty", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Difficulty.entries.forEach { d ->
                            SmallButton(d.label, { difficulty = d }, Modifier.weight(1f), outlined = d != difficulty)
                        }
                    }
                    Text(
                        "Reward: +${difficulty.xp} XP, +${difficulty.gold} gold, +1 hunt token",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, difficulty, due, priority) }, enabled = calendar || title.isNotBlank()) {
                Text(if (task == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opens the phone's default calendar app at today. Returns false if there isn't one. */
fun openCalendar(context: Context): Boolean {
    val today = CalendarContract.CONTENT_URI.buildUpon()
        .appendPath("time")
        .also { ContentUris.appendId(it, System.currentTimeMillis()) }
        .build()
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, today),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            // Try the next way of opening a calendar.
        }
    }
    return false
}
