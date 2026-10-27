package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Weapon types. Any class can use any weapon, but a class that matches the
 * type gets [CLASS_MATCH_BONUS] more out of it.
 */
@Serializable
enum class WeaponType(
    val label: String,
    val classes: Set<HeroClass>,
    /** Share of the weapon's power added to Attack / Magic. */
    val attackShare: Double,
    val magicShare: Double,
    /** Speed change in percent. */
    val speedPercent: Int,
    /** Basic-attack hits per turn. */
    val hits: Int,
    /** Energy restored at the start of each of the hero's turns. */
    val energyPerTurn: Int,
    val description: String,
) {
    SWORD("Sword", setOf(HeroClass.WARRIOR, HeroClass.ROGUE), 1.0, 0.2, 0, 1, 0, "Balanced blade"),
    BROADSWORD("Broadsword", setOf(HeroClass.WARRIOR), 1.6, 0.0, -15, 1, 0, "Heavy damage, but slows you down"),
    DAGGER("Dagger", setOf(HeroClass.ROGUE), 0.6, 0.0, 5, 2, 0, "Lighter hits that strike twice"),
    STAFF("Staff", setOf(HeroClass.MAGE), 0.4, 1.4, 0, 1, 0, "Weak swings, much stronger magic"),
    WAND("Wand", setOf(HeroClass.MAGE), 0.3, 0.8, 0, 1, 3, "Weak hits; restores Energy every turn"),
}

const val CLASS_MATCH_BONUS = 0.2

@Serializable
enum class GearRarity(val label: String, val multiplier: Double, val effectCount: Int) {
    NORMAL("Normal", 1.0, 0),
    RARE("Rare", 1.3, 1),
    EPIC("Epic", 1.65, 2),
    LEGENDARY("Legendary", 2.1, 3),
}

/** Secondary effects on Rare and better weapons. */
@Serializable
enum class GearEffect(val label: String, val description: String) {
    KEEN("Keen", "+10% critical-hit chance"),
    VAMPIRIC("Vampiric", "Heals you for 15% of the damage you deal"),
    SWIFT("Swift", "+10% Speed"),
    GUARDED("Guarded", "+10% Defense"),
    ARCANE("Arcane", "+2 Energy each turn"),
    SEARING("Searing", "15% chance to burn on hit"),
    CRACKLING("Crackling", "15% chance to shock on hit"),
    VENOMOUS("Venomous", "15% chance to poison on hit"),
    LUCKY("Lucky", "+10% taming odds"),
    MIGHTY("Mighty", "+10% Attack"),
    FOCUSED("Focused", "+10% Magic"),
}

/**
 * What gear is made of. Better materials add more to every bonus. Low-level
 * heroes mostly find wooden weapons and leather armor, some bronze, and only
 * very rarely anything better; the odds improve with level but stay capped.
 */
@Serializable
enum class Material(val weaponLabel: String, val armorLabel: String, val multiplier: Double) {
    BASIC("Wooden", "Leather", 1.0),
    BRONZE("Bronze", "Bronze", 1.15),
    IRON("Iron", "Iron", 1.3),
    GOLD("Golden", "Gold", 1.5),
    DIAMOND("Diamond", "Diamond", 1.8),
}

/**
 * A curse on gear from the weekend Gold shrine: a strong power, but until the
 * curse is lifted the gear hurts its owner and a weapon hits 25% softer.
 * Using it lifts the curse ([CURSE_PER_USE]% a hit); then it becomes a normal
 * Legendary, keeps the power and gains new effects.
 */
@Serializable
enum class Curse(val label: String, val weaponText: String, val armorText: String) {
    FROST("Frost", "Hits give the foe frostbite", "Physical attackers get frostbite"),
    LIFE_DRAIN("Life Drain", "Heals you for 30% of the damage you deal", "Steals 10% of the damage when a physical attack hits you"),
    FLAME("Flame", "Hits set the foe on fire", "Physical attackers get burned"),
}

