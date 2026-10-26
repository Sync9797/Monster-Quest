package com.monsterquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Steps today/this week, the quest streak, and a month calendar marking days with completed quests. */
@Composable
fun DashboardCard(vm: GameViewModel) {
    val s = vm.state
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val todayDay = today.toEpochDay()
    val questDays: Set<Long> = s.questDays.filterValues { it > 0 }.keys +
        s.tasks.mapNotNull { it.completedAtMs }.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toEpochDay() }
    val stepsToday = if (s.stepsDay == todayDay) s.stepsToday else 0
    val weekStart = today.with(DayOfWeek.MONDAY).toEpochDay()
    val stepsWeek = s.stepHistory.filterKeys { it in weekStart..todayDay }.values.sum()
    // Consecutive days with a completed quest, counting back from today (or yesterday if today has none yet).
    var streak = 0
    var d = if (todayDay in questDays) todayDay else todayDay - 1
    while (d in questDays) {
        streak++
        d--
    }

    var month by remember { mutableStateOf(YearMonth.from(today)) }

    GameCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            DashStat(GameIcon.WALK, "%,d".format(stepsToday), "steps today", HpGreen)
            DashStat(GameIcon.WALK, "%,d".format(stepsWeek), "this week", MpBlue)
            DashStat(GameIcon.STAR, "$streak", "day streak", Gold)
        }
        Spacer(Modifier.height(10.dp))
        // Keep the calendar compact on wide (unfolded) screens.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { Column(Modifier.widthIn(max = 420.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }, contentPadding = PaddingValues(0.dp)) { Text("<") }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month < YearMonth.from(today),
                contentPadding = PaddingValues(0.dp),
            ) { Text(">") }
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1 // Monday first
        val cells = (List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) })
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1.25f), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, date.toEpochDay() in questDays, (s.stepHistory[date.toEpochDay()] ?: 0) >= STEP_GOAL, date == today, date > today)
                    }
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Mint, CircleShape))
            Text(" Quest done   ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(6.dp).background(HpGreen, CircleShape))
            Text(" ${"%,d".format(STEP_GOAL)}+ steps   ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.size(10.dp).border(1.5.dp, Gold, CircleShape))
            Text(" Today", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        } }
    }
}

private const val STEP_GOAL = 5_000

@Composable
private fun DashStat(icon: GameIcon, value: String, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconText(icon, value, tint = tint, bold = true, fontSize = 18.sp, size = 18.dp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(date: LocalDate, quest: Boolean, walked: Boolean, today: Boolean, future: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        var m = Modifier.size(30.dp)
        if (quest) m = m.background(Mint.copy(alpha = 0.85f), CircleShape)
        if (today) m = m.border(2.dp, Gold, CircleShape)
        Box(m, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (today || quest) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    quest -> Color(0xFF0D171A)
                    today -> Gold
                    future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (walked) Box(Modifier.align(Alignment.BottomEnd).size(6.dp).background(HpGreen, CircleShape))
    }
}
