package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
fun AppRoot(vm: GameViewModel, initialTab: Tab = Tab.QUESTS) {
    val state = vm.state
    if (!state.starterChosen) {
        StarterScreen(onChoose = vm::chooseStarter)
        return
    }
    val focus = state.focus
    if (focus != null) {
        FocusOverlay(focus, onGiveUp = vm::giveUpFocus)
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

    // The menu stays at the bottom on every screen size, folded or open, so it's
    // always within thumb reach.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
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
