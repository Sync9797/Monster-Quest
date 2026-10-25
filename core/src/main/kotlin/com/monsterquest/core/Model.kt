package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Elements
// ---------------------------------------------------------------------------

@Serializable
enum class Element(val label: String, val emoji: String) {
    NEUTRAL("Neutral", "⚪"),
    FIRE("Fire", "🔥"),
    WATER("Water", "💧"),
    NATURE("Nature", "🌿"),
    EARTH("Earth", "⛰️"),
    AIR("Air", "🌪️"),
    LIGHT("Light", "✨"),
    DARK("Dark", "🌑");

    /** Damage multiplier when an attack of this element hits [defender]. */
    fun multiplierAgainst(defender: Element): Double = when {
        defender in strongAgainst() -> 1.5
        this in defender.strongAgainst() && this != defender -> 0.75
        else -> 1.0
    }

    fun strongAgainst(): Set<Element> = when (this) {
        FIRE -> setOf(NATURE, AIR)
        WATER -> setOf(FIRE, EARTH)
        NATURE -> setOf(WATER, EARTH)
        EARTH -> setOf(FIRE, AIR)
        AIR -> setOf(NATURE, WATER)
        LIGHT -> setOf(DARK)
        DARK -> setOf(LIGHT)
        NEUTRAL -> emptySet()
    }
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

@Serializable
enum class Stat(val label: String) {
    HP("HP"), ATTACK("Attack"), MAGIC("Magic"), DEFENSE("Defense"), SPEED("Speed")
}

@Serializable
data class StatBlock(
    val hp: Int = 0,
    val attack: Int = 0,
    val magic: Int = 0,
    val defense: Int = 0,
    val speed: Int = 0,
) {
    operator fun get(stat: Stat): Int = when (stat) {
        Stat.HP -> hp
        Stat.ATTACK -> attack
        Stat.MAGIC -> magic
        Stat.DEFENSE -> defense
        Stat.SPEED -> speed
    }

    companion object {
        fun random(rng: Random, max: Int) = StatBlock(
            hp = rng.nextInt(max + 1),
            attack = rng.nextInt(max + 1),
            magic = rng.nextInt(max + 1),
            defense = rng.nextInt(max + 1),
            speed = rng.nextInt(max + 1),
        )
    }
}

// ---------------------------------------------------------------------------
// Personalities: every monster rolls one, so two Greywolves can play very
// differently (e.g. a Ferocious wolf hits hard but folds quickly, a Stalwart
// one is a slow wall).
// ---------------------------------------------------------------------------

@Serializable
enum class Personality(
    val label: String,
    private val mods: Map<Stat, Double>,
) {
    BALANCED("Balanced", emptyMap()),
    BRAVE("Brave", mapOf(Stat.ATTACK to 1.2, Stat.SPEED to 0.85)),
    FEROCIOUS("Ferocious", mapOf(Stat.ATTACK to 1.25, Stat.DEFENSE to 0.85)),
    RECKLESS("Reckless", mapOf(Stat.ATTACK to 1.2, Stat.SPEED to 1.1, Stat.DEFENSE to 0.8, Stat.HP to 0.9)),
    STALWART("Stalwart", mapOf(Stat.DEFENSE to 1.25, Stat.SPEED to 0.85)),
    STURDY("Sturdy", mapOf(Stat.HP to 1.15, Stat.DEFENSE to 1.1, Stat.ATTACK to 0.85)),
    HARDY("Hardy", mapOf(Stat.HP to 1.25, Stat.SPEED to 0.85)),
    NIMBLE("Nimble", mapOf(Stat.SPEED to 1.25, Stat.DEFENSE to 0.85)),
    HASTY("Hasty", mapOf(Stat.SPEED to 1.2, Stat.HP to 0.9)),
    CUNNING("Cunning", mapOf(Stat.MAGIC to 1.25, Stat.ATTACK to 0.85)),
    MYSTIC("Mystic", mapOf(Stat.MAGIC to 1.2, Stat.DEFENSE to 0.9)),
    LAZY("Lazy", mapOf(Stat.HP to 1.2, Stat.DEFENSE to 1.05, Stat.SPEED to 0.8));

    fun modifier(stat: Stat): Double = mods[stat] ?: 1.0

    /** e.g. "High Attack, low Speed". */
    val description: String
        get() {
            if (mods.isEmpty()) return "No strengths or weaknesses"
            val ups = mods.filterValues { it > 1.0 }.keys.map { it.label }
            val downs = mods.filterValues { it < 1.0 }.keys.map { it.label }
            val parts = ups.map { "high $it" } + downs.map { "low $it" }
            return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
        }
}

// ---------------------------------------------------------------------------
// Skills
// ---------------------------------------------------------------------------

@Serializable
enum class SkillKind { PHYSICAL, MAGICAL, HEAL }

@Serializable
data class Skill(
    val id: String,
    val name: String,
    val element: Element,
    val kind: SkillKind,
    val power: Int,
    /** Energy (MP) cost for hero spells. Monster specials use a cooldown instead. */
    val mpCost: Int = 0,
    val unlockLevel: Int = 1,
)

object HeroSkills {
    val all = listOf(
        Skill("fire_bolt", "Fire Bolt", Element.FIRE, SkillKind.MAGICAL, 30, mpCost = 4, unlockLevel = 1),
        Skill("heal", "Heal", Element.LIGHT, SkillKind.HEAL, 30, mpCost = 5, unlockLevel = 1),
        Skill("frost_lance", "Frost Lance", Element.WATER, SkillKind.MAGICAL, 32, mpCost = 5, unlockLevel = 4),
        Skill("gale", "Gale", Element.AIR, SkillKind.MAGICAL, 34, mpCost = 6, unlockLevel = 7),
        Skill("stone_spike", "Stone Spike", Element.EARTH, SkillKind.MAGICAL, 36, mpCost = 6, unlockLevel = 10),
        Skill("vine_lash", "Vine Lash", Element.NATURE, SkillKind.MAGICAL, 38, mpCost = 7, unlockLevel = 12),
        Skill("holy_light", "Holy Light", Element.LIGHT, SkillKind.MAGICAL, 45, mpCost = 9, unlockLevel = 15),
        Skill("shadow_rend", "Shadow Rend", Element.DARK, SkillKind.MAGICAL, 50, mpCost = 10, unlockLevel = 20),
    )

