package com.monsterquest.app.ui

import com.monsterquest.core.GameEngine
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.core.ArmorSlot
import com.monsterquest.app.ui.art.HeroArt
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
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
import com.monsterquest.core.MAX_PARTY_SIZE
import com.monsterquest.core.PAID_UPGRADE_AMOUNT
import com.monsterquest.core.UPGRADE_LEVEL_INTERVAL
import com.monsterquest.core.Gear
import com.monsterquest.core.Hero
import com.monsterquest.core.HERO_SKILL_LEVELS
import com.monsterquest.core.MAX_SKILL_RANK
import com.monsterquest.core.PROFICIENCY_BATTLES
import com.monsterquest.core.SKILL_CHANGE_COST
import com.monsterquest.core.SKILL_RANK_LEVELS
import com.monsterquest.core.SUBCLASS_CHANGE_COST
import com.monsterquest.core.SUBCLASS_UNLOCK_LEVEL
import com.monsterquest.core.Subclass
import com.monsterquest.core.TEAM_RANGE_RANK
import com.monsterquest.core.Trait
import com.monsterquest.core.skillRank

@Composable
fun HeroScreen(vm: GameViewModel) {
    val s = vm.state
    val h = s.hero
    var renaming by remember { mutableStateOf(false) }

    val profile: @Composable () -> Unit = {
        GameCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroArt(h.heroClass, Modifier.size(110.dp), outfit = HeroOutfit.of(s.equippedGear, s.equippedArmor, s.equippedPieces))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Level ${h.level} ${h.activeSubclass?.label ?: h.heroClass.label}", color = XpPurple)
                }
                TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(4.dp)) {
                    GIcon(GameIcon.EDIT, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            LabeledBar("HP", h.maxHp - s.heroDamage, h.maxHp, hpColor((h.maxHp - s.heroDamage).toFloat() / h.maxHp))
            Spacer(Modifier.height(4.dp))
            // Energy doesn't refill after battle: it comes back over time, faster by walking.
            LabeledBar("Energy", s.heroEnergy, h.maxMp, MpBlue)
            Spacer(Modifier.height(4.dp))
            LabeledBar("XP", h.xp, h.xpToNext, XpPurple)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(GameIcon.WALK, "%,d steps today".format(s.stepsToday), tint = HpGreen, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "+${GameEngine.XP_PER_REWARD} XP/HP in ${GameEngine.STEPS_PER_REWARD - s.stepBank}",
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        SubclassSection(vm)
        SkillsSection(vm)

        SectionTitle(if (h.unspentPoints > 0) "Attributes: ${h.unspentPoints} point(s) to spend!" else "Attributes")
        HeroAttribute.entries.forEach { a ->
            GameCard(Modifier.padding(vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(a.icon(), Modifier.size(24.dp), Gold)
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
        // Bag and monster storage upgrades (free ones every few levels, and bought ones) live in the Bag's shop.
        if (s.pendingUpgrades > 0) {
            GameCard(border = Gold) {
                Text("You have ${s.pendingUpgrades} free storage upgrade(s) to claim in the Bag's shop.", color = Gold, fontSize = 13.sp)
            }
        }

        SectionTitle("Hunt")
        GameCard {
            Text("Spend a hunt token to find a wild monster wherever you are.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::hunt, enabled = s.huntTokens > 0, modifier = Modifier.fillMaxWidth()) {
                IconText(GameIcon.TARGET, "Hunt (${s.huntTokens} tokens)")
            }
        }

        SectionTitle("Equipment")
        // Each slot is a dropdown: tap for full stats and to swap in something from the bag.
        val w = s.equippedGear
        EquipSlot(
            label = "Weapon",
            icon = w?.type?.icon() ?: GameIcon.FIST,
            tint = w?.rarity?.color(),
            title = w?.let { "${it.rarity.label} ${it.name} (Lv ${it.level})" } ?: "Bare-handed",
            detail = w?.let { it.summary(h.heroClass) } ?: "Find weapons at shrines and villain camps, or forge them from parts.",
            classNote = w?.let { weaponClassNote(it, h) },
            effects = w?.effects.orEmpty().map { it.label to it.description },
            curse = w?.curse?.let { CurseInfo(it.label, it.weaponText, w.cursePct, weapon = true) },
            onRemove = if (w != null) vm::unequip else null,
            options = s.gear.sortedWith(compareByDescending<Gear> { it.rarity }.thenByDescending { it.level }).map { g ->
                SwapOption(g.type.icon(), g.rarity.color(), "${g.rarity.label} ${g.name} (Lv ${g.level})", g.summary(h.heroClass)) { vm.equip(g.id) }
            },
        )
        val klepto = h.rankOf(Trait.KLEPTOMANIAC)
        if (klepto > 0) {
            val o = s.offhandGear
            EquipSlot(
                label = "Off hand (${Trait.KLEPTOMANIAC.offhandPct(klepto)}%)",
                icon = o?.type?.icon() ?: GameIcon.FIST,
                tint = o?.rarity?.color(),
                title = o?.let { "${it.rarity.label} ${it.name} (Lv ${it.level})" } ?: "Empty",
                detail = o?.let { it.summary(h.heroClass) } ?: "Kleptomaniac: carry a second weapon.",
                classNote = o?.let { weaponClassNote(it, h) },
                effects = if (klepto >= 3) o?.effects.orEmpty().map { it.label to it.description } else emptyList(),
                curse = null,
                onRemove = if (o != null) vm::unequipOffhand else null,
                options = s.gear.map { g ->
                    SwapOption(g.type.icon(), g.rarity.color(), "${g.rarity.label} ${g.name} (Lv ${g.level})", g.summary(h.heroClass)) { vm.equipOffhand(g.id) }
                },
            )
        }
        // Head first, then body, back, legs and feet: top to toe.
        listOf(
            ArmorSlot.HEAD to "a hood",
            ArmorSlot.BODY to "body armor",
            ArmorSlot.BACK to "a cloak",
            ArmorSlot.LEGS to "greaves",
            ArmorSlot.FEET to "boots",
        ).forEach { (slot, what) ->
            val p = s.worn(slot)
            EquipSlot(
                label = slot.label,
                icon = p?.type?.icon() ?: GameIcon.HERO,
                tint = p?.rarity?.color(),
                title = p?.let { "${it.rarity.label} ${it.name} (Lv ${it.level})" } ?: if (slot == ArmorSlot.BODY) "Traveler's clothes" else "Nothing",
                detail = p?.let { it.summary(h.heroClass) } ?: "Find $what at shrines, villain camps and loot boxes, or forge it from scrap.",
                classNote = if (p?.matches(h.heroClass) == true) "Class bonus active (+20%)" to HpGreen else null,
                effects = p?.effects.orEmpty().map { it.label to it.description },
                curse = p?.curse?.let { CurseInfo(it.label, it.armorText, p.cursePct, weapon = false) },
                onRemove = if (p != null) ({ vm.unequipArmor(slot) }) else null,
                options = s.armor.filter { it.type.slot == slot }.sortedWith(compareByDescending<com.monsterquest.core.Armor> { it.rarity }.thenByDescending { it.level }).map { a ->
                    SwapOption(a.type.icon(), a.rarity.color(), "${a.rarity.label} ${a.name} (Lv ${a.level})", a.summary(h.heroClass)) { vm.equipArmor(a.id) }
                },
            )
        }
        Text(
            "A weapon of your class adds +2/4/6/8 to all stats (by rarity). One outside your class takes that much off " +
                "until you've fought $PROFICIENCY_BATTLES battles with that weapon. Plate suits Warriors, Leather suits Rogues " +
                "and Robes suit Mages (+20%). Hoods suit Mages and Rogues, cloaks Rogues, greaves Warriors and boots anyone. " +
                "Better materials (wood or leather, bronze, iron, gold, diamond) add more to every bonus. Tap a slot to swap.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle("Magic")
        Text(
            "People can't breathe fire like monsters can, so heroes study monster magic and copy it. " +
                "Your spells start a little weaker than monster moves and grow to full strength by level ${Spells.MASTERY_LEVEL} " +
                "(now ${(Spells.mastery(h.level) * 100).toInt()}%). Learn a spell with ${Spells.PAGES_TO_LEARN} pages from item shrines, " +
                "then rank it up to ${Spells.MAX_RANK} with more pages and gold.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spells.all.forEach { base ->
            val rank = h.spellRank(base.id)
            val known = rank > 0
            val sk = Spells.ranked(base, rank.coerceAtLeast(1), h.level)
            val pages = s.spellPages[base.id] ?: 0
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(sk.spellIcon(), Modifier.size(18.dp), if (known) sk.spellColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (known) "${sk.name}  ${"★".repeat(rank)}${"☆".repeat(Spells.MAX_RANK - rank)}" else sk.name,
                            color = if (known) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (known) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            if (known) "${sk.spellEffect()} • ${sk.mpCost} energy • ${sk.description}" else sk.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        !known -> IconText(GameIcon.PAGE, "$pages/${Spells.PAGES_TO_LEARN}", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp, fontSize = 13.sp)
                        rank >= Spells.MAX_RANK -> Text("Max", fontSize = 12.sp, color = Gold)
                        else -> {
                            val needPages = Spells.pagesToUpgrade(rank)
                            val needGold = Spells.goldToUpgrade(rank)
                            Column(horizontalAlignment = Alignment.End) {
                                SmallButton(
                                    "Rank ${rank + 1}",
                                    { vm.upgradeSpell(base.id) },
                                    enabled = pages >= needPages && s.gold >= needGold,
                                    outlined = true,
                                )
                                Text("$pages/$needPages pages • $needGold gold", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
            IconText(GameIcon.LOCK, "Choose a class at level $CLASS_UNLOCK_LEVEL", tint = Gold)
            Text("Warrior, Mage or Rogue, or stay a neutral Adventurer. Your choice comes with a Rare weapon.")
            Text(
                "Class bonuses start small and grow every level.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Your class is a dropdown: tap it to see the other classes and switch.
    var open by rememberSaveable { mutableStateOf(false) }
    if (h.classChosen) {
        GameCard(Modifier.clickable { open = !open }, border = Gold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(h.heroClass.icon(), h.heroClass.label, tint = Gold, bold = true, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Text(if (open) "Hide ▴" else "Change ▾", fontSize = 13.sp, color = Gold)
            }
            Text(classEffects(h.heroClass, h.classBonus), fontSize = 13.sp)
            if (h.heroClass != HeroClass.NONE) {
                Text(
                    "Bonus grows 1.5% per level, up to 40% at level 30.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Text("Choose your path! The first choice is free and comes with a Rare weapon.", color = Gold, fontSize = 13.sp)
    }
    // Preview what each class would do at the current level.
    val previewBonus = (3 + (h.level - CLASS_UNLOCK_LEVEL) * 1.5).coerceIn(0.0, 40.0)
    DropdownReveal(visible = open || !h.classChosen) {
    Column {
    HeroClass.entries.filter { !h.classChosen || it != h.heroClass }.forEach { cls ->
        GameCard(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroArt(cls, Modifier.size(56.dp), portrait = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(cls.label, fontWeight = FontWeight.SemiBold)
                    Text(classEffects(cls, previewBonus), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SmallButton(
                    if (!h.classChosen) "Choose" else "Switch",
                    { confirm = cls },
                )
            }
        }
    }
    if (h.classChosen) {
        Text(
            "Switching class costs $CLASS_CHANGE_COST gold.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
    }
    confirm?.let { cls ->
        val cost = if (!h.classChosen) 0 else CLASS_CHANGE_COST
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (cls == HeroClass.NONE) "Stay an Adventurer?" else "Become a ${cls.label}?") },
            text = { Text(classEffects(cls, previewBonus) + if (cost > 0) "\n\nCosts $cost gold." else "") },
            confirmButton = {
                TextButton(onClick = { vm.chooseClass(cls); confirm = null; open = false }, enabled = s.gold >= cost) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun classEffects(cls: HeroClass, bonus: Double): String {
    val b = "%.1f".format(bonus)
    val half = "%.1f".format(bonus / 2)
    return when (cls) {
        HeroClass.NONE -> "Neutral: no strengths and no weaknesses."
        HeroClass.WARRIOR -> "+$b% Attack, +$b% Speed, −$half% Magic"
        HeroClass.MAGE -> "+$b% Magic, +$b% Defense, −$half% Attack"
        HeroClass.ROGUE -> "+$b% Speed, $half% chance to dodge attacks"
    }
}

/** A cursed piece: its power, and how much of the curse is left. */
private data class CurseInfo(val label: String, val text: String, val pct: Int, val weapon: Boolean)

/** Something in the bag that could go in a slot. */
private data class SwapOption(
    val icon: GameIcon,
    val tint: androidx.compose.ui.graphics.Color,
    val title: String,
    val detail: String,
    val onEquip: () -> Unit,
)

/**
 * One equipment slot as a dropdown: the piece's name and stats, and its curse
 * bar if it's cursed. Tap to open its full details and swap in any piece from
 * the bag that fits the slot.
 */
@Composable
private fun EquipSlot(
    label: String,
    icon: GameIcon,
    tint: androidx.compose.ui.graphics.Color?,
    title: String,
    detail: String,
    classNote: Pair<String, androidx.compose.ui.graphics.Color>?,
    effects: List<Pair<String, String>>,
    curse: CurseInfo?,
    onRemove: (() -> Unit)?,
    options: List<SwapOption>,
) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    GameCard(Modifier.padding(vertical = 3.dp).clickable { open = !open }, border = tint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(icon, Modifier.size(30.dp), tint ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, fontWeight = FontWeight.SemiBold, color = tint ?: MaterialTheme.colorScheme.onSurface)
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (open) "▴" else if (options.isNotEmpty()) "${options.size} ▾" else "▾",
                fontSize = 14.sp,
                color = Gold,
            )
        }
        // The curse shows like a health bar under the piece, always.
        if (curse != null) {
            Spacer(Modifier.height(6.dp))
            if (curse.pct > 0) {
                LabeledBar("Curse", curse.pct, 100, XpPurple)
                Text(
                    "${curse.label}: ${curse.text}. While cursed: " +
                        (if (curse.weapon) "hits do ${com.monsterquest.core.CURSED_DAMAGE_CUT}% less, " else "") +
                        "you lose ${com.monsterquest.core.CURSE_DRAIN_PCT}% HP each turn. Each " +
                        (if (curse.weapon) "hit" else "hit you take") + " lifts ${com.monsterquest.core.CURSE_PER_USE}%.",
                    fontSize = 11.sp,
                    color = XpPurple,
                )
            } else {
                Text("${curse.label}: ${curse.text} (curse lifted)", fontSize = 12.sp, color = HpGreen)
            }
        }
        DropdownReveal(visible = open) {
            Column(Modifier.padding(top = 6.dp)) {
                effects.forEach { (name, text) -> Text("$name: $text", fontSize = 12.sp, color = Gold) }
                classNote?.let { (text, color) -> Text(text, fontSize = 12.sp, color = color) }
                if (onRemove != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SmallButton("Take off", onRemove, outlined = true) }
                }
                if (options.isEmpty()) {
                    Text("Nothing in your bag fits here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Swap with:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    options.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            GIcon(o.icon, Modifier.size(22.dp), o.tint)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.title, fontSize = 13.sp, color = o.tint, fontWeight = FontWeight.SemiBold)
                                Text(o.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SmallButton("Swap", o.onEquip)
                        }
                    }
                }
            }
        }
    }
}

/** How a weapon suits this hero: the class bonus, or the penalty and practice for an unfamiliar one. */
fun weaponClassNote(w: Gear, h: Hero): Pair<String, androidx.compose.ui.graphics.Color>? {
    val n = w.classStats(h.heroClass, h.perks.anyWeapon)
    return when {
        n > 0 && w.matches(h.heroClass) -> "Class bonus: +$n to all stats, +20% power" to HpGreen
        n > 0 -> "Jack of All Trades: +$n to all stats" to HpGreen
        n < 0 -> "Unfamiliar: $n to all stats • practice ${w.proficiency}/$PROFICIENCY_BATTLES battles" to HpYellow
        w.foreign(h.heroClass) -> "Mastered: no penalty" to MpBlue
        else -> null
    }
}

/** Subclass choice at level 15: three specialties for each class. */
@Composable
private fun SubclassSection(vm: GameViewModel) {
    val s = vm.state
    val h = s.hero
    if (!h.classChosen) return
    var confirm by remember { mutableStateOf<Subclass?>(null) }
    SectionTitle("Subclass")
    if (h.level < SUBCLASS_UNLOCK_LEVEL) {
        GameCard {
            IconText(GameIcon.LOCK, "Choose a subclass at level $SUBCLASS_UNLOCK_LEVEL", tint = Gold)
            Text(
                "Each class has three: " + Subclass.of(h.heroClass).joinToString { it.label } + ".",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val current = h.activeSubclass
    // Like the class: your subclass is a dropdown, tap it to see the others.
    var open by rememberSaveable { mutableStateOf(false) }
    if (current == null) {
        Text("Pick a specialty! The first pick is free.", color = Gold, fontSize = 13.sp)
    } else {
        GameCard(Modifier.padding(vertical = 3.dp).clickable { open = !open }, border = Gold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.label, fontWeight = FontWeight.SemiBold, color = Gold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (open) "Hide ▴" else "Change ▾", fontSize = 13.sp, color = Gold)
            }
            Text(current.summary, fontSize = 12.sp)
        }
    }
    DropdownReveal(visible = open || current == null) {
        Column {
            Subclass.of(h.heroClass).filter { it != current }.forEach { sub ->
                GameCard(Modifier.padding(vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.label, fontWeight = FontWeight.SemiBold)
                            Text(sub.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SmallButton(if (current == null) "Choose" else "Switch", { confirm = sub }, outlined = current != null)
                    }
                }
            }
            if (current != null) {
                Text("Switching subclass costs $SUBCLASS_CHANGE_COST gold.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    confirm?.let { sub ->
        val cost = if (current == null) 0 else SUBCLASS_CHANGE_COST
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Become a ${sub.label}?") },
            text = { Text(sub.summary + if (cost > 0) "\n\nCosts $cost gold." else "") },
            confirmButton = { TextButton(onClick = { vm.chooseSubclass(sub); confirm = null; open = false }, enabled = s.gold >= cost) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Skill slots (levels 30, 45 and 60); each skill ranks up every 10 levels. */
@Composable
private fun SkillsSection(vm: GameViewModel) {
    val s = vm.state
    val h = s.hero
    var picking by remember { mutableStateOf<Int?>(null) }
    SectionTitle("Skills")
    Text(
        "Passive skills. A slot opens at levels ${HERO_SKILL_LEVELS.joinToString()}, and each skill ranks up every " +
            "$SKILL_RANK_LEVELS levels after (up to rank $MAX_SKILL_RANK) for a bigger effect. From rank $TEAM_RANGE_RANK " +
            "some cover your whole team.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HERO_SKILL_LEVELS.forEachIndexed { slot, at ->
        val skill = h.skills.getOrNull(slot)?.takeIf { slot < h.skillSlots }
        val rank = skillRank(h.level, at)
        GameCard(Modifier.padding(vertical = 3.dp), border = if (skill != null) XpPurple else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        h.level < at -> IconText(GameIcon.LOCK, "Slot ${slot + 1}: opens at level $at", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        skill == null -> Text("Slot ${slot + 1}: choose a skill!", color = Gold, fontWeight = FontWeight.SemiBold)
                        else -> {
                            Text(
                                "${skill.label}  ${"★".repeat(rank)}${"☆".repeat(MAX_SKILL_RANK - rank)}",
                                fontWeight = FontWeight.SemiBold,
                                color = XpPurple,
                            )
                            Text(skill.describe(rank), fontSize = 12.sp)
                            if (rank < MAX_SKILL_RANK) {
                                Text(
                                    "Rank ${rank + 1} at level ${at + rank * SKILL_RANK_LEVELS}: ${skill.describe(rank + 1)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (h.level >= at) SmallButton(if (skill == null) "Choose" else "Swap", { picking = slot }, outlined = skill != null)
            }
        }
    }
    picking?.let { slot ->
        val rank = skillRank(h.level, HERO_SKILL_LEVELS[slot]).coerceAtLeast(1)
        val current = h.skills.getOrNull(slot)
        val cost = if (current == null) 0 else SKILL_CHANGE_COST
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(if (current == null) "Choose a skill" else "Swap ${current.label}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (cost > 0) Text("Swapping costs $cost gold.", color = Gold, fontSize = 13.sp)
                    Trait.heroSkills.filter { it !in h.skills }.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.label, fontWeight = FontWeight.SemiBold)
                                Text(t.describe(rank), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SmallButton("Pick", { vm.learnSkill(slot, t); picking = null }, enabled = s.gold >= cost)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        )
    }
}
