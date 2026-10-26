package com.monsterquest.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.monsterquest.core.Bestiary
import com.monsterquest.core.Element
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Item
import com.monsterquest.core.MAX_PARTY_SIZE
import com.monsterquest.core.Monster
import com.monsterquest.core.Rarity

fun rarityColor(r: Rarity) = when (r) {
    Rarity.COMMON -> androidx.compose.ui.graphics.Color(0xFFB0B0B0)
    Rarity.UNCOMMON -> HpGreen
    Rarity.RARE -> MpBlue
    Rarity.EPIC -> XpPurple
    Rarity.LEGENDARY -> Gold
}

@Composable
fun MonstersScreen(vm: GameViewModel) {
    val s = vm.state
    var expanded by remember { mutableStateOf<String?>(null) }
    var releasing by remember { mutableStateOf<Monster?>(null) }
    var renaming by remember { mutableStateOf<Monster?>(null) }

    // Party first, then by level.
    val sorted = s.monsters.sortedWith(compareBy<Monster> { it.uid !in s.party }.thenByDescending { it.level })

    // One column on phones; more side by side when unfolded.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Storage ${s.monsters.size}/${s.monsterCapacity}  •  Battle party ${s.party.size}/$MAX_PARTY_SIZE  •  🍖 ${s.count(Item.TREAT)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(sorted, key = { it.uid }) { m ->
            MonsterCard(
                m = m,
                inParty = m.uid in s.party,
                expanded = expanded == m.uid,
                treats = s.count(Item.TREAT),
                onClick = { expanded = if (expanded == m.uid) null else m.uid },
                onParty = { vm.toggleParty(m.uid) },
                onTreat = { vm.feedTreat(m.uid) },
                onRename = { renaming = m },
                onRelease = { releasing = m },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                SectionTitle("Type chart")
                TypeChart()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    releasing?.let { m ->
        AlertDialog(
            onDismissRequest = { releasing = null },
            title = { Text("Release ${m.displayName}?") },
            text = { Text("It will return to the wild forever. This frees one storage slot.") },
            confirmButton = { TextButton(onClick = { vm.release(m.uid); releasing = null }) { Text("Release") } },
            dismissButton = { TextButton(onClick = { releasing = null }) { Text("Keep") } },
        )
    }
    renaming?.let { m ->
        var name by remember(m.uid) { mutableStateOf(m.nickname ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Nickname") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    singleLine = true,
                    label = { Text(m.species.name) },
                )
            },
            confirmButton = { TextButton(onClick = { vm.rename(m.uid, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MonsterCard(
    m: Monster,
    inParty: Boolean,
    expanded: Boolean,
    treats: Int,
    onClick: () -> Unit,
    onParty: () -> Unit,
    onTreat: () -> Unit,
    onRename: () -> Unit,
    onRelease: () -> Unit,
) {
    val sp = m.species
    val st = m.stats
    GameCard(Modifier.padding(vertical = 4.dp).clickable(onClick = onClick), border = if (inParty) Gold else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sp.emoji, fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (inParty) Text("  ⚔️ Party", fontSize = 12.sp, color = Gold, maxLines = 1)
                }
                Text(
                    "Lv ${m.level} ${sp.name} • ${sp.element.emoji} ${sp.element.label}",
                    fontSize = 13.sp,
                )
                Row {
                    Text(sp.rarity.label, fontSize = 12.sp, color = rarityColor(sp.rarity))
                    if (sp.stages > 1) {
                        Text("  • Stage ${sp.stage}/${sp.stages}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                sp.evolveLevel?.let { lvl ->
                    if (m.level < lvl) {
                        Text("Evolves at Lv $lvl", fontSize = 12.sp, color = XpPurple)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "🎭 ${m.personality.label}: ${m.personality.description}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LabeledBar("XP", m.xp, m.xpToNext, XpPurple)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            StatPill("HP", st.hp.toString(), Modifier.weight(1f))
            StatPill("Atk", st.attack.toString(), Modifier.weight(1f))
            StatPill("Mag", st.magic.toString(), Modifier.weight(1f))
            StatPill("Def", st.defense.toString(), Modifier.weight(1f))
            StatPill("Spd", st.speed.toString(), Modifier.weight(1f))
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text("Special: ${sp.special.name} (power ${sp.special.power})", fontSize = 13.sp)
            val perfect = GameEngine.perfectStatCount(m)
            Text(
                "Potential: ${m.potential}%" + if (perfect > 0) "  •  " + "⭐".repeat(perfect) + " $perfect perfect stat${if (perfect == 1) "" else "s"}" else "",
                fontSize = 13.sp,
            )
            Text(
                "Strong vs ${sp.element.beats?.let { "${it.emoji} ${it.label}" } ?: "nothing"} • " +
                    "Weak to ${sp.element.weakTo?.let { "${it.emoji} ${it.label}" } ?: "nothing"}",
                fontSize = 13.sp,
            )
            sp.breed?.let { Text("Breed: $it Hound", fontSize = 13.sp) }
            val line = Bestiary.lineOf(sp)
            if (line.size > 1) {
                Text(
                    "Evolution: " + line.joinToString(" → ") { s ->
                        val tag = s.evolveLevel?.let { " (Lv $it)" } ?: ""
                        if (s.id == sp.id) "[${s.emoji} ${s.name}]$tag" else "${s.emoji} ${s.name}$tag"
                    },
                    fontSize = 13.sp,
                    color = XpPurple,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton(if (inParty) "Bench" else "To party", onParty, Modifier.weight(1f))
                SmallButton("🍖 +${GameEngine.treatXp(m)} XP", onTreat, Modifier.weight(1f), enabled = treats > 0, outlined = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallButton("Rename", onRename, Modifier.weight(1f), outlined = true)
                SmallButton("Release", onRelease, Modifier.weight(1f), outlined = true)
            }
        } else {
            Text(
                "Tap for options",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The rock-paper-scissors cycle: each type beats the next one. */
@Composable
fun TypeChart() {
    GameCard {
        Element.typed.forEach { t ->
            val b = t.beats ?: return@forEach
            Text("${t.emoji} ${t.label}  beats  ${b.emoji} ${b.label}", fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
        }
        Text(
            "Super effective hits deal ×1.5; hitting a type that beats yours deals ×0.67.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