/** Curse lifted per use (each weapon hit, or each physical hit taken in cursed armor). */
const val CURSE_PER_USE = 2
/** While cursed, weapon damage is cut by this much (percent). */
const val CURSED_DAMAGE_CUT = 25
/** While cursed, the gear drains this share of its owner's max HP each of their turns (percent). */
const val CURSE_DRAIN_PCT = 3

/** A weapon. Better rarity and level mean more power and more effects. */
@Serializable
data class Gear(
    val id: String,
    val type: WeaponType,
    val rarity: GearRarity,
    val level: Int,
    val element: Element = Element.NEUTRAL,
    val effects: List<GearEffect> = emptyList(),
    /** Battles fought with this weapon (up to [PROFICIENCY_BATTLES]); each weapon is learned on its own. */
    val proficiency: Int = 0,
    val material: Material = Material.BASIC,
    /** A cursed weapon's power (it stays after the curse is lifted). */
    val curse: Curse? = null,
    /** How much of the curse is left, 0-100 (0 = lifted). */
    val cursePct: Int = 0,
) {
    /** Raw weapon power before its type splits it between Attack and Magic. */
    val power: Int get() = ((6 + level * 0.8) * rarity.multiplier * material.multiplier).roundToInt()

    val cursed: Boolean get() = curse != null && cursePct > 0

    fun matches(cls: HeroClass) = cls in type.classes

    /** Outside your class (Adventurers have no class weapons, so nothing is foreign to them). */
    fun foreign(cls: HeroClass) = cls != HeroClass.NONE && !matches(cls)

    val mastered: Boolean get() = proficiency >= PROFICIENCY_BATTLES

    private fun classMultiplier(cls: HeroClass) = if (matches(cls)) 1 + CLASS_MATCH_BONUS else 1.0

    fun attackBonus(cls: HeroClass): Int = (power * type.attackShare * classMultiplier(cls)).roundToInt()
    fun magicBonus(cls: HeroClass): Int = (power * type.magicShare * classMultiplier(cls)).roundToInt()

    /**
     * Added to Attack, Magic, Defense and Speed: +2/4/6/8 (by rarity) for a
     * weapon of your class; the same amount off for one outside it, fading
     * as you get used to that weapon over [PROFICIENCY_BATTLES] battles.
     */
    fun classStats(cls: HeroClass, anyWeapon: Boolean = false): Int = when {
        matches(cls) || anyWeapon -> rarity.classStatBonus
        foreign(cls) -> -(rarity.classStatBonus * (PROFICIENCY_BATTLES - proficiency.coerceAtMost(PROFICIENCY_BATTLES)) + PROFICIENCY_BATTLES - 1) / PROFICIENCY_BATTLES
        else -> 0
    }

    /** One more battle with this weapon. */
    fun practiced(): Gear = if (proficiency >= PROFICIENCY_BATTLES) this else copy(proficiency = proficiency + 1)

    /** Gold the shop pays for it: rarer and higher-level weapons pay more. */
    val sellPrice: Int get() = gearSellPrice(rarity, level)

    val name: String
        get() {
            val adjective = when (element) {
                Element.FIRE -> "Flame "
                Element.ELECTRIC -> "Storm "
                Element.FLIGHT -> "Gale "
                Element.WATER -> "Tide "
                Element.GAS -> "Venom "
                Element.CORRUPT -> "Shadow "
                Element.EARTH -> "Thorn "
                Element.NEUTRAL -> ""
            }
            val cursedName = curse?.let { "${if (cursed) "Cursed " else ""}${it.label} " } ?: ""
            return "$cursedName$adjective${material.weaponLabel} ${type.label}"
        }

    /** One-line summary, e.g. "+14 Attack • +3 Magic • Fire attacks". */
    fun summary(cls: HeroClass): String = buildList {
        attackBonus(cls).takeIf { it > 0 }?.let { add("+$it Attack") }
        magicBonus(cls).takeIf { it > 0 }?.let { add("+$it Magic") }
        if (type.speedPercent != 0) add("${if (type.speedPercent > 0) "+" else ""}${type.speedPercent}% Speed")
        if (type.hits > 1) add("Hits ${type.hits}×")
        if (type.energyPerTurn > 0) add("+${type.energyPerTurn} Energy/turn")
        if (element != Element.NEUTRAL) add("${element.label} attacks")
    }.joinToString(" • ")
}

