package com.monsterquest.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Difficulty
import com.monsterquest.core.QuestTask
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
    var showDone by remember { mutableStateOf(false) }

    val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.syncCalendar() else vm.toast("Without calendar access you can still add quests by hand.")
    }
    // Quietly refresh from the calendar whenever this tab opens.
    LaunchedEffect(hasCalendar) {
        if (hasCalendar) vm.syncCalendar(quiet = true)
    }

    val pending = state.tasks.filter { !it.completed }.sortedWith(compareBy<QuestTask> { it.startMs ?: Long.MAX_VALUE }.thenBy { it.title })
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
                    SmallButton("+ New quest", { showAdd = true }, Modifier.weight(1f))
                    SmallButton(
                        if (vm.syncingCalendar) "Syncing…" else "📅 Sync calendar",
                        { if (hasCalendar) vm.syncCalendar() else permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                        Modifier.weight(1f),
                        outlined = true,
                    )
                }
                Spacer(Modifier.height(10.dp))
                WeeklyCard(vm)
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
        items(pending, key = { it.id }) { task ->
            QuestRow(task, onComplete = { vm.completeTask(task.id) }, onDelete = { vm.deleteTask(task.id) })
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
                    "✔ ${task.title}",
                    modifier = Modifier.padding(vertical = 4.dp),
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showAdd) AddQuestDialog(onDismiss = { showAdd = false }, onAdd = { t, d -> vm.addTask(t, d); showAdd = false })
}

private val timeFmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

private fun whenText(task: QuestTask): String? {
    val start = task.startMs ?: return null
    val today = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = start }
    val sameDay = today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    val prefix = if (sameDay) "Today " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(start))
    else timeFmt.format(Date(start))
    return prefix
}

@Composable
private fun QuestRow(task: QuestTask, onComplete: () -> Unit, onDelete: () -> Unit) {
    GameCard(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                val sub = buildList {
                    if (task.source == TaskSource.CALENDAR) add("📅 " + (whenText(task) ?: "Calendar"))
                    add(task.difficulty.label)
                    add("+${task.difficulty.xp} XP")
                    add("+${task.difficulty.gold} 🪙")
                }.joinToString(" • ")
                Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (task.source == TaskSource.MANUAL) {
                TextButton(onClick = onDelete, contentPadding = PaddingValues(4.dp)) {
                    Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SmallButton("✔ Done", onComplete)
        }
    }
}

@Composable
private fun AddQuestDialog(onDismiss: () -> Unit, onAdd: (String, Difficulty) -> Unit) {
    var title by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New quest") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What do you need to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Difficulty", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Difficulty.entries.forEach { d ->
                        SmallButton(d.label, { difficulty = d }, Modifier.weight(1f), outlined = d != difficulty)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "Reward: +${difficulty.xp} XP, +${difficulty.gold} gold, +1 hunt token",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title, difficulty) }, enabled = title.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
