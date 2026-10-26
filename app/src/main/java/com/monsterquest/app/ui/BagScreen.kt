package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
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
import com.monsterquest.core.GameEngine
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
                    GIcon(item.icon(), Modifier.size(28.dp), item.color())
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
                    GIcon(w.icon(), Modifier.size(28.dp), w.color())
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
            Text("No weapons yet. Item shrines sometimes have one, and parts can be forged into one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.weapons.forEachIndexed { i, w ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(w.icon(), Modifier.size(28.dp), w.color())
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
        GameCard(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(GameIcon.PARTS, Modifier.size(28.dp), SilverColor)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Weapon parts ${s.weaponParts}/${GameEngine.PARTS_TO_FORGE}", fontWeight = FontWeight.SemiBold)
                    Text("From Level 2+ loot boxes. Forge ${GameEngine.PARTS_TO_FORGE} into a weapon.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SmallButton("Forge", vm::forge, enabled = s.weaponParts >= GameEngine.PARTS_TO_FORGE && !s.bagFull)
            }
        }
        val pages = s.spellPages.filterValues { it > 0 }
        if (pages.isNotEmpty()) {
            SectionTitle("Spell sheets")
            pages.forEach { (id, n) ->
                val sp = Spells.byId(id) ?: return@forEach
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(GameIcon.PAGE, Modifier.size(18.dp), Gold)
                    Spacer(Modifier.width(6.dp))
                    GIcon(sp.element.icon(), Modifier.size(16.dp), sp.element.color())
                    Spacer(Modifier.width(6.dp))
                    Text("${sp.name}: $n/${Spells.PAGES_TO_LEARN} pages")
                }
            }
        }
    }
    val shop: @Composable () -> Unit = {
        SectionTitle("Shop  •  ${s.gold} gold")
        Item.entries.filter { it.price > 0 }.forEach { item ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(item.icon(), Modifier.size(28.dp), item.color())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.buy(item) }, enabled = s.gold >= item.price && !s.bagFull) {
                        IconText(GameIcon.COIN, "${item.price}")
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