/** Sell value of a weapon or armor piece. */
fun gearSellPrice(rarity: GearRarity, level: Int): Int = listOf(15, 50, 140, 350)[rarity.ordinal] + level * (rarity.ordinal + 1) * 2

/** Gold to smelt a weapon, and the parts of its type it gives back (by rarity). */
const val SMELT_COST = 50
val GearRarity.smeltParts: Int get() = ordinal + 1

/** Where a piece of armor is worn. The hero wears one piece in each slot. */
@Serializable
enum class ArmorSlot(val label: String) { BODY("Body"), HEAD("Head"), BACK("Back"), LEGS("Legs"), FEET("Feet") }

/** Armor types. Like weapons, the matching class gets [CLASS_MATCH_BONUS] more out of it. */
@Serializable
enum class ArmorType(
    val label: String,
    val classes: Set<HeroClass>,
    /** Share of the armor's power added to Defense / Magic. */
    val defenseShare: Double,
    val magicShare: Double,
    val speedPercent: Int,
    /** Chance to dodge attacks, in percent. */
    val evasionPercent: Int,
    val description: String,
    val slot: ArmorSlot = ArmorSlot.BODY,
    /** Share of the armor's power added to Attack. */
    val attackShare: Double = 0.0,
    /** The extra buff every Rare or better piece of this kind comes with. */
    val signature: GearEffect = GearEffect.GUARDED,
) {
    PLATE("Plate", setOf(HeroClass.WARRIOR), 1.5, 0.0, -10, 0, "Heavy protection, but slows you down", signature = GearEffect.GUARDED),
    LEATHER("Leather", setOf(HeroClass.ROGUE), 0.9, 0.0, 5, 5, "Light and quick; harder to hit", signature = GearEffect.SWIFT),
    ROBE("Robe", setOf(HeroClass.MAGE), 0.5, 0.7, 0, 0, "Little protection, but adds Magic", signature = GearEffect.FOCUSED),
    HOOD(
        "Hood", setOf(HeroClass.MAGE, HeroClass.ROGUE), 0.3, 0.4, 0, 2, "Sharpens your focus: Magic and a little dodge",
        ArmorSlot.HEAD, signature = GearEffect.FOCUSED,
    ),
    CLOAK(
        "Cloak", setOf(HeroClass.ROGUE, HeroClass.NONE), 0.4, 0.1, 0, 4, "Billows and hides you: harder to hit",
        ArmorSlot.BACK, signature = GearEffect.SWIFT,
    ),
    PANTS(
        "Pants", setOf(HeroClass.WARRIOR, HeroClass.NONE), 0.7, 0.0, 0, 0, "Sturdy legs: Defense and some Attack",
        ArmorSlot.LEGS, attackShare = 0.3, signature = GearEffect.MIGHTY,
    ),
    BOOTS(
        "Boots", HeroClass.entries.toSet(), 0.3, 0.0, 12, 0, "Quick feet: more Speed",
        ArmorSlot.FEET, signature = GearEffect.SWIFT,
    ),
}

/** Effects armor can roll (the on-hit ones only make sense on weapons). */
val ARMOR_EFFECTS = listOf(
    GearEffect.GUARDED, GearEffect.SWIFT, GearEffect.ARCANE, GearEffect.LUCKY, GearEffect.VAMPIRIC, GearEffect.MIGHTY, GearEffect.FOCUSED,
)

