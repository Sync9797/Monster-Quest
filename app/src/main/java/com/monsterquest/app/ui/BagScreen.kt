package com.monsterquest.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import com.monsterquest.core.WeaponType
import com.monsterquest.core.ItemKind
import com.monsterquest.core.HeroClass
import com.monsterquest.core.Gear
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
import com.monsterquest.core.Armor
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Item
import com.monsterquest.core.MAX_ITEMS_PER_BATTLE
import com.monsterquest.core.Spells

@Composable
fun BagScreen(vm: GameViewModel) {
    val s = vm.state
    var using by remember { mutableStateOf<Item?>(null) }
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
                    if (item.kind == ItemKind.HEAL_HP) SmallButton("Use", { using = item }, outlined = true)
                    if (item.kind == ItemKind.LURE) SmallButton("Use", vm::useLure, outlined = true)
                    TextButton(onClick = { vm.discard(item) }) { Text("Drop") }
                }
            }
        }

    }
    val gear: @Composable () -> Unit = {
        SectionTitle("Weapons")
        Text(
            "Any class can use any weapon; your class gets +20% from its own types " +
                "(Warrior: sword, broadsword • Rogue: dagger, sword • Mage: staff, wand).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.equippedGear?.let { w ->
            GearCard(w, s.hero.heroClass, equipped = true) {
                TextButton(onClick = vm::unequip) { Text("Remove") }
            }
        }
        if (s.gear.isEmpty() && s.equippedGear == null) {
            Text("No weapons yet. Item shrines, villains and boxes drop them, and parts can be forged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.gear.sortedWith(compareByDescending<Gear> { it.rarity }.thenByDescending { it.level }).forEach { w ->
            GearCard(w, s.hero.heroClass, equipped = false) {
                TextButton(onClick = { vm.dropWeapon(w.id) }) { Text("Drop") }
                SmallButton("Equip", { vm.equip(w.id) })
            }
        }
        SectionTitle("Armor")
        Text(
            "Plate suits Warriors, Leather suits Rogues and Robes suit Mages (+20%). Anyone can wear anything.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        s.equippedArmor?.let { a ->
            ArmorCard(a, s.hero.heroClass, equipped = true) {
                TextButton(onClick = vm::unequipArmor) { Text("Remove") }
            }
        }
        if (s.armor.isEmpty() && s.equippedArmor == null) {
            Text("No armor yet. Shrines, villain camps and loot boxes drop it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.armor.sortedWith(compareByDescending<Armor> { it.rarity }.thenByDescending { it.level }).forEach { a ->
            ArmorCard(a, s.hero.heroClass, equipped = false) {
                TextButton(onClick = { vm.dropArmor(a.id) }) { Text("Drop") }
                SmallButton("Wear", { vm.equipArmor(a.id) })
            }
        }
        SectionTitle("Weapon parts")
        WeaponType.entries.forEach { type ->
            val n = s.parts[type] ?: 0
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(type.icon(), Modifier.size(20.dp), SilverColor)
                Spacer(Modifier.width(8.dp))
                Text("${type.label} parts $n/${GameEngine.PARTS_TO_FORGE}", modifier = Modifier.weight(1f))
                SmallButton("Forge", { vm.forge(type) }, enabled = n >= GameEngine.PARTS_TO_FORGE && !s.bagFull, outlined = true)
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
    using?.let { item -> PotionTargetDialog(vm, item) { using = null } }
}

/** One weapon: rarity-colored name, level and type, stats for your class, and its effects. */
@Composable
private fun GearCard(w: Gear, cls: HeroClass, equipped: Boolean, actions: @Composable () -> Unit) {
    GameCard(Modifier.padding(vertical = 3.dp), border = if (equipped) Gold else w.rarity.color().copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(w.type.icon(), Modifier.size(30.dp), w.rarity.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${w.rarity.label} ${w.name}" + if (equipped) " (equipped)" else "", fontWeight = FontWeight.SemiBold, color = w.rarity.color())
                Text(
                    "Lv ${w.level} ${w.type.label}" + if (w.matches(cls)) " • Class bonus" else "",
                    fontSize = 12.sp,
                    color = if (w.matches(cls)) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(w.summary(cls), fontSize = 12.sp)
                w.effects.forEach { fx ->
                    Text("${fx.label}: ${fx.description}", fontSize = 11.sp, color = XpPurple)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
private fun ArmorCard(a: Armor, cls: HeroClass, equipped: Boolean, actions: @Composable () -> Unit) {
    GameCard(Modifier.padding(vertical = 3.dp), border = if (equipped) Gold else a.rarity.color().copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(a.type.icon(), Modifier.size(30.dp), a.rarity.color())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${a.rarity.label} ${a.name}" + if (equipped) " (worn)" else "", fontWeight = FontWeight.SemiBold, color = a.rarity.color())
                Text(
                    "Lv ${a.level} ${a.type.label} armor" + if (a.matches(cls)) " • Class bonus" else "",
                    fontSize = 12.sp,
                    color = if (a.matches(cls)) HpGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(a.summary(cls), fontSize = 12.sp)
                a.effects.forEach { fx ->
                    Text("${fx.label}: ${fx.description}", fontSize = 11.sp, color = XpPurple)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** Pick who drinks a healing potion outside battle. */
@Composable
fun PotionTargetDialog(vm: GameViewModel, item: Item, onDone: () -> Unit) {
    val s = vm.state
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Use ${item.label} on…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val heroHp = s.hero.maxHp - s.heroDamage
                TargetRow("You", "HP $heroHp/${s.hero.maxHp}", s.heroDamage > 0) {
                    vm.usePotion(item, com.monsterquest.core.Combatant.HERO_KEY)
                    onDone()
                }
                s.monsters.filter { !it.fainted }.forEach { m ->
                    TargetRow(m.displayName, "HP ${m.currentHp}/${m.stats.hp}", m.damage > 0) {
                        vm.usePotion(item, m.uid)
                        onDone()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
private fun TargetRow(name: String, hp: String, hurt: Boolean, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold)
            Text(hp, fontSize = 12.sp, color = if (hurt) HpYellow else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SmallButton(if (hurt) "Heal" else "Full HP", onPick, enabled = hurt)
    }
}
