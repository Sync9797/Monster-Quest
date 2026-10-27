package com.monsterquest.app.ui

import androidx.activity.compose.BackHandler
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.ArtQuality
import com.monsterquest.app.ui.art.MonsterArt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.color
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel

enum class Tab(val label: String, val icon: GameIcon) {
    QUESTS("Quests", GameIcon.QUESTS),
    HERO("Hero", GameIcon.HERO),
    MONSTERS("Monsters", GameIcon.MONSTERS),
    MAP("Map", GameIcon.MAP),
    BAG("Bag", GameIcon.BAG),
}

@Composable
fun AppRoot(vm: GameViewModel, initialTab: Tab = Tab.QUESTS, initialSettings: Boolean = false) {
    val state = vm.state
    ArtQuality.reduced = state.settings.reducedEffects
    if (!state.starterChosen) {
        StarterScreen(onChoose = vm::chooseStarter)
        return
    }
    val focus = state.focus
    if (focus != null) {
        FocusOverlay(focus, onGiveUp = vm::giveUpFocus, onDone = vm::onFocusTimerDone, penaltyGold = com.monsterquest.core.GameEngine.focusPenalty(state))
        return
    }
    val battle = vm.battle
    if (battle != null) {
        BattleScreen(battle, onAction = vm::battleAction, onClose = vm::closeBattle, onNext = vm::nextVillainMonster, outfit = HeroOutfit.of(state.equippedGear, state.equippedArmor, state.equippedPieces), onTarget = vm::selectTarget, onReplace = vm::replaceFainted)
        return
    }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var settingsOpen by rememberSaveable { mutableStateOf(initialSettings) }
    // Back steps out of Settings, then from any tab to Quests; only Back on Quests leaves the app.
    BackHandler(enabled = !settingsOpen && tab != Tab.QUESTS) { tab = Tab.QUESTS }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            vm.clearMessage()
            snackbar.showSnackbar(msg)
        }
    }

    vm.focusResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissFocusResult,
            title = { Text(if (vm.focusSucceeded) "Session complete!" else "Session failed") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissFocusResult) { Text("OK") } },
        )
    }

    vm.adventureResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissAdventureResult,
            title = { Text(if (vm.adventureSucceeded) "Adventure complete!" else "Adventure failed") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissAdventureResult) { Text("OK") } },
        )
    }

    vm.boxResult?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissBoxResult,
            title = { Text(if (vm.boxBoss != null) "A boss emerges!" else "Loot box opened!") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = vm::dismissBoxResult) { Text(if (vm.boxBoss != null) "Fight!" else "Awesome") } },
        )
    }


    if (state.villainSpoils.isNotEmpty()) SpoilsDialog(vm)
    // Yesterday's gains and last night's sleep, on the first open each morning.
    if (vm.showRecap) MorningRecap(vm)
    if (state.starterGiftPending && !vm.starterGiftLater) StarterGiftDialog(vm)
    state.pendingDenCatch?.let { DenCatchDialog(vm, it) }
    // A den cast: the crystal shakes before the result.
    vm.denSeal?.let { (roll, _) -> BindingSeal(roll, Gold, vm::finishDenSeal) }

    // The menu stays at the bottom on every screen size, folded or open, so it's
    // always within thumb reach.
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !settingsOpen,
                        onClick = { tab = t; settingsOpen = false },
                        icon = { GIcon(t.icon, Modifier.size(24.dp)) },
                        label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ResourceHeader(state.hero.level, state.gold, state.huntTokens, onSettings = { settingsOpen = !settingsOpen })
                if (settingsOpen) {
                    SettingsScreen(vm) { settingsOpen = false }
                } else when (tab) {
                    Tab.QUESTS -> QuestsScreen(vm)
                    Tab.HERO -> HeroScreen(vm)
                    Tab.MONSTERS -> MonstersScreen(vm)
                    Tab.MAP -> MapScreen(vm)
                    Tab.BAG -> BagScreen(vm)
                }
            }
        }
    }
    // Battery saver: black screen on top while the map keeps running underneath.
    if (vm.batterySaver) BatterySaverOverlay(vm)
    vm.lootPopup?.let { LootPopupCard(it) { vm.lootPopup = null } }
    if (vm.drivingWarning) DrivingWarning { vm.drivingWarning = false }
    }
}

/** Moving faster than 15 mph: monsters stop appearing, and a reminder to keep your eyes on the road. */
@Composable
private fun DrivingWarning(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Moving too fast") },
        text = {
            Text(
                "You're going faster than ${com.monsterquest.core.SpeedGuard.LIMIT_MPH.toInt()} mph, so monsters have stopped " +
                    "appearing.\n\nPlease don't play while driving. Keep your eyes on the road. If you're a passenger, enjoy " +
                    "the ride: monsters come back once you're on foot again.",
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("I'm not driving") } },
    )
}