@Serializable
data class Armor(
    val id: String,
    val type: ArmorType,
    val rarity: GearRarity,
    val level: Int,
    val effects: List<GearEffect> = emptyList(),
    val material: Material = Material.BASIC,
    val curse: Curse? = null,
    val cursePct: Int = 0,
) {
    val power: Int get() = ((5 + level * 0.7) * rarity.multiplier * material.multiplier).roundToInt()

    val cursed: Boolean get() = curse != null && cursePct > 0

    fun matches(cls: HeroClass) = cls in type.classes

    private fun classMultiplier(cls: HeroClass) = if (matches(cls)) 1 + CLASS_MATCH_BONUS else 1.0

    fun defenseBonus(cls: HeroClass): Int = (power * type.defenseShare * classMultiplier(cls)).roundToInt()
    fun magicBonus(cls: HeroClass): Int = (power * type.magicShare * classMultiplier(cls)).roundToInt()
    fun attackBonus(cls: HeroClass): Int = (power * type.attackShare * classMultiplier(cls)).roundToInt()

    val sellPrice: Int get() = gearSellPrice(rarity, level)

    val name: String
        get() {
            val noun = when (type) {
                ArmorType.PLATE -> "Plate"
                ArmorType.LEATHER -> "Jerkin"
                ArmorType.ROBE -> "Robe"
                ArmorType.HOOD -> "Hood"
                ArmorType.CLOAK -> "Cloak"
                ArmorType.PANTS -> "Greaves"
                ArmorType.BOOTS -> "Boots"
            }
            val cursedName = curse?.let { "${if (cursed) "Cursed " else ""}${it.label} " } ?: ""
            return "$cursedName${material.armorLabel} $noun"
        }

    fun summary(cls: HeroClass): String = buildList {
        defenseBonus(cls).takeIf { it > 0 }?.let { add("+$it Defense") }
        attackBonus(cls).takeIf { it > 0 }?.let { add("+$it Attack") }
        magicBonus(cls).takeIf { it > 0 }?.let { add("+$it Magic") }
        if (type.speedPercent != 0) add("${if (type.speedPercent > 0) "+" else ""}${type.speedPercent}% Speed")
        if (type.evasionPercent > 0) add("${type.evasionPercent}% dodge")
    }.joinToString(" • ")
}

/** Five scrap pieces of one kind forge a new piece of that kind (weapons and armor alike). */
const val PARTS_TO_FORGE = 5

object Armory {
    /**
     * A material for gear found around [lvl]: mostly the basic kind early on,
     * better ones getting likelier with level but capped, so even at level 100
     * diamond stays rare (about 1 in 20) and at level 10 it's about 1 in 800.
     */
    fun rollMaterial(lvl: Int, rng: Random, luck: Int = 0): Material {
        val boost = 1 + luck / 20.0
        val weights = listOf(
            Material.BASIC to (100.0 - lvl).coerceAtLeast(25.0),
            Material.BRONZE to ((8 + lvl * 1.5) * boost).coerceAtMost(90.0),
            Material.IRON to ((lvl - 8) * 1.2 * boost).coerceIn(0.6, 60.0),
            Material.GOLD to ((0.5 + (lvl - 20).coerceAtLeast(0) * 0.5) * boost).coerceAtMost(30.0),
            Material.DIAMOND to ((0.15 + (lvl - 35).coerceAtLeast(0) * 0.25) * boost).coerceAtMost(10.0),
        )
        var roll = rng.nextDouble() * weights.sumOf { it.second }
        for ((m, w) in weights) {
            roll -= w
            if (roll < 0) return m
        }
        return Material.BASIC
    }

    fun randomArmorPartType(rng: Random): ArmorType = ArmorType.entries.random(rng)

    /** A cursed weapon (Legendary) for the weekend Gold shrine. */
    fun cursedWeapon(heroLevel: Int, rng: Random): Gear =
        roll(heroLevel, rng, minimum = GearRarity.LEGENDARY, luck = 30).copy(curse = Curse.entries.random(rng), cursePct = 100)

    /** A cursed armor piece (Legendary) for the weekend Gold shrine. */
    fun cursedArmor(heroLevel: Int, rng: Random): Armor =
        rollArmor(heroLevel, rng, minimum = GearRarity.LEGENDARY, luck = 30).copy(curse = Curse.entries.random(rng), cursePct = 100)

