package com.monsterquest.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.core.Dungeon
import com.monsterquest.core.DungeonRun
import com.monsterquest.core.Dungeons
import com.monsterquest.core.RoomKind

/** Stars for a dungeon's difficulty, e.g. "★★★☆☆". */
fun starsText(n: Int): String = "★".repeat(n) + "☆".repeat(5 - n)

private fun RoomKind.icon(): GameIcon = when (this) {
    RoomKind.MONSTERS -> GameIcon.PAW
    RoomKind.ELITE -> GameIcon.STAR
    RoomKind.COMBAT -> GameIcon.SKULL
    RoomKind.TRAP -> GameIcon.TARGET
    RoomKind.TREASURY -> GameIcon.CHEST
    RoomKind.FOUNTAIN -> GameIcon.DROP
    RoomKind.EMPTY -> GameIcon.MOON
    RoomKind.BOSS -> GameIcon.FIGHT
}

private fun RoomKind.color(): Color = when (this) {
    RoomKind.MONSTERS -> Color(0xFF8FD18A)
    RoomKind.ELITE -> Color(0xFFE0574A)
    RoomKind.COMBAT -> Color(0xFFB57FE0)
    RoomKind.TRAP -> Color(0xFFF2A65A)
    RoomKind.TREASURY -> Color(0xFFEBC678)
    RoomKind.FOUNTAIN -> Color(0xFF7FD6C2)
    RoomKind.EMPTY -> Color(0xFF9AA3A1)
    RoomKind.BOSS -> Color(0xFFFF6B5E)
}

/**
 * Inside a dungeon: the painted room, how far you are, your team's health,
 * what just happened, and two doors to choose from (the champion at the end).
 */