/** A den monster is beaten: a few throws to tame it before it slips away. */
@Composable
private fun DenCatchDialog(vm: GameViewModel, c: com.monsterquest.core.DenCatch) {
    val m = c.monster
    val pct = (com.monsterquest.core.Dens.throwOdds(m, c.bait) * 100).toInt()
    val perfectPct = (com.monsterquest.core.Dens.throwOdds(m, c.bait, com.monsterquest.core.TraceGrade.PERFECT) * 100).toInt()
    var casting by remember { mutableStateOf(false) }
    if (casting) {
        val glyph = remember(m.uid) { com.monsterquest.core.Binding.glyphFor(m.species) }
        BindingCast(
            title = "Binding the ${m.species.name}",
            glyph = glyph,
            gradeOdds = com.monsterquest.core.TraceGrade.entries.associateWith { (com.monsterquest.core.Dens.throwOdds(m, c.bait, it) * 100).toInt() },
            onCancel = { casting = false },
        ) { grade ->
            casting = false
            vm.denThrow(grade)
        }
    }
    val overall = (com.monsterquest.core.Dens.oddsOver(m, c.throwsLeft, c.bait) * 100).toInt()
    val berries = com.monsterquest.core.Item.entries.filter { it.kind == com.monsterquest.core.ItemKind.BAIT && vm.state.count(it) > 0 && c.bait < it.power }
    val bonus = c.throwsTotal - com.monsterquest.core.Dens.BASE_THROWS
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Tame the ${m.species.name}!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterArt(m.species, Modifier.size(110.dp), shiny = m.shiny)
                Text("Lv ${m.level} • ${com.monsterquest.core.GameEngine.perfectStatCount(m)} perfect stats", fontWeight = FontWeight.SemiBold)
                Text(
                    "Catch rate: $pct%" + if (c.bait > 1.0) " on this cast (berry ${c.bait}x)" else " per cast",
                    fontWeight = FontWeight.SemiBold,
                    color = Gold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "$overall% to tame it with your ${c.throwsLeft} cast${if (c.throwsLeft == 1) "" else "s"} left, at a Good trace " +
                        "(including a 2% critical catch on every cast). A Perfect trace gives ${perfectPct}%." +
                        if (bonus > 0) " +$bonus cast${if (bonus == 1) "" else "s"} for your monsters still standing." else "",
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(c.throwsTotal) { i ->
                        GIcon(GameIcon.ORB, Modifier.size(18.dp), if (i < c.throwsLeft) Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    }
                }
                // Feed a berry before the next throw (it doesn't use a throw).
                berries.forEach { berry ->
                    OutlinedButton(onClick = { vm.denFeed(berry) }, modifier = Modifier.padding(top = 4.dp)) {
                        GIcon(berry.icon(), Modifier.size(16.dp), berry.color())
                        Spacer(Modifier.width(6.dp))
                        Text("Feed ${berry.label} x${vm.state.count(berry)}  •  ${berry.power}x", fontSize = 13.sp)
                    }
                }
                if (vm.state.storageFull) Text("Monster storage is full: make room first.", color = HpRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = { if (vm.state.settings.simpleCasting) vm.denThrow() else casting = true },
                enabled = !vm.state.storageFull,
            ) { Text("Cast Binding") }
        },
        dismissButton = { TextButton(onClick = vm::denGiveUp) { Text("Let it go") } },
    )
}

/** For players from before 0.5: pick one of the new starters, free. */
@Composable
private fun StarterGiftDialog(vm: GameViewModel) {
    val level = com.monsterquest.core.GameEngine.giftStarterLevel(vm.state.hero.level)
    AlertDialog(
        onDismissRequest = { vm.starterGiftLater = true },
        title = { Text("New starters have arrived!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Thanks for playing from the start. Three rare starters joined Monster Quest: pick one to join you " +
                        "at level $level. Each evolves twice into a pseudo-legendary.",
                    fontSize = 13.sp,
                )
                com.monsterquest.core.GameEngine.starters.map { com.monsterquest.core.Bestiary.get(it) }.forEach { sp ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonsterArt(sp, Modifier.size(56.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sp.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${sp.typeLabel} • " + com.monsterquest.core.Bestiary.lineOf(sp).drop(1).joinToString(" > ") { it.name },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SmallButton("Choose", { vm.claimStarterGift(sp.id) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.starterGiftLater = true }) { Text("Decide later") } },
    )
}

/** After beating a villain's team: restore one of their monsters (75% it joins you). */
@Composable
private fun SpoilsDialog(vm: GameViewModel) {
    val s = vm.state
    val potions = s.count(com.monsterquest.core.Item.RESTORE_POTION)
    AlertDialog(
        onDismissRequest = vm::dismissSpoils,
        title = { Text("Redeem a cursed monster?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Use a Restore Potion on one of their monsters. There's a 75% chance it joins you, " +
                        "still carrying the shadow curse until you battle together. You have $potions.",
                    fontSize = 13.sp,
                )
                if (s.storageFull) Text("Your monster storage is full.", color = HpRed, fontSize = 13.sp)
                s.villainSpoils.forEachIndexed { i, m ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MonsterArt(m.species, Modifier.size(48.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName, fontWeight = FontWeight.SemiBold)
                            Text("Lv ${m.level} ${m.species.typeLabel}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SmallButton("Restore", { vm.redeemVillainMonster(i) }, enabled = potions > 0 && !s.storageFull)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissSpoils) { Text("Leave them") } },
    )
}