    fun unlockedAt(level: Int) = all.filter { it.unlockLevel <= level }
    fun byId(id: String) = all.first { it.id == id }
}

// ---------------------------------------------------------------------------
// Species
// ---------------------------------------------------------------------------

@Serializable
enum class Rarity(val label: String, val spawnWeight: Int, val captureMultiplier: Double) {
    COMMON("Common", 60, 1.0),
    UNCOMMON("Uncommon", 25, 0.85),
    RARE("Rare", 11, 0.65),
    EPIC("Epic", 4, 0.45),
    LEGENDARY("Legendary", 0, 0.3),
}

@Serializable
data class Species(
    val id: String,
    val name: String,
    val emoji: String,
    val element: Element,
    val rarity: Rarity,
    val base: StatBlock,
    val special: Skill,
    /** Boss species only appear at boss shrines. */
    val bossOnly: Boolean = false,
)

object Bestiary {
    private fun sp(
        id: String, name: String, emoji: String, element: Element, rarity: Rarity,
        hp: Int, atk: Int, mag: Int, def: Int, spd: Int,
        special: String, kind: SkillKind, power: Int, bossOnly: Boolean = false,
    ) = Species(
        id, name, emoji, element, rarity, StatBlock(hp, atk, mag, def, spd),
        Skill("${id}_special", special, element, kind, power), bossOnly,
    )

