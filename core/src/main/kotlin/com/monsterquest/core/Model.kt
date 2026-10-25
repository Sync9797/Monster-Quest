package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Types: a rock-paper-scissors cycle. Each type beats exactly one other and
// loses to exactly one other:
//   Electric > Water > Fire > Corrupt > Flight > Poison > Electric
// ---------------------------------------------------------------------------

@Serializable
enum class Element(val label: String, val emoji: String) {
    NEUTRAL("Neutral", "⚪"),
    FIRE("Fire", "🔥"),
    ELECTRIC("Electric", "⚡"),
    FLIGHT("Flight", "🕊️"),
    WATER("Water", "💧"),
    POISON("Poison", "☠️"),
    CORRUPT("Corrupt", "🌑");

    /** The one type this type deals double-ish damage to. */
    val beats: Element?
        get() = when (this) {
            ELECTRIC -> WATER
            WATER -> FIRE
            FIRE -> CORRUPT
            CORRUPT -> FLIGHT
            FLIGHT -> POISON
            POISON -> ELECTRIC
            NEUTRAL -> null
        }

    /** The one type that beats this type. */
    val weakTo: Element?
        get() = entries.firstOrNull { it.beats == this }

    /** Damage multiplier when an attack of this type hits [defender]. */
    fun multiplierAgainst(defender: Element): Double = when {
        beats == defender -> 1.5
        defender.beats == this -> 0.67
        else -> 1.0
    }

    companion object {
        val typed: List<Element> = entries.filter { it != NEUTRAL }
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
// Personalities: every monster rolls one, so two Ember Pups can play very
// differently (e.g. a Ferocious one hits hard but folds quickly, a Stalwart
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

/** Hero spells. Fire Bolt and Heal are known from the start; the rest are
 * learned by collecting [PAGES_TO_LEARN] spell-book pages from item shrines. */
object Spells {
    const val PAGES_TO_LEARN = 5

    val all = listOf(
        Skill("fire_bolt", "Fire Bolt", Element.FIRE, SkillKind.MAGICAL, 30, mpCost = 4),
        Skill("heal", "Heal", Element.NEUTRAL, SkillKind.HEAL, 30, mpCost = 5),
        Skill("thunderclap", "Thunderclap", Element.ELECTRIC, SkillKind.MAGICAL, 32, mpCost = 5),
        Skill("tidal_lance", "Tidal Lance", Element.WATER, SkillKind.MAGICAL, 32, mpCost = 5),
        Skill("gale", "Gale", Element.FLIGHT, SkillKind.MAGICAL, 32, mpCost = 5),
        Skill("venom_cloud", "Venom Cloud", Element.POISON, SkillKind.MAGICAL, 34, mpCost = 6),
        Skill("shadow_rend", "Shadow Rend", Element.CORRUPT, SkillKind.MAGICAL, 36, mpCost = 6),
        Skill("mega_heal", "Mega Heal", Element.NEUTRAL, SkillKind.HEAL, 70, mpCost = 10),
        Skill("inferno", "Inferno", Element.FIRE, SkillKind.MAGICAL, 50, mpCost = 10),
        Skill("judgement", "Judgement", Element.NEUTRAL, SkillKind.MAGICAL, 60, mpCost = 14),
    )

    val starting: Set<String> = setOf("fire_bolt", "heal")

    fun byId(id: String): Skill? = all.find { it.id == id }
}

// ---------------------------------------------------------------------------
// Weapons: found at item shrines. The equipped weapon adds to the hero's
// Attack/Magic and gives their basic attack its type.
// ---------------------------------------------------------------------------

@Serializable
enum class Weapon(
    val label: String,
    val emoji: String,
    val attack: Int,
    val magic: Int,
    val element: Element,
    val rarity: Rarity,
) {
    WOODEN_SWORD("Wooden Sword", "🗡️", 3, 0, Element.NEUTRAL, Rarity.COMMON),
    APPRENTICE_STAFF("Apprentice Staff", "🪄", 0, 4, Element.NEUTRAL, Rarity.COMMON),
    IRON_SWORD("Iron Sword", "⚔️", 6, 0, Element.NEUTRAL, Rarity.UNCOMMON),
    FLAME_BLADE("Flame Blade", "🔥", 8, 2, Element.FIRE, Rarity.RARE),
    STORM_SPEAR("Storm Spear", "⚡", 8, 2, Element.ELECTRIC, Rarity.RARE),
    WIND_BOW("Wind Bow", "🏹", 7, 3, Element.FLIGHT, Rarity.RARE),
    TIDE_TRIDENT("Tide Trident", "🔱", 8, 2, Element.WATER, Rarity.RARE),
    VENOM_DAGGER("Venom Dagger", "🗡️", 7, 3, Element.POISON, Rarity.RARE),
    SHADOW_SCYTHE("Shadow Scythe", "🌙", 10, 3, Element.CORRUPT, Rarity.EPIC),
    ARCANE_STAFF("Arcane Staff", "🔮", 2, 10, Element.NEUTRAL, Rarity.EPIC);

    val description: String
        get() = buildList {
            if (attack > 0) add("+$attack Attack")
            if (magic > 0) add("+$magic Magic")
            if (element != Element.NEUTRAL) add("${element.emoji} ${element.label} attacks")
        }.joinToString(" • ")
}

@Serializable
enum class Rarity(val label: String, val spawnWeight: Int, val captureMultiplier: Double) {
    COMMON("Common", 60, 1.0),
    UNCOMMON("Uncommon", 25, 0.85),
    RARE("Rare", 11, 0.65),
    EPIC("Epic", 4, 0.45),
    LEGENDARY("Legendary", 0, 0.3),
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
        /** A new monster; it evolves straight away if [level] is past its evolution levels. */
        fun create(species: Species, level: Int, rng: Random): Monster = Monster(
            uid = newId(rng),
            speciesId = species.id,
            personality = Personality.entries[rng.nextInt(Personality.entries.size)],
            ivs = StatBlock.random(rng, MAX_IV),
            level = level.coerceIn(1, MAX_MONSTER_LEVEL),
        ).evolved()
    }
}

fun monsterXpForNext(level: Int): Int = 20 + level * level * 5

/** Adds XP to a monster, levelling it up (and evolving it) as needed. */
fun Monster.gainXp(amount: Int): Monster {
    var lvl = level
    var x = xp + amount
    while (lvl < MAX_MONSTER_LEVEL && x >= monsterXpForNext(lvl)) {
        x -= monsterXpForNext(lvl)
        lvl++
    }
    if (lvl >= MAX_MONSTER_LEVEL) x = 0
    return copy(level = lvl, xp = x).evolved()
}

/** Applies every evolution this monster's level qualifies for. */
fun Monster.evolved(): Monster {
    var m = this
    while (true) {
        val next = m.species.evolvesTo ?: break
        val at = m.species.evolveLevel ?: break
        if (m.level < at) break
        m = m.copy(speciesId = next)
    }
    return m
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