    private fun rollRarity(lvl: Int, rng: Random, luck: Int, minimum: GearRarity): GearRarity {
        val weights = listOf(
            GearRarity.NORMAL to (60 - lvl).coerceAtLeast(15),
            GearRarity.RARE to 25 + lvl + luck,
            GearRarity.EPIC to 4 + lvl / 2 + luck,
            GearRarity.LEGENDARY to ((lvl - 8) / 2).coerceAtLeast(0) + luck / 2,
        ).filter { it.first >= minimum }
        var roll = rng.nextInt(weights.sumOf { it.second }.coerceAtLeast(1))
        for ((r, w) in weights) {
            roll -= w
            if (roll < 0) return r
        }
        return weights.first().first
    }

    /** Random armor around [heroLevel]; same rarity odds as weapons. */
    fun rollArmor(heroLevel: Int, rng: Random, type: ArmorType? = null, luck: Int = 0, minimum: GearRarity = GearRarity.NORMAL): Armor {
        val lvl = (heroLevel + rng.nextInt(-1, 2)).coerceAtLeast(1)
        val rarity = rollRarity(lvl, rng, luck, minimum)
        val t = type ?: ArmorType.entries.random(rng)
        // Rare and better always carry the piece's signature buff, then random extras.
        val effects = if (rarity.effectCount == 0) emptyList()
        else listOf(t.signature) + (ARMOR_EFFECTS - t.signature).shuffled(rng).take(rarity.effectCount - 1)
        return Armor(newId(rng), t, rarity, lvl, effects, rollMaterial(lvl, rng, luck))
    }

    /**
     * A random weapon around [heroLevel]. Rarer weapons get likelier as you
     * level up; [luck] shifts the odds further (forging, bosses, boxes).
     */
    fun roll(heroLevel: Int, rng: Random, type: WeaponType? = null, luck: Int = 0, minimum: GearRarity = GearRarity.NORMAL): Gear {
        val lvl = (heroLevel + rng.nextInt(-1, 2)).coerceAtLeast(1)
        val rarity = rollRarity(lvl, rng, luck, minimum)
        val element = if (rng.nextDouble() < 0.4) Element.typed.random(rng) else Element.NEUTRAL
        val pool = GearEffect.entries.shuffled(rng)
        return Gear(
            id = newId(rng),
            type = type ?: WeaponType.entries.random(rng),
            rarity = rarity,
            level = lvl,
            element = element,
            effects = pool.take(rarity.effectCount),
            material = rollMaterial(lvl, rng, luck),
        )
    }

    fun randomPartType(rng: Random): WeaponType = WeaponType.entries.random(rng)

    /** Converts weapons from older saves to the closest new weapon. */
    fun fromLegacy(w: Weapon, heroLevel: Int, rng: Random): Gear {
        val (type, rarity) = when (w) {
            Weapon.WOODEN_SWORD -> WeaponType.SWORD to GearRarity.NORMAL
            Weapon.APPRENTICE_STAFF -> WeaponType.STAFF to GearRarity.NORMAL
            Weapon.IRON_SWORD -> WeaponType.SWORD to GearRarity.RARE
            Weapon.FLAME_BLADE -> WeaponType.SWORD to GearRarity.RARE
            Weapon.STORM_SPEAR -> WeaponType.BROADSWORD to GearRarity.RARE
            Weapon.WIND_BOW -> WeaponType.DAGGER to GearRarity.RARE
            Weapon.TIDE_TRIDENT -> WeaponType.STAFF to GearRarity.RARE
            Weapon.VENOM_DAGGER -> WeaponType.DAGGER to GearRarity.RARE
            Weapon.SHADOW_SCYTHE -> WeaponType.BROADSWORD to GearRarity.EPIC
            Weapon.ARCANE_STAFF -> WeaponType.STAFF to GearRarity.EPIC
        }
        return Gear(newId(rng), type, rarity, heroLevel, w.element, GearEffect.entries.shuffled(rng).take(rarity.effectCount))
    }
}
