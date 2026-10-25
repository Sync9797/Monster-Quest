package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.BAG_UPGRADE_AMOUNT
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroSkills
import com.monsterquest.core.STORAGE_UPGRADE_AMOUNT
import com.monsterquest.core.UPGRADE_LEVEL_INTERVAL

@Composable
fun HeroScreen(vm: GameViewModel) {
    val s = vm.state
    val h = s.hero
    var renaming by remember { mutableStateOf(false) }

    val profile: @Composable () -> Unit = {
        GameCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧙", fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Level ${h.level} Adventurer", color = XpPurple)
                }
                TextButton(onClick = { renaming = true }) { Text("Rename") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledBar("XP", h.xp, h.xpToNext, XpPurple)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatPill("Max HP", h.maxHp.toString())
                StatPill("Energy", h.maxMp.toString())
                StatPill("Atk", h.attack.toString())
                StatPill("Mag", h.magic.toString())
                StatPill("Def", h.defense.toString())
                StatPill("Spd", h.speed.toString())
            }
        }

        SectionTitle(if (h.unspentPoints > 0) "Attributes: ${h.unspentPoints} point(s) to spend!" else "Attributes")
        HeroAttribute.entries.forEach { a ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.emoji, fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${a.label}: ${h.attr(a)}", fontWeight = FontWeight.SemiBold)
                        Text(a.blurb, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.allocate(a) }, enabled = h.unspentPoints > 0) { Text("+1") }
                }
            }
        }
        Text(
            "Complete quests to earn XP. Each level gives 3 attribute points.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

    }
    val progress: @Composable () -> Unit = {
        SectionTitle("Storage upgrades")
        GameCard(border = if (s.pendingUpgrades > 0) Gold else null) {
            Text("🎒 Bag: ${s.bagCount}/${s.bagCapacity}    🐾 Monsters: ${s.monsters.size}/${s.monsterCapacity}")
            if (s.pendingUpgrades > 0) {
                Text("You have ${s.pendingUpgrades} upgrade(s) to claim!", color = Gold, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.upgrade(bag = true) }, modifier = Modifier.weight(1f)) { Text("Bag +$BAG_UPGRADE_AMOUNT") }
                    Button(onClick = { vm.upgrade(bag = false) }, modifier = Modifier.weight(1f)) { Text("Monsters +$STORAGE_UPGRADE_AMOUNT") }
                }
            } else {
                val next = (h.level / UPGRADE_LEVEL_INTERVAL + 1) * UPGRADE_LEVEL_INTERVAL
                Text(
                    "Next upgrade at level $next.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle("Hunt")
        GameCard {
            Text("Spend a hunt token to find a wild monster wherever you are.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::hunt, enabled = s.huntTokens > 0, modifier = Modifier.fillMaxWidth()) {
                Text("🎯 Hunt (${s.huntTokens} tokens)")
            }
        }

        SectionTitle("Spells")
        HeroSkills.all.forEach { sk ->
            val unlocked = sk.unlockLevel <= h.level
            Text(
                "${sk.element.emoji} ${sk.name}: ${if (unlocked) "${sk.mpCost} Energy" else "unlocks at Lv ${sk.unlockLevel}"}",
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }

        SectionTitle("Journey")
        val st = s.stats
        Text("Quests completed: ${st.tasksCompleted}")
        Text("Battles won / lost: ${st.battlesWon} / ${st.battlesLost}")
        Text("Monsters tamed: ${st.monstersTamed}")
        Text("Shrines visited: ${st.shrinesVisited}")
        Text("Bosses defeated: ${st.bossesDefeated}")
    }

    AdaptivePanes(
        compact = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                profile()
                progress()
                Spacer(Modifier.height(24.dp))
            }
        },
        first = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
                profile()
                Spacer(Modifier.height(24.dp))
            }
        },
        second = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 16.dp)) {
                progress()
                Spacer(Modifier.height(24.dp))
            }
        },
    )

    if (renaming) {
        var name by remember { mutableStateOf(h.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Hero name") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(16) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.renameHero(name); renaming = false }) { Text("Save") } },
            dismissButton = { OutlinedButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
