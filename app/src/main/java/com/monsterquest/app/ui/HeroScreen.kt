package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.monsterquest.core.CLASS_CHANGE_COST
import com.monsterquest.core.CLASS_UNLOCK_LEVEL
import com.monsterquest.core.HeroAttribute
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Spells
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
                Text(h.heroClass.emoji, fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Level ${h.level} ${h.heroClass.label}", color = XpPurple)
                }
                TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(4.dp)) { Text("✏️") }
            }
            Spacer(Modifier.height(8.dp))
            LabeledBar("XP", h.xp, h.xpToNext, XpPurple)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatPill("Max HP", h.maxHp.toString(), Modifier.weight(1f))
                StatPill("Energy", h.maxMp.toString(), Modifier.weight(1f))
                StatPill("Atk", h.attack.toString(), Modifier.weight(1f))
                StatPill("Mag", h.magic.toString(), Modifier.weight(1f))
                StatPill("Def", h.defense.toString(), Modifier.weight(1f))
                StatPill("Spd", h.speed.toString(), Modifier.weight(1f))
            }
        }

        ClassSection(vm)

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
                    SmallButton("🎒 Bag +$BAG_UPGRADE_AMOUNT", { vm.upgrade(bag = true) }, Modifier.weight(1f))
                    SmallButton("🐾 Storage +$STORAGE_UPGRADE_AMOUNT", { vm.upgrade(bag = false) }, Modifier.weight(1f))
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

        SectionTitle("Weapon")
        GameCard {
            val w = s.equippedWeapon
            if (w == null) {
                Text("Bare-handed. Find weapons at item shrines 🎁 and equip them from the Bag.")
            } else {
                Text("${w.emoji} ${w.label}", fontWeight = FontWeight.SemiBold)
                Text(w.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle("Spells")
        Text(
            "Collect ${Spells.PAGES_TO_LEARN} pages of a spell book at item shrines to learn it.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spells.all.forEach { sk ->
            val known = sk.id in h.knownSpells
            val pages = s.spellPages[sk.id] ?: 0
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sk.element.emoji} ${sk.name}",
                    modifier = Modifier.weight(1f),
                    color = if (known) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (known) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (known) {
                    Text("⚡${sk.mpCost}", fontSize = 13.sp, color = MpBlue)
                } else {
                    Text("📜 $pages/${Spells.PAGES_TO_LEARN}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle("Journey")
        val st = s.stats
        Text("Quests completed: ${st.tasksCompleted}")
        Text("Battles won / lost: ${st.battlesWon} / ${st.battlesLost}")
        Text("Monsters tamed: ${st.monstersTamed}")
        Text("Shrines visited: ${st.shrinesVisited}")
        Text("Bosses defeated: ${st.bossesDefeated}")
        Text("Phone-free sessions: ${st.focusWins} won / ${st.focusFails} failed (${st.focusMinutes} min)")
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

/** Class choice (unlocked at level 5) and the current class's growing bonus. */
@Composable
private fun ClassSection(vm: GameViewModel) {
    val s = vm.state
    val h = s.hero
    var confirm by remember { mutableStateOf<HeroClass?>(null) }
    SectionTitle("Class")
    if (h.level < CLASS_UNLOCK_LEVEL) {
        GameCard {
            Text("🔒 Choose a class at level $CLASS_UNLOCK_LEVEL: ⚔️ Warrior, 🔮 Mage or 🗡️ Rogue.")
            Text(
                "Class bonuses start small and grow every level.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    if (h.heroClass != HeroClass.NONE) {
        GameCard(border = Gold) {
            Text("${h.heroClass.emoji} ${h.heroClass.label}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(classEffects(h.heroClass, h.classBonus), fontSize = 13.sp)
            Text(
                "Bonus grows 1.5% per level, up to 40% at level 30.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Text("Choose your path! The first choice is free.", color = Gold, fontSize = 13.sp)
    }
    // Preview what each class would do at the current level.
    val previewBonus = (3 + (h.level - CLASS_UNLOCK_LEVEL) * 1.5).coerceIn(0.0, 40.0)
    HeroClass.entries.filter { it != HeroClass.NONE && it != h.heroClass }.forEach { cls ->
        GameCard(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cls.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(cls.label, fontWeight = FontWeight.SemiBold)
                    Text(classEffects(cls, previewBonus), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SmallButton(
                    if (h.heroClass == HeroClass.NONE) "Choose" else "Switch",
                    { confirm = cls },
                )
            }
        }
    }
    if (h.heroClass != HeroClass.NONE) {
        Text(
            "Switching class costs $CLASS_CHANGE_COST gold.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    confirm?.let { cls ->
        val cost = if (h.heroClass == HeroClass.NONE) 0 else CLASS_CHANGE_COST
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Become a ${cls.label}?") },
            text = { Text(classEffects(cls, previewBonus) + if (cost > 0) "\n\nCosts $cost gold." else "") },
            confirmButton = {
                TextButton(onClick = { vm.chooseClass(cls); confirm = null }, enabled = s.gold >= cost) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun classEffects(cls: HeroClass, bonus: Double): String {
    val b = "%.1f".format(bonus)
    val half = "%.1f".format(bonus / 2)
    return when (cls) {
        HeroClass.NONE -> ""
        HeroClass.WARRIOR -> "+$b% Attack, +$b% Speed, −$half% Magic"
        HeroClass.MAGE -> "+$b% Magic, +$b% Defense, −$half% Attack"
        HeroClass.ROGUE -> "+$b% Speed, $half% chance to dodge attacks"
    }
}
