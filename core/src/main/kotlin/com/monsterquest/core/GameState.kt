package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Serializable
enum class HeroAttribute(val label: String, val blurb: String) {
    ATTACK("Attack", "Physical damage"),
    ENERGY("Energy", "Energy pool for spells"),
    MAGIC("Magic", "Spell damage and healing"),
    DEFENSE("Defense", "Reduces damage, raises max HP"),
    SPEED("Speed", "Act first, flee more easily"),
}

const val STAT_POINTS_PER_LEVEL = 3
const val STARTING_ATTRIBUTE = 8

/** Every this-many hero levels the player earns a storage upgrade. */
const val UPGRADE_LEVEL_INTERVAL = 5
const val BAG_UPGRADE_AMOUNT = 5
const val STORAGE_UPGRADE_AMOUNT = 3

/**
 * Hero classes, chosen at [CLASS_UNLOCK_LEVEL]. The effect starts small and
 * grows with level (see [Hero.classBonus]).
 */
@Serializable
enum class HeroClass(val label: String, val summary: String) {
    NONE("Adventurer", "No class yet"),
    WARRIOR("Warrior", "Stronger and faster, weaker magic"),
    MAGE("Mage", "Stronger magic and defense, weaker attack"),
    ROGUE("Rogue", "Faster, and dodges some attacks"),
}

const val CLASS_UNLOCK_LEVEL = 5
const val CLASS_CHANGE_COST = 300

@Serializable
data class Hero(
    val name: String = "Hero",
    val level: Int = 1,
    val xp: Int = 0,
    val unspentPoints: Int = 0,
    val attributes: Map<HeroAttribute, Int> = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE },
    val knownSpells: Set<String> = Spells.starting,
    val heroClass: HeroClass = HeroClass.NONE,
) {
    fun attr(a: HeroAttribute): Int = attributes[a] ?: STARTING_ATTRIBUTE

    /**
     * Class strength in percent: 3% at level 5, +1.5% per level after, capped
     * at 40% (level 30). Small at first, noticeable later.
     */
    val classBonus: Double
        get() = if (heroClass == HeroClass.NONE) 0.0 else (3 + (level - CLASS_UNLOCK_LEVEL) * 1.5).coerceIn(0.0, 40.0)

    private fun classMultiplier(a: HeroAttribute): Double {
        val b = classBonus / 100
        return 1 + when (heroClass) {
            HeroClass.NONE -> 0.0
            HeroClass.WARRIOR -> when (a) {
                HeroAttribute.ATTACK, HeroAttribute.SPEED -> b
                HeroAttribute.MAGIC -> -b / 2
                else -> 0.0
            }
            HeroClass.MAGE -> when (a) {
                HeroAttribute.MAGIC, HeroAttribute.DEFENSE -> b
                HeroAttribute.ATTACK -> -b / 2
                else -> 0.0
            }
            HeroClass.ROGUE -> if (a == HeroAttribute.SPEED) b else 0.0
        }
    }

    private fun stat(a: HeroAttribute) = ((attr(a) + level) * classMultiplier(a)).toInt()

    // Effective combat stats; level adds a little to everything so allocating
    // points is a boost rather than the only source of growth.
    val attack: Int get() = stat(HeroAttribute.ATTACK)
    val magic: Int get() = stat(HeroAttribute.MAGIC)
    val defense: Int get() = stat(HeroAttribute.DEFENSE)
    val speed: Int get() = stat(HeroAttribute.SPEED)

    /** Chance (0..1) to dodge an enemy attack; Rogues only. */
    val evasion: Double get() = if (heroClass == HeroClass.ROGUE) classBonus / 200 else 0.0
    val maxHp: Int get() = 40 + level * 10 + attr(HeroAttribute.DEFENSE) * 3
    val maxMp: Int get() = 10 + attr(HeroAttribute.ENERGY) * 2

    val xpToNext: Int get() = heroXpForNext(level)
}

fun heroXpForNext(level: Int): Int = 50 + (level - 1) * 40

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

@Serializable
enum class Difficulty(val label: String, val xp: Int, val gold: Int) {
    EASY("Easy", 15, 8),
    NORMAL("Normal", 30, 15),
    HARD("Hard", 60, 30),
}

@Serializable
enum class TaskSource { MANUAL, CALENDAR }

@Serializable
data class QuestTask(
    val id: String,
    val title: String,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val source: TaskSource = TaskSource.MANUAL,
    /** Start time (epoch ms) for calendar events. */
    val startMs: Long? = null,
    val endMs: Long? = null,
    val calendarName: String? = null,
    val completedAtMs: Long? = null,
    /** When a manual quest was added (calendar quests use [startMs]). */
    val createdAtMs: Long? = null,
) {
    val completed: Boolean get() = completedAtMs != null
}