@Composable
fun DungeonScreen(vm: GameViewModel, run: DungeonRun) {
    val d = run.dungeon
    val s = vm.state
    var trapDoor by remember { mutableStateOf<Boolean?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    // A room's fight was interrupted (the app closed): pick it back up.
    LaunchedEffect(run.fighting) { if (run.fighting != null) vm.startDungeonFight() }
    // Each new room fades in.
    val enter = remember(run.step) { Animatable(0f) }
    LaunchedEffect(run.step) { enter.animateTo(1f, tween(500)) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(Scene.of(d.theme), Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f + 0.65f * enter.value })
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(d.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${starsText(d.stars)}  •  Lv ${d.level}", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            // Progress: a dot per room, then the champion.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(d.rooms) { i ->
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(if (i < run.step) Gold else Color.White.copy(alpha = 0.3f)))
                }
                GIcon(GameIcon.FIGHT, Modifier.size(16.dp), if (run.atBoss) Color(0xFFFF6B5E) else Color.White.copy(alpha = 0.4f))
            }
            Text(
                if (run.atBoss) "The champion's chamber" else "Room ${run.step + 1} of ${d.rooms}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            // Your team's health.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val heroFrac = 1f - s.heroDamage.toFloat() / s.hero.maxHp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GIcon(GameIcon.HERO, Modifier.size(28.dp), Color.White)
                    Bar(heroFrac, hpColor(heroFrac), Modifier.width(36.dp), heightDp = 4)
                }
                s.partyMonsters.forEach { m ->
                    val frac = if (m.fainted) 0f else m.currentHp.toFloat() / m.stats.hp
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MonsterArt(m.species, Modifier.size(28.dp), dim = m.fainted, shiny = m.shiny)
                        Bar(frac, hpColor(frac), Modifier.width(36.dp), heightDp = 4)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // What just happened.
            if (run.last.isNotEmpty()) {
                Text(
                    run.last,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp)
                        .graphicsLayer { alpha = enter.value },
                )
            }
            Spacer(Modifier.height(16.dp))
            if (run.atBoss) {
                val champ = Dungeons.champion(d)
                Text(champ.title, color = Color(0xFFFF6B5E), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(
                    "Defeat the champion and their monsters to clear the dungeon. Reward: +1 ${d.bossStat.label} for good (once a day) and a roll on the rare reward pool.",
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(onClick = { vm.openDoor(true) }, modifier = Modifier.fillMaxWidth()) { Text("Face the champion") }
            } else {
                Text("Choose a door", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val (left, right) = Dungeons.doors(run)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(true to left, false to right).forEach { (isLeft, room) ->
                        val hidden = Dungeons.hidden(run, isLeft)
                        Door(
                            room, hidden, Modifier.weight(1f),
                        ) { if (room == RoomKind.TRAP) trapDoor = isLeft else vm.openDoor(isLeft) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Found so far: ${run.gold} gold" + (if (run.torches > 0) ", ${run.torches} torches" else "") +
                    (if (run.items.isNotEmpty()) ", ${run.items.values.sum()} items" else "") +
                    (if (run.pages.isNotEmpty()) ", ${run.pages.values.sum()} pages" else "") +
                    (if (run.gear.isNotEmpty() || run.armor.isNotEmpty()) ", gear" else ""),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { confirmLeave = true }) { Text("Leave the dungeon", color = Color.White) }
        }
    }

    trapDoor?.let { isLeft ->
        val chance = (Dungeons.trapChance(s, d) * 100).toInt()
        val (l, r) = Dungeons.doors(run)
        val other = if (isLeft) r else l
        AlertDialog(
            onDismissRequest = { trapDoor = null },
            title = { Text("A trap!") },
            text = {
                Text(
                    "The floor is laced with traps. You have a $chance% chance to get through unhurt. " +
                        "If it goes wrong, everyone takes damage, but you can keep going." +
                        if (other != RoomKind.TRAP) " Or take the other door (${other.label.lowercase()})." else "",
                )
            },
            confirmButton = { Button(onClick = { trapDoor = null; vm.springTrap() }) { Text("Risk it ($chance%)") } },
            dismissButton = {
                if (other != RoomKind.TRAP) TextButton(onClick = { trapDoor = null; vm.openDoor(!isLeft) }) { Text("Take the other door") }
                else TextButton(onClick = { trapDoor = null }) { Text("Not yet") }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the ${d.name}?") },
            text = { Text("You keep everything you've found. The torches you spent to enter are gone, and the champion waits for another day.") },
            confirmButton = { Button(onClick = { confirmLeave = false; vm.leaveDungeon() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
        )
    }
}

/** A door: its room's sign (or "?" when hidden). */
@Composable
private fun Door(room: RoomKind, hidden: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (hidden) Color(0xFFBFC7D1) else room.color()
    Column(
        modifier
            .height(170.dp)
            .clip(RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .background(Color(0xFF3A2A1E).copy(alpha = 0.92f))
            .border(3.dp, tint, RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (hidden) {
            Text("?", fontSize = 44.sp, fontWeight = FontWeight.Black, color = tint)
            Text("Unknown", color = Color.White, fontWeight = FontWeight.SemiBold)
        } else {
            GIcon(room.icon(), Modifier.size(44.dp), tint)
            Spacer(Modifier.height(6.dp))
            Text(room.label, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(room.hint, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A dungeon's entrance on the map: what's inside, before you spend torches. */
@Composable
fun DungeonDialog(vm: GameViewModel, d: Dungeon, distance: Double?, onDismiss: () -> Unit) {
    val inRange = distance != null && distance <= Dungeons.RANGE_M
    val s = vm.state
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(d.name)
                Text("${d.theme.label} • ${starsText(d.stars)}", color = Gold, fontSize = 14.sp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Recommended level ${d.level} (you're ${s.hero.level}) • ${d.rooms} rooms, then the champion • " +
                        "costs ${d.torchCost} torch${if (d.torchCost == 1) "" else "es"} (you have ${s.torches})",
                    fontSize = 13.sp,
                )
                Text("You may meet", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Dungeons.residents(d).forEach { sp ->
                        val seen = sp.id in s.seen
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MonsterArt(sp, Modifier.size(44.dp), silhouette = !seen)
                            Text(if (seen) sp.name else "???", fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                Text(
                    "Plus cursed guardians that can't be tamed, traps, treasure and fairy fountains.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val champ = Dungeons.champion(d)
                Text("Champion: ${champ.title} (Lv ${champ.level} ${d.bossClass.label})", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text("Reward pool", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Dungeons.bossRewards(d).forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(r.label, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${r.pct}%", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (!inRange) {
                    Text(
                        "Walk within ${formatDistance(Dungeons.RANGE_M, s.settings.imperialUnits)} to enter (${distance?.let { formatDistance(it, s.settings.imperialUnits) } ?: "?"} away).",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.enterDungeon(d); onDismiss() }, enabled = inRange && s.torches >= d.torchCost) { Text("Enter (${d.torchCost} torch${if (d.torchCost == 1) "" else "es"})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
