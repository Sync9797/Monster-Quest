package com.monsterquest.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.core.Health
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val WaterBlue = Color(0xFF4FA8E8)
private val SleepIndigo = Color(0xFF7B7FE0)

/**
 * Health on the Home screen: a card that folds to one line (steps and water
 * today) and opens into steps by hour, a water tracker and a preview of the
 * sleep tracker to come. Nothing here pays rewards; it's just for you.
 */
@Composable
fun HealthCard(vm: GameViewModel, nowMs: Long) {
    val s = vm.state
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val steps = if (s.stepsDay == today.toEpochDay()) s.stepsToday else 0
    val glasses = Health.glasses(s, today.toEpochDay())
    val open = s.settings.healthOpen
    LaunchedEffect(open) { if (open) vm.refreshHourlySteps() }

    GameCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { vm.updateSettings { it.copy(healthOpen = !open) } }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heart(Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Health", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${"%,d".format(steps)} steps • $glasses of ${s.waterGoal} glasses",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column {
                Spacer(Modifier.height(10.dp))
                StepsSection(vm, today, steps, nowMs)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                WaterSection(vm, glasses)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SleepPreview()
            }
        }
    }
}

@Composable
private fun Heart(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w / 2, h * 0.9f)
            cubicTo(w * -0.05f, h * 0.55f, w * 0.1f, h * 0.02f, w / 2, h * 0.28f)
            cubicTo(w * 0.9f, h * 0.02f, w * 1.05f, h * 0.55f, w / 2, h * 0.9f)
            close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFF6B7A), Color(0xFFD93A55))))
    }
}

@Composable
private fun SectionHeader(icon: GameIcon, title: String, tint: Color, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, Modifier.size(18.dp), tint)
        Spacer(Modifier.width(6.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Today's steps, with a bar for every hour. Tap a bar to see its count. */
@Composable
private fun StepsSection(vm: GameViewModel, today: LocalDate, steps: Int, nowMs: Long) {
    val fromHealth = vm.healthHourly
    val hours = fromHealth ?: Health.hourly(vm.state, today)
    val nowHour = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
    var selected by remember { mutableIntStateOf(nowHour) }
    SectionHeader(GameIcon.WALK, "Steps", Mint, "Goal ${"%,d".format(Health.STEP_GOAL)}")
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("%,d".format(steps), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(" today", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
    // Progress toward the day's goal.
    val pct = (steps.toFloat() / Health.STEP_GOAL).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(pct).height(6.dp).clip(CircleShape).background(Mint))
    }
    Spacer(Modifier.height(10.dp))
    val max = (hours.maxOrNull() ?: 0).coerceAtLeast(100)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val gold = Gold
    val mint = Mint
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = "Steps by hour" }
            .pointerInput(Unit) {
                detectTapGestures { o -> selected = (o.x / size.width * 24).toInt().coerceIn(0, 23) }
            },
    ) {
        val slot = size.width / 24
        val barW = slot * 0.62f
        for (h in 0 until 24) {
            val x = h * slot + (slot - barW) / 2
            // Faint track for every hour, then the hour's steps on top.
            drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
            val v = hours[h]
            if (v > 0) {
                val bh = (v.toFloat() / max * size.height).coerceAtLeast(barW)
                val c = when (h) {
                    selected -> gold
                    else -> if (h > nowHour) mint.copy(alpha = 0.35f) else mint
                }
                drawRoundRect(c, Offset(x, size.height - bh), Size(barW, bh), CornerRadius(barW / 2))
            } else if (h == selected) {
                drawRoundRect(gold.copy(alpha = 0.5f), Offset(x, size.height - barW), Size(barW, barW), CornerRadius(barW / 2))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        listOf(0, 6, 12, 18).forEach { h ->
            Text(Health.hourLabel(h), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
    val busiest = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    Text(
        "${Health.hourLabel(selected)}–${Health.hourLabel((selected + 1) % 24)}: ${"%,d".format(hours[selected])} steps" +
            (busiest?.let { "  •  Busiest: ${Health.hourLabel(it)}" } ?: ""),
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        if (fromHealth != null) "From Health Connect" else "Counted by the game while it gets step updates",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Glasses of water today, against a daily goal. Just for keeping track. */
@Composable
private fun WaterSection(vm: GameViewModel, glasses: Int) {
    val goal = vm.state.waterGoal
    val imperial = vm.state.settings.imperialUnits
    SectionHeader(GameIcon.DROP, "Water", WaterBlue, "1 glass = ${if (imperial) "${Health.GLASS_OZ} oz" else "${Health.GLASS_ML} ml"}")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { vm.addWater(-1) }, enabled = glasses > 0, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$glasses of $goal glasses", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                Health.waterAmount(glasses, imperial) + if (glasses >= goal) "  •  Goal reached!" else "",
                fontSize = 12.sp,
                color = if (glasses >= goal) WaterBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { vm.addWater(1) }, modifier = Modifier.size(44.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    // A drop for every glass of the goal (and any extra), filled as you drink.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
        repeat(maxOf(goal, glasses).coerceAtMost(16)) { i ->
            GIcon(
                GameIcon.DROP,
                Modifier.size(16.dp),
                if (i < glasses) WaterBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Daily goal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { vm.setWaterGoal(goal - 1) }, enabled = goal > Health.WATER_GOALS.first) { Text("−") }
        Text("$goal glasses", fontSize = 13.sp)
        TextButton(onClick = { vm.setWaterGoal(goal + 1) }, enabled = goal < Health.WATER_GOALS.last) { Text("+") }
    }
}

/**
 * What the sleep tracker will look like. It shows sample data and is
 * clearly marked as a preview; nothing is tracked yet.
 */
@Composable
private fun SleepPreview() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(GameIcon.SLEEP, Modifier.size(18.dp), SleepIndigo)
        Spacer(Modifier.width(6.dp))
        Text("Sleep", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "Coming soon",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = SleepIndigo,
            modifier = Modifier.border(1.dp, SleepIndigo, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    Column(Modifier.alpha(0.55f).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("7h 35m", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("  last night", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text("11:10 PM → 6:45 AM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        // The night as a band: light and deep sleep, with a short wake-up.
        Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
            listOf(0.18f to 0.6f, 0.22f to 1f, 0.05f to 0.25f, 0.3f to 0.6f, 0.25f to 1f).forEach { (w, a) ->
                Box(Modifier.weight(w).height(12.dp).background(SleepIndigo.copy(alpha = a)))
            }
        }
        Spacer(Modifier.height(8.dp))
        // The week at a glance.
        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            listOf("M" to 0.8f, "T" to 0.7f, "W" to 0.9f, "T" to 0.6f, "F" to 0.75f, "S" to 1f, "S" to 0.85f).forEach { (_, f) ->
                Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(4.dp)).background(SleepIndigo))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    Text(
        "Preview with sample numbers. Sleep tracking isn't on yet.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
