package com.monsterquest.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Item
import com.monsterquest.core.MAX_ITEMS_PER_BATTLE
import com.monsterquest.core.Spells

@Composable
fun BagScreen(vm: GameViewModel) {
    val s = vm.state
    val bag: @Composable () -> Unit = {
        LootBoxSection(vm)
        SectionTitle("Bag ${s.bagCount}/${s.bagCapacity}")
        LabeledBar("Space", s.bagCount, s.bagCapacity, if (s.bagFull) HpRed else MpBlue)
        Text(
            "You can use up to $MAX_ITEMS_PER_BATTLE items per battle. Bag size grows with upgrades every 5 levels.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        val owned = Item.entries.filter { s.count(it) > 0 }
        if (owned.isEmpty()) Text("Your bag is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        owned.forEach { item ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${item.label} x${s.count(item)}", fontWeight = FontWeight.SemiBold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.discard(item) }) { Text("Drop") }
                }
            }
        }

    }
    val gear: @Composable () -> Unit = {
        SectionTitle("Weapons")
        s.equippedWeapon?.let { w ->
            GameCard(Modifier.padding(vertical = 3.dp), border = Gold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${w.label} (equipped)", fontWeight = FontWeight.SemiBold)
                        Text(w.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = vm::unequip) { Text("Remove") }
                }
            }
        }
        if (s.weapons.isEmpty() && s.equippedWeapon == null) {
            Text("No weapons yet. Item shrines 🎁 sometimes have one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.weapons.forEachIndexed { i, w ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.label, fontWeight = FontWeight.SemiBold)
                        Text("${w.rarity.label} • ${w.description}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.dropWeapon(i) }) { Text("Drop") }
                    SmallButton("Equip", { vm.equip(i) })
                }
            }
        }
        val pages = s.spellPages.filterValues { it > 0 }
        if (pages.isNotEmpty()) {
            SectionTitle("Spell pages")
            pages.forEach { (id, n) ->
                val sp = Spells.byId(id) ?: return@forEach
                Text(
                    "📜 ${sp.element.emoji} ${sp.name}: $n/${Spells.PAGES_TO_LEARN} pages",
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
    val shop: @Composable () -> Unit = {
        SectionTitle("Shop  •  🪙 ${s.gold}")
        Item.entries.filter { it.price > 0 }.forEach { item ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.buy(item) }, enabled = s.gold >= item.price && !s.bagFull) {
                        Text("🪙 ${item.price}")
                    }
                }
            }
        }
    }

    AdaptivePanes(
        compact = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                bag()
                gear()
                shop()
                Spacer(Modifier.height(24.dp))
            }
        },
        first = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
                bag()
                gear()
                Spacer(Modifier.height(24.dp))
            }
        },
        second = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 16.dp)) {
                shop()
                Spacer(Modifier.height(24.dp))
            }
        },
    )
}
