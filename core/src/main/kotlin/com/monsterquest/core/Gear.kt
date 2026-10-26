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

/** A weapon. Better rarity and level mean more power and more effects. */
@Serializable
data class Gear(
    val id: String,
    val type: WeaponType,
    val rarity: GearRarity,
    val level: Int,
    val element: Element = Element.NEUTRAL,
    val effects: List<GearEffect> = emptyList(),
) {
    /** Raw weapon power before its type splits it between Attack and Magic. */
    val power: Int get() = ((6 + level * 0.8) * rarity.multiplier).roundToInt()

    fun matches(cls: HeroClass) = cls in type.classes

    private fun classMultiplier(cls: HeroClass) = if (matches(cls)) 1 + CLASS_MATCH_BONUS else 1.0

    fun attackBonus(cls: HeroClass): Int = (power * type.attackShare * classMultiplier(cls)).roundToInt()
    fun magicBonus(cls: HeroClass): Int = (power * type.magicShare * classMultiplier(cls)).roundToInt()

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
            return "$adjective${type.label}"
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
) {
    val power: Int get() = ((5 + level * 0.7) * rarity.multiplier).roundToInt()

    fun matches(cls: HeroClass) = cls in type.classes

    private fun classMultiplier(cls: HeroClass) = if (matches(cls)) 1 + CLASS_MATCH_BONUS else 1.0

    fun defenseBonus(cls: HeroClass): Int = (power * type.defenseShare * classMultiplier(cls)).roundToInt()
    fun magicBonus(cls: HeroClass): Int = (power * type.magicShare * classMultiplier(cls)).roundToInt()
    fun attackBonus(cls: HeroClass): Int = (power * type.attackShare * classMultiplier(cls)).roundToInt()

    val name: String
        get() = when (type) {
            ArmorType.PLATE -> listOf("Iron Plate", "Knight's Plate", "Dragonscale Plate", "Aegis of Kings")
            ArmorType.LEATHER -> listOf("Leather Vest", "Shadow Leathers", "Nightstalker Garb", "Phantom Mantle")
            ArmorType.ROBE -> listOf("Cloth Robe", "Runed Robe", "Starweave Robe", "Archmage Vestments")
            ArmorType.HOOD -> listOf("Wool Hood", "Seer's Hood", "Moonveil Cowl", "Crown of Whispers")
            ArmorType.CLOAK -> listOf("Travel Cloak", "Elven Cloak", "Shadowweave Cloak", "Mantle of the Wind")
            ArmorType.PANTS -> listOf("Linen Pants", "Ranger Breeches", "Warden Greaves", "Titanguard Legplates")
            ArmorType.BOOTS -> listOf("Walking Boots", "Swiftfoot Boots", "Stormstride Boots", "Boots of the Far Road")
        }[rarity.ordinal]

    fun summary(cls: HeroClass): String = buildList {
        defenseBonus(cls).takeIf { it > 0 }?.let { add("+$it Defense") }
        attackBonus(cls).takeIf { it > 0 }?.let { add("+$it Attack") }
        magicBonus(cls).takeIf { it > 0 }?.let { add("+$it Magic") }
        if (type.speedPercent != 0) add("${if (type.speedPercent > 0) "+" else ""}${type.speedPercent}% Speed")
        if (type.evasionPercent > 0) add("${type.evasionPercent}% dodge")
    }.joinToString(" • ")
}

object Armory {
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
        return Armor(newId(rng), t, rarity, lvl, effects)
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
