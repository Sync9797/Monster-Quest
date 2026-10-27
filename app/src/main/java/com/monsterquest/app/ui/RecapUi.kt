package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.core.Recap
import com.monsterquest.core.SleepLog
import java.time.LocalDate

/**
 * The morning recap: the first time the game opens each morning, what
 * yesterday brought (levels, monster XP, loot, HP restored, tasks done) and
 * how last night's sleep went.
 */
@Composable
fun MorningRecap(vm: GameViewModel) {
    val s = vm.state
    val now = System.currentTimeMillis()
    val y = Recap.yesterday(s, now)
    val night = SleepLog.lastNight(s, LocalDate.now())
    AlertDialog(
        onDismissRequest = vm::dismissRecap,
        icon = { GIcon(GameIcon.STAR, Modifier.size(28.dp), Gold) },
        title = { Text("${greeting(now)}, ${s.hero.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Yesterday", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                RecapRow(GameIcon.HERO, XpPurple, "Levels grown", if (y.heroLevels > 0) "+${y.heroLevels}" else "None")
                RecapRow(
                    GameIcon.MONSTERS, Mint, "Monster XP",
                    "+${"%,d".format(y.monsterXp)}" + if (y.monsterLevels > 0) " (${y.monsterLevels} level${if (y.monsterLevels == 1) "" else "s"})" else "",
                )
                RecapRow(GameIcon.BAG, Gold, "Loot", lootText(y))
                RecapRow(GameIcon.HEART, HpRed, "HP restored", "%,d".format(y.hpRestored))
                RecapRow(GameIcon.CHECK, MpBlue, "Tasks completed", y.tasks.toString())
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Last night", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                if (night != null) {
                    RecapRow(GameIcon.SLEEP, Color(0xFF7B7FE0), "Sleep", SleepLog.label(night.asleepMin))
                    if (night.hasStages) {
                        Text(
                            "Deep ${SleepLog.label(night.deepMin)} • REM ${SleepLog.label(night.remMin)} • Light ${SleepLog.label(night.lightMin)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "No sleep tracked. Sync it from Health Connect or log it on the Health card.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissRecap) { Text("Let's go") } },
    )
}

@Composable
private fun RecapRow(icon: GameIcon, tint: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, Modifier.size(18.dp), tint)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** "120 gold, 3 items, 1 piece of gear" or "None". */
private fun lootText(t: com.monsterquest.core.DayTally): String {
    val items = t.items.values.sum()
    val parts = listOfNotNull(
        "${"%,d".format(t.gold)} gold".takeIf { t.gold > 0 },
        "$items item${if (items == 1) "" else "s"}".takeIf { items > 0 },
        "${t.gear} gear".takeIf { t.gear > 0 },
    )
    return if (parts.isEmpty()) "None" else parts.joinToString(", ")
}