    val all: List<Species> = listOf(
        // Common
        sp("emberpup", "Emberpup", "🐕", Element.FIRE, Rarity.COMMON, 38, 14, 10, 9, 12, "Ember Bite", SkillKind.PHYSICAL, 32),
        sp("bubblefin", "Bubblefin", "🐟", Element.WATER, Rarity.COMMON, 40, 9, 13, 10, 11, "Bubble Jet", SkillKind.MAGICAL, 32),
        sp("sproutling", "Sproutling", "🌱", Element.NATURE, Rarity.COMMON, 44, 9, 12, 12, 8, "Leaf Dart", SkillKind.MAGICAL, 30),
        sp("pebblet", "Pebblet", "🪨", Element.EARTH, Rarity.COMMON, 46, 12, 7, 15, 6, "Rock Toss", SkillKind.PHYSICAL, 32),
        sp("breezling", "Breezling", "🐦", Element.AIR, Rarity.COMMON, 34, 11, 11, 8, 16, "Gust", SkillKind.MAGICAL, 30),
        sp("greywolf", "Greywolf", "🐺", Element.NEUTRAL, Rarity.COMMON, 40, 15, 6, 10, 14, "Pack Howl Bite", SkillKind.PHYSICAL, 34),
        sp("squeaker", "Squeaker", "🐭", Element.NEUTRAL, Rarity.COMMON, 30, 10, 8, 8, 18, "Nibble Flurry", SkillKind.PHYSICAL, 28),
        // Uncommon
        sp("glowmoth", "Glowmoth", "🦋", Element.LIGHT, Rarity.UNCOMMON, 38, 8, 17, 10, 15, "Dazzle Dust", SkillKind.MAGICAL, 36),
        sp("shadecat", "Shadecat", "🐈", Element.DARK, Rarity.UNCOMMON, 40, 16, 12, 10, 17, "Night Claw", SkillKind.PHYSICAL, 36),
        sp("thornboar", "Thornboar", "🐗", Element.NATURE, Rarity.UNCOMMON, 52, 17, 6, 15, 9, "Thorn Charge", SkillKind.PHYSICAL, 38),
        sp("tidecrab", "Tidecrab", "🦀", Element.WATER, Rarity.UNCOMMON, 46, 14, 8, 19, 7, "Tidal Pinch", SkillKind.PHYSICAL, 36),
        sp("cinderfox", "Cinderfox", "🦊", Element.FIRE, Rarity.UNCOMMON, 40, 12, 17, 9, 16, "Foxfire", SkillKind.MAGICAL, 38),
        sp("dustmole", "Dustmole", "🐹", Element.EARTH, Rarity.UNCOMMON, 50, 15, 9, 16, 9, "Burrow Strike", SkillKind.PHYSICAL, 36),
        // Rare
        sp("stormhawk", "Stormhawk", "🦅", Element.AIR, Rarity.RARE, 46, 19, 14, 12, 22, "Thunder Dive", SkillKind.PHYSICAL, 44),
        sp("frostbear", "Frostbear", "🐻", Element.WATER, Rarity.RARE, 62, 20, 12, 18, 8, "Glacier Maul", SkillKind.PHYSICAL, 44),
        sp("ironshell", "Ironshell", "🐢", Element.EARTH, Rarity.RARE, 60, 12, 12, 26, 5, "Quake Shell", SkillKind.MAGICAL, 40),
        sp("wraith", "Wraith", "👻", Element.DARK, Rarity.RARE, 42, 10, 22, 12, 18, "Soul Drain", SkillKind.MAGICAL, 44),
        // Epic
        sp("sunlion", "Sunlion", "🦁", Element.LIGHT, Rarity.EPIC, 60, 22, 18, 17, 17, "Solar Roar", SkillKind.MAGICAL, 50),
        sp("wyrmling", "Wyrmling", "🐉", Element.FIRE, Rarity.EPIC, 58, 21, 21, 16, 15, "Dragon Flame", SkillKind.MAGICAL, 52),
        // Legendary bosses
        sp("elder_treant", "Elder Treant", "🌳", Element.NATURE, Rarity.LEGENDARY, 85, 22, 20, 24, 8, "Ancient Roots", SkillKind.MAGICAL, 56, bossOnly = true),
        sp("abyss_kraken", "Abyss Kraken", "🐙", Element.WATER, Rarity.LEGENDARY, 80, 24, 22, 18, 12, "Maelstrom", SkillKind.MAGICAL, 58, bossOnly = true),
        sp("titan_golem", "Titan Golem", "🗿", Element.EARTH, Rarity.LEGENDARY, 90, 26, 10, 28, 6, "Colossus Slam", SkillKind.PHYSICAL, 60, bossOnly = true),
        sp("sky_serpent", "Sky Serpent", "🐲", Element.AIR, Rarity.LEGENDARY, 72, 22, 24, 16, 22, "Cyclone Coil", SkillKind.MAGICAL, 58, bossOnly = true),
        sp("magma_behemoth", "Magma Behemoth", "🌋", Element.FIRE, Rarity.LEGENDARY, 84, 27, 18, 20, 9, "Eruption", SkillKind.PHYSICAL, 60, bossOnly = true),
        sp("dread_lich", "Dread Lich", "💀", Element.DARK, Rarity.LEGENDARY, 70, 14, 28, 18, 16, "Death Knell", SkillKind.MAGICAL, 62, bossOnly = true),
        sp("seraph", "Seraph", "👼", Element.LIGHT, Rarity.LEGENDARY, 74, 18, 27, 20, 18, "Judgement", SkillKind.MAGICAL, 60, bossOnly = true),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Species = byId.getValue(id)

    val wild: List<Species> = all.filter { !it.bossOnly }
    val bosses: List<Species> = all.filter { it.bossOnly }

    fun randomWild(rng: Random): Species {
        val total = wild.sumOf { it.rarity.spawnWeight }
        var roll = rng.nextInt(total)
        for (s in wild) {
            roll -= s.rarity.spawnWeight
            if (roll < 0) return s
        }
        return wild.last()
    }
}

// ---------------------------------------------------------------------------
// Monster instances
// ---------------------------------------------------------------------------

const val MAX_IV = 15
const val MAX_MONSTER_LEVEL = 100

@Serializable
data class Monster(
    val uid: String,
    val speciesId: String,
    val personality: Personality,
    /** Individual values, 0..15 per stat: hidden genes that make each monster unique. */
    val ivs: StatBlock,
    val level: Int = 1,
    val xp: Int = 0,
    val nickname: String? = null,
) {
    val species: Species get() = Bestiary.get(speciesId)
    val displayName: String get() = nickname ?: species.name

    fun stat(stat: Stat): Int {
        val raw = species.base[stat] + ivs[stat]
        val value = if (stat == Stat.HP) {
            raw * level / 6.0 + level * 2 + 30
        } else {
            raw * level / 20.0 + 5
        }
        return floor(value * personality.modifier(stat)).toInt().coerceAtLeast(1)
    }

    val stats: StatBlock
        get() = StatBlock(
            stat(Stat.HP), stat(Stat.ATTACK), stat(Stat.MAGIC), stat(Stat.DEFENSE), stat(Stat.SPEED),
        )

    /** Average IV as a 0-100% "potential" rating shown in the UI. */
    val potential: Int
        get() = ((ivs.hp + ivs.attack + ivs.magic + ivs.defense + ivs.speed) * 100) / (MAX_IV * 5)

    val xpToNext: Int get() = monsterXpForNext(level)

    companion object {
        fun create(species: Species, level: Int, rng: Random): Monster = Monster(
            uid = newId(rng),
            speciesId = species.id,
            personality = Personality.entries[rng.nextInt(Personality.entries.size)],
            ivs = StatBlock.random(rng, MAX_IV),
            level = level.coerceIn(1, MAX_MONSTER_LEVEL),
        )
    }
}

fun monsterXpForNext(level: Int): Int = 20 + level * level * 5

/** Adds XP to a monster, levelling it up as needed. */
fun Monster.gainXp(amount: Int): Monster {
    var lvl = level
    var x = xp + amount
    while (lvl < MAX_MONSTER_LEVEL && x >= monsterXpForNext(lvl)) {
        x -= monsterXpForNext(lvl)
        lvl++
    }
    if (lvl >= MAX_MONSTER_LEVEL) x = 0
    return copy(level = lvl, xp = x)
}

fun newId(rng: Random = Random.Default): String =
    (1..12).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rng.nextInt(36)] }.joinToString("")

