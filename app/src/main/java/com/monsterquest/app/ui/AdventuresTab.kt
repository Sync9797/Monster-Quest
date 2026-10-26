package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.monsterquest.core.ActiveAdventure
import com.monsterquest.core.Adventure
import com.monsterquest.core.Adventures
import kotlinx.coroutines.delay

/** Send monsters on timed adventures at real places near you. */
@Composable
fun AdventuresTab(vm: GameViewModel) {
    val s = vm.state
    val context = LocalContext.current

    if (s.monsters.size < Adventures.UNLOCK_MONSTERS) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GIcon(GameIcon.COMPASS, Modifier.size(64.dp), Gold)
            Text("Adventures unlock at ${Adventures.UNLOCK_MONSTERS} monsters", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Bar(s.monsters.size.toFloat() / Adventures.UNLOCK_MONSTERS, XpPurple, Modifier.fillMaxWidth())
            Text("${s.monsters.size}/${Adventures.UNLOCK_MONSTERS} collected", fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Once unlocked, send teams of monsters to real places nearby. They battle on their own " +
                    "and bring back gold and items while you're busy.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.any { it }
    }
    if (granted) TrackLocation(context, vm.state.settings.batterySaverGps, vm::onLocation)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    var picking by remember { mutableStateOf<Adventure?>(null) }
    val offers = vm.adventureOffers(now)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text(
                "Monsters on adventures fight without you (at ${(Adventures.AUTO_BATTLE_EFFECTIVENESS * 100).toInt()}% strength). " +
                    "Your hero earns no XP, and the team is away until it returns. Your battle party stays with you: only bench monsters can go.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Out adventuring ${s.adventures.size}/${Adventures.MAX_ACTIVE}")
            if (s.adventures.isEmpty()) {
                Text("Nobody's out right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(s.adventures, key = { "active_" + it.adventure.id }) { a ->
            ActiveAdventureCard(vm, a, now)
        }
        item {
            SectionTitle("Adventures near you today")
            when {
                !granted -> {
                    Text("Adventures are posted at real places around you.")
                    SmallButton(
                        "Enable location",
                        { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    )
                }
                vm.location == null -> Text("Finding your location…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                vm.loadingPlaces -> Text("Scanning nearby places…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                offers.isEmpty() -> Text(
                    "No more adventures here today. New ones are posted tomorrow.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(offers, key = { it.id }) { adv ->
            AdventureCard(adv, canSend = s.adventures.size < Adventures.MAX_ACTIVE) { picking = adv }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    picking?.let { adv ->
        TeamPicker(vm, adv, onDismiss = { picking = null }) { uids ->
            vm.sendAdventure(adv, uids)
            picking = null
        }
    }
}

/** Difficulty as five drawn stars (filled up to [d]). */
@Composable
private fun Stars(d: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i -> GIcon(GameIcon.STAR, Modifier.size(13.dp), if (i <= d) Gold else Line) }
    }
}

private fun minutes(ms: Long) = formatDuration(ms.coerceAtLeast(0))

@Composable
private fun AdventureCard(adv: Adventure, canSend: Boolean, onPick: () -> Unit) {
    GameCard(Modifier.padding(vertical = 4.dp)) {
        Text(adv.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Stars(adv.difficulty)
        Text(
            "Team of ${adv.teamSize}  •  Rec. Lv ${adv.recommendedLevel}  •  ${minutes(adv.durationMs)}",
            fontSize = 12.sp,
            color = Gold,
        )
        Text(
            "Rewards: gold, items" + (if (adv.difficulty >= 3) ", weapon parts" else "") + ", monster XP",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SmallButton(if (canSend) "Choose team" else "Too many adventures running", onPick, Modifier.fillMaxWidth(), enabled = canSend)
    }
}

@Composable
private fun ActiveAdventureCard(vm: GameViewModel, a: ActiveAdventure, now: Long) {
    val team = vm.state.monsters.filter { it.uid in a.monsterUids }
    val done = a.done(now)
    GameCard(Modifier.padding(vertical = 4.dp), border = if (done) Gold else MpBlue) {
        Text(a.adventure.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            team.forEach { MonsterArt(it.species, Modifier.size(40.dp)) }
        }
        Text(team.joinToString { it.displayName }, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Success chance ${(a.successChance * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val total = a.adventure.durationMs.toFloat()
        Bar(((now - a.startMs) / total).coerceIn(0f, 1f), if (done) Gold else MpBlue, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        if (done) {
            SmallButton("Welcome them back", { vm.claimAdventure(a.adventure.id) }, Modifier.fillMaxWidth())
        } else {
            Text("Back in ${minutes(a.endMs - now)}", fontSize = 13.sp)
        }
    }
}

@Composable
private fun TeamPicker(vm: GameViewModel, adv: Adventure, onDismiss: () -> Unit, onSend: (List<String>) -> Unit) {
    val available = Adventures.available(vm.state).sortedByDescending { it.level }
    var chosen by remember(adv.id) { mutableStateOf(listOf<String>()) }
    val team = available.filter { it.uid in chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose ${adv.teamSize} monster${if (adv.teamSize == 1) "" else "s"}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(adv.title, fontSize = 13.sp)
                Text("Recommended level ${adv.recommendedLevel}", fontSize = 12.sp, color = Gold)
                if (team.size == adv.teamSize) {
                    val pct = (Adventures.successChance(team, adv) * 100).toInt()
                    Text("Success chance: $pct%", fontWeight = FontWeight.Bold, color = if (pct >= 60) HpGreen else HpYellow)
                }
                Spacer(Modifier.height(6.dp))
                if (available.size < adv.teamSize) {
                    Text("Not enough monsters available. Your battle party stays with you, and fainted or away monsters can't go.", color = HpRed, fontSize = 13.sp)
                }
                available.forEach { m ->
                    val on = m.uid in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosen = when {
                                    on -> chosen - m.uid
                                    chosen.size < adv.teamSize -> chosen + m.uid
                                    else -> chosen
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GIcon(if (on) GameIcon.CHECK else GameIcon.RING, Modifier.size(20.dp), if (on) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        MonsterArt(m.species, Modifier.size(36.dp), shiny = m.shiny)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Lv ${m.level} ${m.species.element.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(chosen) }, enabled = chosen.size == adv.teamSize) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