// ---------------------------------------------------------------------------
// Shrines
// ---------------------------------------------------------------------------

@Serializable
data class ShrineRecord(
    val lastVisitMs: Long = 0,
    /** Boss shrines: the boss must be defeated once before it can be tamed. */
    val bossDefeated: Boolean = false,
    val bossCaptured: Boolean = false,
)

// ---------------------------------------------------------------------------
// Phone-free focus sessions
// ---------------------------------------------------------------------------

@Serializable
data class FocusSession(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val minutes: Int,
)

val FOCUS_DURATIONS = listOf(15, 30, 45, 60, 90, 120)

// ---------------------------------------------------------------------------
// Whole save game
// ---------------------------------------------------------------------------

const val STARTING_BAG_CAPACITY = 20
const val STARTING_MONSTER_CAPACITY = 6
const val MAX_PARTY_SIZE = 3
const val MAX_ITEMS_PER_BATTLE = 3
const val MAX_HUNT_TOKENS = 10
const val GOLD_LOSS_PERCENT = 10

@Serializable
data class GameState(
    val version: Int = 2,
    val hero: Hero = Hero(),
    val gold: Int = 50,
    val tasks: List<QuestTask> = emptyList(),
    val monsters: List<Monster> = emptyList(),
    /** UIDs of monsters that join battles (max [MAX_PARTY_SIZE]). */
    val party: List<String> = emptyList(),
    val inventory: Map<Item, Int> = mapOf(Item.POTION to 3, Item.TAMING_ORB to 5, Item.RESTORE_POTION to 1),
    /** Epoch day of the last free daily recovery (see GameEngine.dailyRecovery). */
    val lastRecoveryDay: Long? = null,
    /** Unequipped weapons (each takes a bag slot). */
    val weapons: List<Weapon> = emptyList(),
    val equippedWeapon: Weapon? = null,
    /** Spell-book pages collected per spell id; [Spells.PAGES_TO_LEARN] teach the spell. */
    val spellPages: Map<String, Int> = emptyMap(),
    /** Weapon parts; [PARTS_TO_FORGE] forge a new weapon. */
    val weaponParts: Int = 0,
    /** The phone-free session in progress, if any. */
    val focus: FocusSession? = null,
    /** Loot boxes from phone-free sessions (max [MAX_LOOT_BOXES]); opened by walking. */
    val lootBoxes: List<LootBox> = emptyList(),
    /** Monsters currently out on adventures. */
    val adventures: List<ActiveAdventure> = emptyList(),
    /** Adventure id -> epoch day it was taken, so it isn't offered again. */
    val adventureLog: Map<String, Long> = emptyMap(),
    /** Last step-counter reading, to work out steps walked since. */
    val lastStepCount: Long? = null,
    val bagCapacity: Int = STARTING_BAG_CAPACITY,
    val monsterCapacity: Int = STARTING_MONSTER_CAPACITY,
    /** Storage upgrades earned (one per [UPGRADE_LEVEL_INTERVAL] levels) but not yet spent. */
    val pendingUpgrades: Int = 0,
    /** Earned by completing tasks; spent to start a wild encounter from anywhere. */
    val huntTokens: Int = 1,
    val shrines: Map<String, ShrineRecord> = emptyMap(),
    /** Wild map spawns already battled (cleared periodically). */
    val usedSpawns: Set<String> = emptySet(),
    val stats: Lifetime = Lifetime(),
    val starterChosen: Boolean = false,
) {
    val bagCount: Int get() = inventory.values.sum() + weapons.size
    val bagFull: Boolean get() = bagCount >= bagCapacity
    val storageFull: Boolean get() = monsters.size >= monsterCapacity
    val partyMonsters: List<Monster> get() = party.mapNotNull { uid -> monsters.find { it.uid == uid } }
    fun count(item: Item): Int = inventory[item] ?: 0

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }
        fun fromJson(text: String): GameState = json.decodeFromString(serializer(), text).migrated()
    }

    /** Brings older saves up to date (e.g. monsters from the previous roster). */
    fun migrated(): GameState {
        if (version >= 2) return this
        return copy(
            version = 2,
            monsters = monsters.map { it.copy(speciesId = Bestiary.migrateId(it.speciesId)).evolved() },
        )
    }
}

@Serializable
data class Lifetime(
    val tasksCompleted: Int = 0,
    val battlesWon: Int = 0,
    val battlesLost: Int = 0,
    val monstersTamed: Int = 0,
    val shrinesVisited: Int = 0,
    val bossesDefeated: Int = 0,
    val focusWins: Int = 0,
    val focusFails: Int = 0,
    val focusMinutes: Int = 0,
)