// ---------------------------------------------------------------------------
// Items
// ---------------------------------------------------------------------------

@Serializable
enum class ItemKind { HEAL_HP, HEAL_MP, REVIVE, CAPTURE, TREAT }

@Serializable
enum class Item(
    val label: String,
    val emoji: String,
    val kind: ItemKind,
    val power: Double,
    /** Shop price in gold; 0 = not sold. */
    val price: Int,
    val description: String,
) {
    POTION("Potion", "🧪", ItemKind.HEAL_HP, 50.0, 25, "Restores 50 HP to one ally."),
    HI_POTION("Hi-Potion", "⚗️", ItemKind.HEAL_HP, 150.0, 70, "Restores 150 HP to one ally."),
    ETHER("Ether", "🔷", ItemKind.HEAL_MP, 20.0, 40, "Restores 20 Energy to the hero."),
    PHOENIX_DOWN("Phoenix Down", "🪶", ItemKind.REVIVE, 0.5, 90, "Revives a fallen ally at 50% HP."),
    TAMING_ORB("Taming Orb", "🔮", ItemKind.CAPTURE, 1.0, 30, "Tames a weakened monster."),
    GREAT_ORB("Great Orb", "🟣", ItemKind.CAPTURE, 1.5, 80, "Better taming odds."),
    MASTER_ORB("Master Orb", "🌟", ItemKind.CAPTURE, 3.0, 0, "Excellent taming odds. Found at boss shrines."),
    TREAT("Monster Treat", "🍖", ItemKind.TREAT, 100.0, 50, "Feed to a monster outside battle for a big XP boost.");

    val usableInBattle: Boolean get() = kind != ItemKind.TREAT
}
