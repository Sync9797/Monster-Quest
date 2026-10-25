package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel

enum class Tab(val label: String, val emoji: String) {
    QUESTS("Quests", "📜"),
    HERO("Hero", "🧙"),
    MONSTERS("Monsters", "🐾"),
    MAP("Map", "🗺️"),
    BAG("Bag", "🎒"),
}

@Composable
fun AppRoot(vm: GameViewModel, initialTab: Tab = Tab.QUESTS) {
    val state = vm.state
    if (!state.starterChosen) {
        StarterScreen(onChoose = vm::chooseStarter)
        return
    }
    val battle = vm.battle
    if (battle != null) {
        BattleScreen(battle, onAction = vm::battleAction, onClose = vm::closeBattle)
        return
    }

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg != null) {
            vm.clearMessage()
            snackbar.showSnackbar(msg)
        }
    }

    // Wide screens (unfolded foldables, tablets) get a side rail; phones a bottom bar.
    val wide = widthClass() != WidthClass.COMPACT
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.emoji, fontSize = 22.sp) },
                            label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide) {
                // Scaffold already applied the system-bar insets; don't pad twice.
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Text(t.emoji, fontSize = 22.sp) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ResourceHeader(state.hero.level, state.gold, state.huntTokens)
                when (tab) {
                    Tab.QUESTS -> QuestsScreen(vm)
                    Tab.HERO -> HeroScreen(vm)
                    Tab.MONSTERS -> MonstersScreen(vm)
                    Tab.MAP -> MapScreen(vm)
                    Tab.BAG -> BagScreen(vm)
                }
            }
        }
    }
}
