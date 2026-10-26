package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Schedule

/** This week's quest progress and whether the silver/gold shrines are open. */
@Composable
fun WeeklyCard(vm: GameViewModel) {
    val now = System.currentTimeMillis()
    val week = Schedule.weekSoFar(vm.state, now)
    val workweek = Schedule.workweek(vm.state, now)
    val silver = Schedule.silver(vm.state, now)
    val gold = Schedule.gold(vm.state, now)

    GameCard(border = if (gold.open) Gold else if (silver.open) SilverColor else null) {
        Row(Modifier.fillMaxWidth()) {
            Text("🗓️ This week", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("${week.done}/${week.total} quests (${week.percent}%)", fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Mon–Fri progress toward the 🥇 weekend goal (${Schedule.GOLD_PERCENT}%, at least ${Schedule.GOLD_MIN_DONE})", fontSize = 12.sp)
        Bar(workweek.percent / 100f, if (workweek.percent >= Schedule.GOLD_PERCENT) Gold else XpPurple, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "🥈 " + silver.reason,
            fontSize = 12.sp,
            color = if (silver.open) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "🥇 " + gold.reason,
            fontSize = 12.sp,
            color = if (gold.open) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
