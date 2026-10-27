package com.monsterquest.core

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

enum class EncounterType {
    /** A wild monster from the map or a hunt token. */
    WILD,
    /** First fight against a shrine boss: it must be defeated before it can be tamed. */
    BOSS_CHALLENGE,
    /** The boss has been beaten before, so taming is now allowed. */
    BOSS_TAMING,
    /** Silver shrine guardian (Tue/Thu): tough and harder to tame, but tameable right away. */
    SILVER_BOSS,
    /** Boss that bursts out of a Legendary loot box; tameable right away. */
    BOX_BOSS,
    /** A villain's shadow-cursed monster: can't be tamed. */
    VILLAIN,
    /** A den's strong monster: beat it first, then you get a few taming throws. */
    DEN,
    /** A thug and their monsters jump you: an even fight, and nothing can be tamed. */
    THUG;

    /** Enemy HP multiplier. */
    val hpMultiplier: Double
        get() = when (this) {
            WILD, VILLAIN, THUG -> 1.0
            DEN -> Dens.HP_MULTIPLIER
            SILVER_BOSS -> 2.2
            else -> 2.0
        }

    /** Multiplier on taming odds. */
    val captureMultiplier: Double
        get() = when (this) {
            SILVER_BOSS -> 0.6
            else -> 1.0
        }
}

enum class BattleOutcome { ONGOING, WON, LOST, CAPTURED, FLED }

sealed interface BattleAction {
    /** Hero's basic weapon attack. */
    data object Attack : BattleAction
    data object Defend : BattleAction
    data class CastSpell(val skillId: String, val targetKey: String? = null) : BattleAction
    /** A monster uses one of its 4 moves. */
    data class UseMove(val moveId: String) : BattleAction
    data class UseItem(val item: Item, val targetKey: String) : BattleAction
    /** Send in a monster from the swap slots, in place of [outKey] (null: into a free spot). */
    data class Swap(val inUid: String, val outKey: String?) : BattleAction
    /** Cast Binding with a crystal ([orb]); [grade] is how well its glyph was traced. */
    data class Capture(val orb: Item, val grade: TraceGrade = TraceGrade.GOOD) : BattleAction
    data object Flee : BattleAction
    /** A fainted monster's turn passes (when the player doesn't swap it out). */
    data object Skip : BattleAction
}

class Combatant(
    /** "hero", "enemy", or the monster's uid. */
    val key: String,
    val name: String,
    val element: Element,
    val level: Int,
    val maxHp: Int,
    var hp: Int,
    val attack: Int,
    val magic: Int,
    val defense: Int,
    val speed: Int,
    val maxMp: Int,
    var mp: Int,
    val isHero: Boolean,
    /** Monster moves (empty for the hero, who uses Attack and spells). */
    val moves: List<Move>,
) {
    /** For artwork: the monster's species, or the hero's class. */
    var speciesId: String? = null
    /** A dual-type monster's second type. */
    var element2: Element? = null
    /** One or two types, for matchups and the same-type bonus. */
    val types: List<Element> get() = listOfNotNull(element, element2)
    /** An enemy thug (a person fighting with their monsters). */
    var thug: Boolean = false
    var shiny: Boolean = false
    var heroClass: HeroClass? = null

    /** Chance to dodge incoming attacks (Rogue hero). */
    var evasion: Double = 0.0

    // Weapon effects (hero only).
    var hits: Int = 1
    var energyRegen: Int = 0
    var critBonus: Double = 0.0
    var lifesteal: Double = 0.0
    var onHitStatus: Status? = null
    var tameBonus: Double = 0.0
    var cursed: Boolean = false

    // Subclass perks (hero only).
    var spellPct: Int = 0
    var healPct: Int = 0
    var spellDrainPct: Int = 0
    var critDamagePct: Int = 0
    var ragePct: Int = 0
    var blindPct: Int = 0

    // Skills: the fighter's own, at their rank, and what they (or a teammate's) give it.
    val traits: MutableMap<Trait, Int> = mutableMapOf()
    var immuneEarth: Boolean = false
    /** Hovering only because of the hero's maxed Hover (it breaks when the hero is hit). */
    var borrowedHover: Boolean = false
    /** Knocked down by Smack Down: Earth moves hit it for the rest of the battle. */
    var grounded: Boolean = false
    /** Less damage from Earth moves, in percent (a hovering hero's team). */
    var earthResistPct: Int = 0
    var oblivious: Boolean = false
    /** Turns of sleep shaken off; [NO_SLEEP] means it can't fall asleep. */
    var sleepCut: Int = 0
    var regenPct: Int = 0
    var secondWindPct: Int = 0
    var secondWindUsed: Boolean = false
    var hidePct: Int = 0
    var touchStatus: Status? = null
    var touchPct: Int = 0

    // Cursed gear (hero only).
    var weaponCurse: Curse? = null
    /** The weapon is still cursed: 25% less damage. */
    var weaponCursed: Boolean = false
    /** Curses on worn armor: they strike back at physical attackers. */
    var armorCurses: List<Curse> = emptyList()
    /** Max HP lost each turn while wearing or wielding cursed gear, in percent. */
    var curseDrainPct: Int = 0
    /** Weapon hits landed and physical hits taken (they wear the curses down). */
    var weaponUses: Int = 0
    var armorHits: Int = 0

    /** Applies a skill's effect on its owner. */
    fun gainTrait(t: Trait, rank: Int, hero: Boolean) {
        traits[t] = rank
        when (t) {
            Trait.INSOMNIA -> sleepCut = maxOf(sleepCut, if (rank >= MAX_SKILL_RANK) NO_SLEEP else t.sleepCut(rank))
            Trait.HOVER, Trait.LEVITATE -> immuneEarth = true
            Trait.OBLIVIOUS -> oblivious = true
            Trait.QUICK_STEP -> evasion += t.dodgePct(rank) / 100.0
            Trait.SECOND_WIND -> secondWindPct = maxOf(secondWindPct, t.secondWindPct(rank))
            Trait.REGENERATION -> regenPct += t.regenPct(rank)
            Trait.THICK_HIDE -> hidePct = t.hidePct(rank)
            Trait.STATIC, Trait.FLAME_BODY, Trait.POISON_POINT -> {
                touchStatus = when (t) {
                    Trait.STATIC -> Status.SHOCK
                    Trait.FLAME_BODY -> Status.BURN
                    else -> Status.POISON
                }
                touchPct = t.touchPct(rank)
            }
            else -> {}
        }
    }

    /** What a hero skill at team range gives a teammate. */
    fun shareTrait(t: Trait, rank: Int) {
        when (t) {
            Trait.INSOMNIA -> sleepCut = maxOf(sleepCut, t.sleepCut(rank.coerceAtMost(MAX_SKILL_RANK - 1)))
            Trait.HOVER -> {
                earthResistPct = maxOf(earthResistPct, t.hoverTeamPct(rank))
                // Maxed: the whole team hovers too, until the hero takes a hit.
                if (rank >= MAX_SKILL_RANK && !immuneEarth) {
                    immuneEarth = true
                    borrowedHover = true
                }
            }
            Trait.OBLIVIOUS -> oblivious = true
            Trait.QUICK_STEP -> evasion += t.dodgePct(rank) / 200.0
            Trait.SECOND_WIND -> secondWindPct = maxOf(secondWindPct, t.secondWindPct(rank))
            Trait.REGENERATION -> regenPct += t.regenPct(rank)
            else -> {}
        }
    }
    /** Type of the hero's basic Attack (from their weapon). */
    var attackElement: Element = Element.NEUTRAL
    var defending: Boolean = false

    // ---- conditions ----
    var status: Status? = null
    var statusTurns: Int = 0
    var nightmareDamage: Int = 0
    var scorchTurns: Int = 0
    var scorchDamage: Int = 0
    var scorchVerb: String = "is scorched"
    /** Next move has a 40% chance to miss. */
    var accuracyDown: Boolean = false
    /** Mud Shot: accuracy lowered for the rest of the battle, in percent (stacks). */
    var muddiedPct: Int = 0
    /** Next move misses. */
    var missNext: Boolean = false
    var flinch: Boolean = false
    var mustRecharge: Boolean = false
    var protecting: Boolean = false
    /** Ward: damage soaked up before HP is touched. */
    var shield: Int = 0
    var usedProtectLast: Boolean = false
    /** Mid-dive (Dive Bomb turn 1): out of reach until it strikes. */
    var diving: Move? = null
    val stages: MutableMap<BattleStat, Int> = mutableMapOf()
    /** Steel Wing stacks, in percent. */
    var powerUp: Int = 0
    /** Scared or Intimidated: Attack drops to [SCARED_ATTACK] (66.7%) for the battle. Doesn't stack. */
    var scared: Boolean = false
    /** Swift (Water moves): moves first next round. */
    var swift: Boolean = false

    val alive: Boolean get() = hp > 0
    val hpFraction: Double get() = hp.toDouble() / maxHp

    fun stageMultiplier(stat: BattleStat): Double = (1.0 + 0.15 * (stages[stat] ?: 0)).coerceIn(0.4, 2.0)
    val effectiveSpeed: Double get() = speed * stageMultiplier(BattleStat.SPEED)

    /** Short labels for the UI, e.g. "Asleep", "Def ↓1". */
    fun tags(): List<String> = buildList {
        if (cursed) add("Shadow-cursed")
        status?.let { add(it.label) }
        if (scorchTurns > 0) add("Scorched")
        stages.forEach { (s, n) -> if (n != 0) add("${s.label.take(3)} ${if (n < 0) "↓" else "↑"}${kotlin.math.abs(n)}") }
        if (powerUp > 0) add("Power +$powerUp%")
        if (scared) add("Scared (Atk 67%)")
        if (swift) add("Moves first")
        if (immuneEarth) add("Hovering")
        if (grounded) add("Grounded")
        traits.forEach { (t, r) -> add("${t.label} ${"★".repeat(r)}") }
        if (diving != null) add("In the air")
        if (protecting) add("Guarding")
        if (shield > 0) add("Ward $shield")
        if (mustRecharge) add("Recharging")
        if (accuracyDown || missNext) add("Blinded")
        if (muddiedPct > 0) add("Acc ↓$muddiedPct%")
    }

    companion object {
        const val HERO_KEY = "hero"
        const val ENEMY_KEY = "enemy"
        const val NO_SLEEP = 99

        /** Keys for a pack's monsters: "enemy", "enemy2", "enemy3", "enemy4". */
        fun enemyKey(index: Int) = if (index == 0) ENEMY_KEY else "$ENEMY_KEY${index + 1}"

        fun isEnemy(key: String) = key.startsWith(ENEMY_KEY)

        fun fromHero(
            h: Hero,
            gear: Gear? = null,
            damage: Int = 0,
            armor: List<Armor> = emptyList(),
            /** Second weapon (Kleptomaniac): part of its power counts. */
            offhand: Gear? = null,
            /** Energy already spent before this battle (it doesn't come back between fights). */
            energyUsed: Int = 0,
        ): Combatant {
            val cls = h.heroClass
            val perks = h.perks
            val klepto = h.rankOf(Trait.KLEPTOMANIAC)
            val off = offhand?.takeIf { klepto > 0 }
            val share = if (off != null) Trait.KLEPTOMANIAC.offhandPct(klepto) / 100.0 else 0.0
            val fx = gear?.effects.orEmpty() + armor.flatMap { it.effects } + (if (klepto >= 3) off?.effects.orEmpty() else emptyList())
            // The class bonus (or the penalty for an unfamiliar weapon) goes on every stat.
            val classStats = (gear?.classStats(cls, perks.anyWeapon) ?: 0) + ((off?.classStats(cls, perks.anyWeapon) ?: 0) * share).roundToInt()
            val offAttack = ((off?.attackBonus(cls) ?: 0) * share).roundToInt()
            val offMagic = ((off?.magicBonus(cls) ?: 0) * share).roundToInt()
            val speedPct = (gear?.type?.speedPercent ?: 0) + armor.sumOf { it.type.speedPercent } + 10 * fx.count { it == GearEffect.SWIFT }
            val baseDefense = h.defense + armor.sumOf { it.defenseBonus(cls) } + classStats
            val defense = (baseDefense * (1 + 0.1 * fx.count { it == GearEffect.GUARDED })).toInt().coerceAtLeast(1)
            val attack = (h.attack + (gear?.attackBonus(cls) ?: 0) + offAttack + armor.sumOf { it.attackBonus(cls) } + classStats) *
                (1 + 0.1 * fx.count { it == GearEffect.MIGHTY })
            val magic = (h.magic + (gear?.magicBonus(cls) ?: 0) + offMagic + armor.sumOf { it.magicBonus(cls) } + classStats) *
                (1 + 0.1 * fx.count { it == GearEffect.FOCUSED })
            return Combatant(
                HERO_KEY, h.name, Element.NEUTRAL, h.level, h.maxHp, (h.maxHp - damage).coerceIn(1, h.maxHp),
                attack.toInt().coerceAtLeast(1), magic.toInt().coerceAtLeast(1),
                defense, ((h.speed + classStats) * (1 + speedPct / 100.0)).toInt().coerceAtLeast(1), h.maxMp,
                (h.maxMp - energyUsed).coerceIn(0, h.maxMp), true, emptyList(),
            ).apply {
                attackElement = gear?.element ?: Element.NEUTRAL
                evasion = h.evasion + armor.sumOf { it.type.evasionPercent } / 100.0 + perks.dodgePct / 100.0
                heroClass = h.heroClass
                hits = gear?.type?.hits ?: 1
                energyRegen = (gear?.type?.energyPerTurn ?: 0) + 2 * fx.count { it == GearEffect.ARCANE } + perks.energyPerTurn
                critBonus = (if (GearEffect.KEEN in fx) 0.1 else 0.0) + perks.critPct / 100.0
                lifesteal = 0.15 * fx.count { it == GearEffect.VAMPIRIC }
                onHitStatus = when {
                    GearEffect.SEARING in fx -> Status.BURN
                    GearEffect.CRACKLING in fx -> Status.SHOCK
                    GearEffect.VENOMOUS in fx -> Status.POISON
                    else -> null
                }
                tameBonus = 0.1 * fx.count { it == GearEffect.LUCKY } + perks.tamePct / 100.0
                weaponCurse = gear?.curse
                weaponCursed = gear?.cursed == true
                armorCurses = armor.mapNotNull { it.curse }
                curseDrainPct = CURSE_DRAIN_PCT * ((if (gear?.cursed == true) 1 else 0) + armor.count { it.cursed })
                spellPct = perks.spellPct
                healPct = perks.healPct
                spellDrainPct = perks.spellDrainPct
                critDamagePct = perks.critDamagePct
                ragePct = perks.ragePct
                blindPct = perks.blindPct
                regenPct = perks.regenPct
                if (perks.startWardPct > 0) shield = maxHp * perks.startWardPct / 100
                h.skillRanks.forEach { (t, r) -> gainTrait(t, r, hero = true) }
            }
        }

        /**
         * A thug: about as tough as a monster of their level, fighting their own way.
         * Brutes hit hard and take a beating but are slow; Hexers cast dark magic
         * but are frail; Cutthroats are quick, dodge, and fight dirty.
         */
        fun fromThug(t: Thug, key: String): Combatant {
            val lv = t.level
            data class Build(val hp: Int, val atk: Int, val def: Int, val spd: Int, val element: Element, val moves: List<String>, val dodge: Double)
            val b = when (t.cls) {
                HeroClass.WARRIOR -> Build(70 + lv * 12, 18 + lv * 2, 15 + lv * 2, 8 + lv * 3 / 4, Element.NEUTRAL, listOf("headbutt", "ground_pound", "slam", "bite"), 0.0)
                HeroClass.MAGE -> Build(45 + lv * 8, 18 + lv * 5 / 2, 10 + lv, 12 + lv, Element.CORRUPT, listOf("hex_barrage", "dark_wave", "nightmare", "soul_drain"), 0.0)
                else -> Build(50 + lv * 9, 16 + lv * 2, 12 + lv * 3 / 2, 14 + lv * 5 / 4, Element.NEUTRAL, listOf("sucker_punch", "night_slash", "fury_swipes", "scare"), 0.1)
            }
            return Combatant(
                key, t.title, b.element, lv, b.hp, b.hp, b.atk, 0, b.def, b.spd, 0, 0, false, b.moves.map { Moves.get(it) },
            ).apply {
                thug = true
                heroClass = t.cls
                // Brutes glare their foes down as the fight starts.
                if (t.cls == HeroClass.WARRIOR) gainTrait(Trait.INTIMIDATE, 1, hero = false)
                evasion = b.dodge
            }
        }

        fun fromMonster(
            m: Monster,
            key: String = m.uid,
            hpMultiplier: Double = 1.0,
            attackMultiplier: Double = 1.0,
            defenseMultiplier: Double = 1.0,
            speedMultiplier: Double = 1.0,
        ): Combatant {
            val s = m.stats
            val hp = (s.hp * hpMultiplier).toInt()
            val atk = (s.attack * attackMultiplier).toInt()
            return Combatant(
                key, m.displayName, m.species.element, m.level, hp, (hp - m.damage).coerceIn(1, hp),
                atk, atk, (s.defense * defenseMultiplier).toInt(), (s.speed * speedMultiplier).toInt().coerceAtLeast(1), 0, 0, false, m.activeMoves,
            ).apply {
                speciesId = m.speciesId
                element2 = m.species.secondary
                cursed = m.cursed
                shiny = m.shiny
                m.trait?.let { (t, r) -> gainTrait(t, r, hero = false) }
            }
        }
    }
}

/** How an attack is shown on screen. */
enum class StrikeStyle {
    /** Weapons and Normal-type moves: slash marks across the target. */
    SLASH,
    /** Spells and typed moves: a projectile of the move's type flies at the target. */
    BOLT,
    HEAL,
    MISS,
    /** A protective spell: Ward or Fortify on an ally. */
    WARD,
}

/** One thing that happened in battle, for the UI to animate (in [seq] order). */
data class BattleEvent(
    val seq: Int,
    val actorKey: String,
    val targetKey: String,
    val style: StrikeStyle,
    val element: Element,
    val amount: Int = 0,
    val crit: Boolean = false,
    val superEffective: Boolean = false,
)

/**
 * Enemies grow with the size of your team (the hero plus up to 5 monsters),
 * so a full team of 6 faces a real fight while a lone hero isn't overwhelmed.
 * Bosses, dens' rivals and villains are built for a team of 4: they get easier
 * against smaller teams and tougher (and pay more) against 5 or 6.
 */
object TeamScaling {
    data class Factors(val hp: Double, val attack: Double, val defense: Double)

    fun factors(teamSize: Int, encounter: EncounterType): Factors =
        if (encounter == EncounterType.WILD || encounter == EncounterType.DEN) {
            Factors(hp(teamSize), attack(teamSize), defense(teamSize))
        } else {
            Factors(hp(teamSize) / hp(4), attack(teamSize) / attack(4), defense(teamSize) / defense(4))
        }

    private val hpByTeam = listOf(0.35, 1.2, 2.6, 4.2, 5.6, 7.0)
    private val attackByTeam = listOf(0.45, 1.1, 1.6, 2.0, 2.3, 2.55)
    private val defenseByTeam = listOf(1.0, 1.0, 1.1, 1.2, 1.25, 1.3)

    private fun pick(table: List<Double>, teamSize: Int) = table[(teamSize - 1).coerceIn(0, table.size - 1)]

    fun hp(teamSize: Int) = pick(hpByTeam, teamSize)
    fun attack(teamSize: Int) = pick(attackByTeam, teamSize)
    fun defense(teamSize: Int) = pick(defenseByTeam, teamSize)
    private val rewardByTeam = listOf(0.6, 1.25, 2.0, 2.75, 3.4, 4.0)

    /** Bosses and villains pay more when they had to scale up for a team bigger than 4. */
    fun bossReward(teamSize: Int) = (reward(teamSize) / reward(4)).coerceAtLeast(1.0)

    /**
     * Rewards follow the enemy's strength: a lone hero faces a weaker monster
     * for less, a full team faces a much stronger one for much more.
     */
    fun reward(teamSize: Int) = pick(rewardByTeam, teamSize)

    // Packs: the team-scaled strength is shared out among the pack, with a
    // little extra in total (more monsters means more turns against you).
    private val packHp = listOf(1.0, 0.45, 0.32, 0.25)
    private val packAttack = listOf(1.0, 0.5, 0.36, 0.28)
    /** Packs pay more in total than a single monster. */
    private val packReward = listOf(1.0, 1.4, 1.75, 2.1)

    fun pack(f: Factors, count: Int): Factors {
        val i = (count - 1).coerceIn(0, 3)
        return Factors(f.hp * packHp[i], f.attack * packAttack[i], f.defense)
    }

    fun packReward(count: Int) = packReward[(count - 1).coerceIn(0, 3)]
}

data class BattleResult(
    val outcome: BattleOutcome,
    val encounter: EncounterType,
    val enemy: Monster,
    val heroXp: Int,
    /** Gold won (positive). Losses are applied as a percentage by [GameEngine]. */
    val goldWon: Int,
    val monsterXp: Map<String, Int>,
    val itemsUsed: Map<Item, Int>,
    val drops: Map<Item, Int>,
    val captured: Monster?,
    val shrineId: String?,
    /** Party monsters knocked out at the end of the battle; they stay down until restored. */
    val faintedUids: Set<String> = emptySet(),
    /** Missing HP per surviving ally key ("hero" or a monster uid), carried into the next battle. */
    val damage: Map<String, Int> = emptyMap(),
    val heroFell: Boolean = false,
    /** Energy the hero has spent by the end of the battle. */
    val heroEnergyUsed: Int = 0,
    /** Weapon hits landed and physical hits taken: they wear curses down. */
    val weaponUses: Int = 0,
    val armorHits: Int = 0,
    /** Species of every monster beaten (each gives candy of its line). */
    val defeated: List<String> = emptyList(),
    /** Your monsters still standing at the end (each one earns an extra taming throw at a den). */
    val standing: Int = 0,
)

/** " (strong trace)" for the battle log; nothing for a Perfect Crystal, which needs no trace. */
private fun traceNote(a: BattleAction.Capture) = if (Binding.needsTrace(a.orb)) " (${a.grade.label.lowercase()} trace)" else ""

/** Every taming cast has this chance to be a critical catch: tamed, whatever the odds. */
const val CRITICAL_CATCH = 0.02

/** The real chance of a throw with base odds [p], counting the critical catch. */
fun withCriticalCatch(p: Double): Double = if (p >= 1.0) 1.0 else CRITICAL_CATCH + (1 - CRITICAL_CATCH) * p

/** Chance (0..1) that throwing [orb] tames the enemy right now. 0 = not allowed. */
fun captureChance(hpFraction: Double, rarity: Rarity, levelDiff: Int, orb: Item): Double {
    if (hpFraction > 0.5) return 0.0
    val base = (1.0 - hpFraction * 1.6) * 0.9
    val levelPenalty = 0.92.pow(max(0, levelDiff))
    return (base * rarity.captureMultiplier * orb.power * levelPenalty).coerceIn(0.03, 0.95)
}

/** Hero spells aimed at an ally (or the hero) instead of an enemy. */
val SUPPORT_KINDS = setOf(SkillKind.HEAL, SkillKind.SHIELD, SkillKind.FORTIFY)

private val TOUCH_TRAITS = setOf(Trait.STATIC, Trait.FLAME_BODY, Trait.POISON_POINT)

/** Chance (percent) a cursed Frost or Flame piece afflicts the foe. */
const val CURSE_STATUS_PCT = 35
/** A cursed Life Drain weapon heals this share of the damage it deals (percent). */
const val CURSE_DRAIN_HEAL = 30
/** Cursed Life Drain armor steals this share of the damage taken back from the attacker (percent). */
const val CURSE_ARMOR_STEAL = 10

/** Monsters that wait in the swap slots, ready to be sent in. */
const val MAX_SWAPS = 2

/** Scare and Intimidate: the foe's Attack drops to 66.7% (two thirds). */
const val SCARED_ATTACK = 2.0 / 3.0

/** Pay for a thug fight, per fighter on their side (thug included). */
const val THUG_REWARD = 0.9

/** Highest a stat stage can go (stage multipliers are capped at 2x anyway). */
private const val MAX_STAGES = 6

class Battle(
    hero: Hero,
    party: List<Monster>,
    val enemyMonster: Monster,
    val encounter: EncounterType,
    inventory: Map<Item, Int>,
    /** Whether the player has room to keep a tamed monster. */
    private val storageFull: Boolean,
    val shrineId: String? = null,
    private val rng: Random = Random.Default,
    gear: Gear? = null,
    heroDamage: Int = 0,
    armor: List<Armor> = emptyList(),
    /** Other wild monsters fighting alongside [enemyMonster] as a pack (up to 3). */
    extraEnemies: List<Monster> = emptyList(),
    /** The monsters struck first: they act before you in the first round. */
    val ambush: Boolean = false,
    /** Second weapon carried with Kleptomaniac. */
    offhand: Gear? = null,
    /** Monsters in the swap slots: they can be sent in during the fight. */
    swaps: List<Monster> = emptyList(),
    /** Energy the hero has already spent. */
    heroEnergyUsed: Int = 0,
    /** A thug fighting alongside the enemy monsters. */
    val thug: Thug? = null,
) {
    private val fighters = party.filter { !it.fainted }.take(MAX_PARTY_SIZE)
    private val swapMonsters = swaps.filter { !it.fainted }.take(MAX_SWAPS)
    private val heroPerks = hero.perks
    /** Your monsters' boost from the hero (Morale Master, Warlord, Beastmaster) and the best Pack Leader among them. */
    private val teamBoost = 1 + (heroPerks.teamPct + packLeader(fighters)) / 100.0
    private fun ally(m: Monster) =
        Combatant.fromMonster(m, hpMultiplier = teamBoost, attackMultiplier = teamBoost, defenseMultiplier = teamBoost, speedMultiplier = teamBoost)

    private val team: MutableList<Combatant> =
        (listOf(Combatant.fromHero(hero, gear, heroDamage, armor, offhand, heroEnergyUsed)) + fighters.map { ally(it) }).toMutableList()

    /** Your side as it stands: the hero, then the monsters fighting (swaps change who's here). */
    val allies: List<Combatant> get() = team

    /** Monsters waiting in the swap slots (and any swapped out). */
    private val reserve: MutableList<Combatant> = swapMonsters.map { ally(it) }.toMutableList()
    val swapsWaiting: List<Combatant> get() = reserve

    /** Monsters that actually stepped into the fight (they earn full XP). */
    private val fought: MutableSet<String> = fighters.map { it.uid }.toMutableSet()
    /** Fighters on your side: the hero plus the party monsters able to fight. */
    val teamSize: Int = 1 + fighters.size
    /** The enemy side: one monster, or a pack of up to 4. */
    val enemyMonsters: List<Monster> = listOf(enemyMonster) + extraEnemies.take(3)
    // A thug's crew is an even match already: no extra scaling.
    private val scaling = if (encounter == EncounterType.THUG) TeamScaling.Factors(1.0, 1.0, 1.0)
    else TeamScaling.pack(TeamScaling.factors(teamSize, encounter), enemyMonsters.size)
    private val enemyBoost = 1 + packLeader(enemyMonsters) / 100.0
    val enemies: List<Combatant> = enemyMonsters.mapIndexed { i, m ->
        // Legendaries are built stronger to own; as shrine guardians they're scaled back to their usual challenge.
        val legend = if (m.species.rarity == Rarity.LEGENDARY && encounter != EncounterType.WILD) 1 / Bestiary.LEGENDARY_POWER else 1.0
        Combatant.fromMonster(
            m,
            Combatant.enemyKey(i),
            hpMultiplier = encounter.hpMultiplier * scaling.hp * legend * enemyBoost,
            attackMultiplier = scaling.attack * legend * enemyBoost,
            defenseMultiplier = scaling.defense * legend * enemyBoost,
            speedMultiplier = enemyBoost,
        )
    } + listOfNotNull(thug?.let { Combatant.fromThug(it, Combatant.enemyKey(enemyMonsters.size)) })

    /** Which enemy your attacks, spells and orbs aim at (the player can switch). */
    var targetKey: String = Combatant.ENEMY_KEY
        private set

    /** The enemy being targeted (the next one standing if it falls). */
    val enemy: Combatant
        get() = enemies.find { it.key == targetKey && it.alive } ?: enemies.firstOrNull { it.alive } ?: enemies.first()

    fun selectTarget(key: String) {
        if (enemies.any { it.key == key && it.alive }) targetKey = key
    }

    private fun monsterFor(key: String): Monster = enemyMonsters[enemies.indexOfFirst { it.key == key }.coerceIn(0, enemyMonsters.size - 1)]

    private var capturedKey: String? = null
    private val heroLevel = hero.level
    private val heroSkills = hero.spells()
    private val partyUids = fighters.map { it.uid } + swapMonsters.map { it.uid }

    private val bag: MutableMap<Item, Int> = inventory.toMutableMap()
    private val itemsUsed = mutableMapOf<Item, Int>()
    val itemsUsedCount: Int get() = itemsUsed.values.sum()
    val itemsRemainingThisBattle: Int get() = MAX_ITEMS_PER_BATTLE - itemsUsedCount

    val log = mutableListOf<String>()
    /** Attacks, heals and misses in order, for animations. */
    val events = mutableListOf<BattleEvent>()
    var outcome: BattleOutcome = BattleOutcome.ONGOING
        private set
    var round: Int = 0
        private set
    var weather: Weather = Weather.CLEAR
        private set
    /** Rounds left with reversed turn order (Wild Wind). */
    var reversedRounds: Int = 0
        private set

    private val queue = ArrayDeque<String>()

    /** The ally whose turn it is (waiting for player input), or null if the battle is over. */
    var current: Combatant? = null
        private set

    val isBoss: Boolean get() = encounter !in setOf(EncounterType.WILD, EncounterType.DEN, EncounterType.THUG)
    val captureLocked: Boolean
        get() = encounter == EncounterType.BOSS_CHALLENGE || encounter == EncounterType.VILLAIN || encounter == EncounterType.DEN ||
            encounter == EncounterType.THUG

    init {
        val intro = when (encounter) {
            EncounterType.WILD -> if (enemies.size > 1) {
                "A pack of ${enemies.size} wild monsters attacks together: ${enemies.joinToString { "${it.name} (Lv ${it.level})" }}!"
            } else {
                "A wild ${enemy.name} (Lv ${enemy.level}) appears!"
            }
            EncounterType.BOSS_CHALLENGE -> "BOSS: ${enemy.name} (Lv ${enemy.level}) guards this shrine! Defeat it to earn the right to tame it."
            EncounterType.BOSS_TAMING -> "BOSS: ${enemy.name} (Lv ${enemy.level}) remembers you. Weaken it and try to tame it!"
            EncounterType.SILVER_BOSS -> "SILVER GUARDIAN: ${enemy.name} (Lv ${enemy.level}) blocks your path! It's hard to tame, but it has perfect genes."
            EncounterType.BOX_BOSS -> "The loot box bursts open... ${enemy.name} (Lv ${enemy.level}) erupts out of it!"
            EncounterType.VILLAIN -> "A shadow-cursed ${enemy.name} (Lv ${enemy.level}) attacks! It can't be tamed."
            EncounterType.DEN -> "A fierce ${enemy.name} (Lv ${enemy.level}) charges out of its den! Beat it, then try to tame it."
            EncounterType.THUG -> "${thug?.title ?: "A thug"} of the ${thug?.crew ?: "streets"} jumps you with " +
                "${enemyMonsters.joinToString { "${it.displayName} (Lv ${it.level})" }}!"
        }
        log += intro
        if (ambush) log += "Ambush! The monsters strike before you're ready!"
        if (encounter == EncounterType.WILD && teamSize > 1 && enemies.size < teamSize) {
            log += (if (enemies.size > 1) "The pack grows" else "${enemy.name} grows") + " stronger against your team of $teamSize!"
        }
        openingSkills(hero)
        advance()
    }

    /** The best Pack Leader bonus in a group of monsters, in percent. */
    private fun packLeader(group: List<Monster>): Int =
        group.mapNotNull { m -> m.trait?.takeIf { it.first == Trait.PACK_LEADER }?.let { (t, r) -> t.teamPct(r) } }.maxOrNull() ?: 0

    /** Skills that act as the battle starts: team-wide hero skills, wards and Intimidate. */
    private fun openingSkills(hero: Hero) {
        val me = allies.first()
        for ((t, r) in hero.skillRanks) {
            if (t.teamRange(r, hero = true)) (allies.drop(1) + reserve).forEach { it.shareTrait(t, r) }
        }
        if (heroPerks.teamPct > 0 && allies.size > 1) log += "Your monsters are fired up! (+${heroPerks.teamPct}%)"
        if (packLeader(fighters) > 0) log += "A Pack Leader rallies your team! (+${packLeader(fighters)}%)"
        if (enemyBoost > 1) log += "A Pack Leader rallies the wild pack! (+${packLeader(enemyMonsters)}%)"
        if (me.shield > 0) log += "${me.name} starts behind a holy ward (absorbs ${me.shield})."
        for (c in allies + enemies) {
            val r = c.traits[Trait.INTIMIDATE] ?: continue
            if (!c.alive) continue
            // The same Attack cut as Scare (66.7%), plus extra stages at higher ranks.
            val extra = Trait.INTIMIDATE.intimidateStages(r)
            log += "${c.name}'s Intimidate!"
            for (foe in foesOf(c).filter { it.alive }) {
                if (foe.oblivious) {
                    log += "${foe.name} doesn't even notice."
                } else {
                    foe.scared = true
                    if (extra > 0) foe.stages[BattleStat.ATTACK] = ((foe.stages[BattleStat.ATTACK] ?: 0) - extra).coerceAtLeast(-4)
                    log += "${foe.name}'s Attack fell to 67%!"
                }
            }
        }
    }

    fun combatant(key: String): Combatant? =
        if (Combatant.isEnemy(key)) enemies.find { it.key == key } else allies.find { it.key == key } ?: reserve.find { it.key == key }

    fun count(item: Item): Int = bag[item] ?: 0

    // ---- queries for the UI -------------------------------------------------

    fun spellsFor(actor: Combatant): List<Skill> = if (actor.isHero) heroSkills else emptyList()

    fun battleItems(): List<Item> =
        Item.entries.filter { it.usableInBattle && it.kind != ItemKind.CAPTURE && it.kind != ItemKind.BAIT && count(it) > 0 }

    /** HP a healing spell restores when [caster] casts it (before capping at full). */
    fun spellHeal(caster: Combatant, skill: Skill): Int = ((skill.power + caster.magic * 1.5) * (1 + caster.healPct / 100.0)).roundToInt()

    /** Berries you could feed the foe (on the Tame menu). */
    fun berries(): List<Item> = Item.entries.filter { it.kind == ItemKind.BAIT && count(it) > 0 }

    /** True if [berry] can be fed now: the foe can be tamed in this fight, and it hasn't eaten as good a berry. */
    fun canFeed(berry: Item): Boolean =
        berry.kind == ItemKind.BAIT && count(berry) > 0 && itemsRemainingThisBattle > 0 && bait < berry.power &&
            encounter != EncounterType.VILLAIN && encounter != EncounterType.DEN && encounter != EncounterType.THUG && !captureLocked

    /** A berry the foe has eaten: the next throw's odds are multiplied by this (1 = none). */
    var bait: Double = 1.0
        private set

    /** Orbs you can throw now: above half HP only a Perfect Orb works. */
    fun orbs(): List<Item> = Item.entries.filter {
        it.kind == ItemKind.CAPTURE && count(it) > 0 && (enemy.hpFraction <= 0.5 || it == Item.PERFECT_ORB)
    }

    /** Null when capture is allowed, otherwise a reason to show the player. */
    fun captureBlockedReason(): String? = when {
        encounter == EncounterType.VILLAIN -> "Shadow-bound monsters can't be tamed. Defeat the whole team."
        encounter == EncounterType.DEN -> "Den monsters are too fierce. Beat it first, then you'll get your binding casts."
        encounter == EncounterType.THUG -> "A thug's monsters won't leave their master. Beat the whole crew."
        captureLocked -> "Defeat this boss first. Then it can be tamed on a later visit."
        storageFull -> "Monster storage is full. Release a monster or upgrade storage."
        orbs().isEmpty() -> "No Binding Crystals left."
        itemsRemainingThisBattle <= 0 -> "Item limit reached for this battle ($MAX_ITEMS_PER_BATTLE)."
        enemy.hpFraction > 0.5 && count(Item.PERFECT_ORB) <= 0 -> "Too strong to tame. Get it below 50% HP."
        else -> null
    }

    /** The odds shown for [orb]: its taming chance plus the 2% critical catch. */
    fun catchOdds(orb: Item, grade: TraceGrade = TraceGrade.GOOD): Double = withCriticalCatch(captureOdds(orb, grade))

    /** Status conditions (sleep, shock, poison, burn) make taming easier. */
    fun captureOdds(orb: Item, grade: TraceGrade = TraceGrade.GOOD): Double = if (orb == Item.PERFECT_ORB) 1.0 else
        (
            captureChance(enemy.hpFraction, monsterFor(enemy.key).species.rarity, enemy.level - heroLevel, orb) * bait * grade.multiplier *
                encounter.captureMultiplier * (enemy.status?.captureBonus ?: 1.0) * (1 + (allies.firstOrNull()?.tameBonus ?: 0.0))
            ).coerceIn(0.0, 0.95)

    // ---- turn handling ------------------------------------------------------

    fun act(action: BattleAction) {
        val actor = current ?: return
        if (outcome != BattleOutcome.ONGOING) return
        val consumedTurn = perform(actor, action)
        if (!consumedTurn) return
        current = null
        checkEnd()
        advance()
    }

    private fun advance() {
        while (outcome == BattleOutcome.ONGOING) {
            if (queue.isEmpty()) startRound()
            val key = queue.removeFirst()
            val c = combatant(key) ?: continue
            if (!c.alive) {
                // A fallen monster's turn: the player can swap someone in or skip it.
                if (awaitsSwap(c)) {
                    current = c
                    return
                }
                continue
            }
            val canAct = beginTurn(c)
            checkEnd()
            if (!canAct || outcome != BattleOutcome.ONGOING) continue
            if (Combatant.isEnemy(key)) {
                enemyTurn(c)
                checkEnd()
            } else {
                current = c
                return
            }
        }
        current = null
    }

    /**
     * Who acts next, starting with whoever is acting now: the rest of this
     * round, then the predicted order of the next round (by Speed).
     */
    fun turnOrder(count: Int = 6): List<String> {
        val alive = { k: String -> combatant(k)?.alive == true }
        val now = listOfNotNull(current?.key) + queue.filter(alive)
        val everyone = (allies + enemies).filter { it.alive }.sortedByDescending { it.effectiveSpeed }
            .sortedByDescending { it.swift }
        val nextReversed = reversedRounds > 1
        val next = (if (nextReversed) everyone.reversed() else everyone).map { it.key } +
            (if (isBoss && enemies.first().alive) listOf(Combatant.ENEMY_KEY) else emptyList())
        return (now + next + next).take(count)
    }

    private fun record(a: Combatant, d: Combatant, style: StrikeStyle, element: Element, amount: Int = 0, crit: Boolean = false, superEffective: Boolean = false) {
        events += BattleEvent(events.size, a.key, d.key, style, element, amount, crit, superEffective)
    }

    private fun startRound() {
        round++
        if (reversedRounds > 0) reversedRounds--
        // Fallen monsters keep their turn while a swap is waiting (to swap or skip).
        val everyone = (allies + enemies).filter { it.alive || awaitsSwap(it) }
        val bySpeed = everyone.sortedByDescending { it.effectiveSpeed + rng.nextDouble() }
        val ordered = if (reversedRounds > 0) bySpeed.reversed() else bySpeed
        // An ambush: the monsters all act before you in the first round.
        var order = if (ambush && round == 1) ordered.sortedBy { !Combatant.isEnemy(it.key) } else ordered
        // First Strike: the hero (or, at team range, the whole team) opens the battle.
        val strike = allies.first().traits[Trait.FIRST_STRIKE]
        if (round == 1 && strike != null) {
            val first = if (Trait.FIRST_STRIKE.teamRange(strike, hero = true)) order.filter { !Combatant.isEnemy(it.key) }
            else order.filter { it.key == Combatant.HERO_KEY }
            order = first + (order - first.toSet())
        }
        // Swift: whoever used a Swift move last round goes first now.
        val swifts = order.filter { it.swift }
        if (swifts.isNotEmpty()) {
            order = swifts + (order - swifts.toSet())
            swifts.forEach { it.swift = false }
        }
        queue.addAll(order.map { it.key })
        // Bosses act twice per round.
        if (isBoss) queue.addLast(Combatant.ENEMY_KEY)
    }

    /** Damage of a fixed "base" amount, scaled by level like normal attacks. */
    private fun scaled(base: Int, level: Int) = (base * (1 + level * 0.08)).roundToInt()

    private fun hurt(c: Combatant, amount: Int, text: String) {
        c.hp = max(0, c.hp - amount)
        log += "${c.name} $text ($amount)."
        if (!c.alive) log += "${c.name} falls!"
        secondWind(c)
    }

    private fun breakTeamHover(hero: Combatant) {
        val lifted = teamOf(hero).filter { it.borrowedHover } + reserve.filter { it.borrowedHover }
        if (lifted.isEmpty()) return
        lifted.forEach { it.borrowedHover = false; it.immuneEarth = false }
        log += "${hero.name} is hit! The team's hover breaks (${hero.name} keeps floating)."
    }

    /** Second Wind: once a battle, a big heal on dropping below a quarter of max HP. */
    private fun secondWind(c: Combatant) {
        if (c.secondWindPct <= 0 || c.secondWindUsed || !c.alive || c.hpFraction >= 0.25) return
        c.secondWindUsed = true
        heal(c, maxOf(1, c.maxHp * c.secondWindPct / 100), "${c.name} gets a second wind")
    }

    /** Puts [d] to sleep for [turns], less what Insomnia shakes off. Returns false if it stays awake. */
    private fun fallAsleep(d: Combatant, turns: Int): Boolean {
        if (d.sleepCut >= Combatant.NO_SLEEP) {
            log += "${d.name}'s Insomnia keeps it wide awake!"
            return false
        }
        d.status = Status.SLEEP
        d.statusTurns = (turns - d.sleepCut).coerceAtLeast(1)
        return true
    }

    /** Ticks conditions at the start of a fighter's turn. Returns false if it loses the turn. */
    private fun beginTurn(c: Combatant): Boolean {
        c.defending = false
        c.protecting = false
        if (c.energyRegen > 0 && c.mp < c.maxMp) c.mp = minOf(c.maxMp, c.mp + c.energyRegen)
        if (c.regenPct > 0 && c.hp < c.maxHp) heal(c, maxOf(1, c.maxHp * c.regenPct / 100), "${c.name} regenerates")
        if (c.scorchTurns > 0) {
            c.scorchTurns--
            hurt(c, c.scorchDamage, c.scorchVerb)
        }
        when (c.status) {
            Status.POISON -> hurt(c, max(1, c.maxHp / 10), "is hurt by poison")
            Status.BURN -> hurt(c, max(1, c.maxHp * 6 / 100), "is hurt by its burn")
            // A drought thaws frostbite out.
            Status.FROSTBITE -> if (weather != Weather.DROUGHT) hurt(c, max(1, c.maxHp * 5 / 100), "is hurt by frostbite")
            else -> {}
        }
        if (c.status == Status.POISON || c.status == Status.BURN || c.status == Status.FROSTBITE) tickStatus(c)
        // Cursed gear gnaws at its owner every turn until the curse is lifted.
        if (c.curseDrainPct > 0 && c.alive) hurt(c, max(1, c.maxHp * c.curseDrainPct / 100), "is drained by a curse")
        if (!c.alive) return false

        if (c.status == Status.SLEEP) {
            if (c.nightmareDamage > 0) hurt(c, c.nightmareDamage, "writhes in a nightmare")
            if (!c.alive) return false
            tickStatus(c)
            if (c.status == Status.SLEEP) {
                log += "${c.name} is fast asleep."
                return false
            }
            log += "${c.name} woke up!"
        }
        if (c.mustRecharge) {
            c.mustRecharge = false
            log += "${c.name} must recharge."
            return false
        }
        if (c.flinch) {
            c.flinch = false
            log += "${c.name} flinched and lost its turn!"
            return false
        }
        if (c.status == Status.SHOCK) {
            tickStatus(c)
            if (rng.nextDouble() < 0.25) {
                log += "${c.name} is shocked and can't move!"
                return false
            }
        }
        val dive = c.diving
        if (dive != null) {
            c.diving = null
            val target = if (Combatant.isEnemy(c.key)) pickTarget() else enemy
            if (target != null) useMove(c, target, dive, diveStrike = true)
            return false
        }
        return true
    }

    private fun tickStatus(c: Combatant) {
        c.statusTurns--
        if (c.statusTurns <= 0) {
            val s = c.status
            c.status = null
            c.nightmareDamage = 0
            if (s != null && s != Status.SLEEP) log += "${c.name} is no longer ${s.label.lowercase()}."
        }
    }

    private fun checkEnd() {
        if (outcome != BattleOutcome.ONGOING) return
        if (enemies.none { it.alive }) {
            outcome = BattleOutcome.WON
            log += if (enemies.size > 1) "The whole pack is defeated!" else "${enemy.name} is defeated!"
        } else if (allies.none { it.alive }) {
            outcome = BattleOutcome.LOST
            log += "Your party has fallen..."
        }
    }

    /** Returns false if the action was invalid (turn not used). */
    private fun perform(actor: Combatant, action: BattleAction): Boolean {
        // A fallen monster can only be swapped out, or let its turn pass.
        if (!actor.alive) {
            return when (action) {
                is BattleAction.Swap -> swapIn(action.copy(outKey = actor.key))
                BattleAction.Skip -> {
                    log += "${actor.name} can't fight. Its turn passes."
                    true
                }
                else -> false
            }
        }
        when (action) {
            BattleAction.Skip -> return false
            BattleAction.Attack -> {
                if (actor.isHero) {
                    // Daggers strike twice.
                    repeat(actor.hits) {
                        if (enemy.alive && !floatsOver(actor, enemy, actor.attackElement) && rollHit(actor, enemy, 100)) {
                            // A cursed weapon hits 25% softer until the curse is lifted.
                            val power = if (actor.weaponCursed) 40 * (100 - CURSED_DAMAGE_CUT) / 100 else 40
                            val dmg = strike(actor, enemy, power, actor.attackElement, useMagic = false, verb = "attacks", style = StrikeStyle.SLASH)
                            afterHeroHit(actor, enemy, dmg, basic = true)
                        }
                    }
                } else {
                    useMove(actor, enemy, actor.moves.firstOrNull() ?: Moves.get("tackle"))
                }
            }
            BattleAction.Defend -> {
                actor.defending = true
                log += "${actor.name} braces for impact."
            }
            is BattleAction.UseMove -> {
                val move = actor.moves.find { it.id == action.moveId } ?: return false
                useMove(actor, enemy, move)
            }
            is BattleAction.CastSpell -> {
                val skill = spellsFor(actor).find { it.id == action.skillId } ?: return false
                if (actor.mp < skill.mpCost) return false
                if (skill.kind in SUPPORT_KINDS) {
                    val target = combatant(action.targetKey ?: actor.key)?.takeIf { it.alive && !Combatant.isEnemy(it.key) }
                        ?: return false
                    actor.mp -= skill.mpCost
                    when (skill.kind) {
                        SkillKind.SHIELD -> {
                            val amount = ((skill.power + actor.magic * 1.2) * (1 + actor.healPct / 100.0)).roundToInt()
                            target.shield = max(target.shield, amount)
                            log += "${actor.name} casts ${skill.name}: ${target.name} is shielded (absorbs ${target.shield})."
                            record(actor, target, StrikeStyle.WARD, Element.NEUTRAL, target.shield)
                        }
                        SkillKind.FORTIFY -> {
                            val now = target.stages[BattleStat.DEFENSE] ?: 0
                            target.stages[BattleStat.DEFENSE] = minOf(MAX_STAGES, now + skill.power)
                            log += "${actor.name} casts ${skill.name}: ${target.name}'s Defense rises!"
                            record(actor, target, StrikeStyle.WARD, Element.NEUTRAL)
                        }
                        else -> heal(target, spellHeal(actor, skill), "${actor.name} casts ${skill.name}")
                    }
                } else {
                    actor.mp -= skill.mpCost
                    if (floatsOver(actor, enemy, skill.element) || !rollHit(actor, enemy, 100)) return true
                    val target = enemy
                    val dmg = strike(actor, target, skill.power, skill.element, useMagic = true, verb = "casts ${skill.name} on", style = StrikeStyle.BOLT)
                    afterHeroHit(actor, target, dmg)
                    // Mud Shot: mud in the eyes, stacking.
                    if (skill.id == Spells.MUD_SHOT && target.alive) {
                        val cap = Spells.MUD_SHOT_ACCURACY * Spells.MUD_SHOT_STACKS
                        if (target.muddiedPct >= cap) {
                            log += "${target.name} can't get any muddier."
                        } else {
                            target.muddiedPct = minOf(cap, target.muddiedPct + Spells.MUD_SHOT_ACCURACY)
                            log += "${target.name}'s accuracy fell! (−${target.muddiedPct}%)"
                        }
                    }
                    if (actor.spellDrainPct > 0 && dmg > 0 && actor.alive && actor.hp < actor.maxHp) {
                        heal(actor, maxOf(1, dmg * actor.spellDrainPct / 100), "${actor.name}'s dark magic drains life")
                    }
                }
            }
            is BattleAction.UseItem -> {
                val target = combatant(action.targetKey)?.takeIf { !Combatant.isEnemy(it.key) } ?: return false
                // A berry for the foe: only when it can be tamed, and it doesn't stack.
                if (action.item.kind == ItemKind.BAIT && !canFeed(action.item)) return false
                if (!useItem(action.item)) return false
                when (action.item.kind) {
                    ItemKind.BAIT -> {
                        bait = action.item.power
                        log += "${actor.name} feeds a ${action.item.label} to ${enemy.name}. Your next Binding cast is ${action.item.power}x as likely to tame it!"
                    }
                    ItemKind.HEAL_HP -> {
                        if (!target.alive) return refund(action.item)
                        heal(target, GameEngine.potionHeal(action.item, target.maxHp), "${actor.name} uses ${action.item.label}")
                    }
                    ItemKind.HEAL_MP -> {
                        if (!target.isHero || !target.alive) return refund(action.item)
                        val before = target.mp
                        target.mp = minOf(target.maxMp, target.mp + action.item.power.toInt())
                        log += "${actor.name} uses ${action.item.label}: ${target.name} +${target.mp - before} Energy."
                    }
                    ItemKind.REVIVE -> {
                        if (target.alive) return refund(action.item)
                        target.hp = max(1, (target.maxHp * action.item.power).roundToInt())
                        log += "${actor.name} uses ${action.item.label}: ${target.name} is back on their feet!"
                    }
                    else -> return refund(action.item)
                }
            }
            is BattleAction.Capture -> {
                if (captureBlockedReason() != null) return false
                if (action.orb.kind != ItemKind.CAPTURE || !useItem(action.orb)) return false
                val chance = captureOdds(action.orb, action.grade)
                // A berry only lasts for one throw.
                bait = 1.0
                val critical = chance < 1.0 && rng.nextDouble() < CRITICAL_CATCH
                if (critical || rng.nextDouble() < chance) {
                    outcome = BattleOutcome.CAPTURED
                    capturedKey = enemy.key
                    if (critical) log += "Critical catch!"
                    log += "${actor.name} casts ${Binding.SPELL} with a ${action.orb.label}${traceNote(action)}... ${enemy.name} was tamed!"
                    if (enemies.count { it.alive } > 1) log += "The rest of the pack scatters!"
                } else {
                    log += "${actor.name} casts ${Binding.SPELL} with a ${action.orb.label}${traceNote(action)}... ${enemy.name} broke free!"
                }
            }
            is BattleAction.Swap -> if (!swapIn(action)) return false
            BattleAction.Flee -> {
                val chance = (0.5 + (actor.speed - enemy.speed) * 0.02).coerceIn(0.2, 0.95)
                if (rng.nextDouble() < chance) {
                    outcome = BattleOutcome.FLED
                    log += "You got away safely."
                } else {
                    log += "Couldn't escape!"
                }
            }
        }
        return true
    }

    /** A fallen monster on your side that a waiting swap could replace. */
    fun awaitsSwap(c: Combatant): Boolean =
        !c.alive && !c.isHero && !Combatant.isEnemy(c.key) && c in team && reserve.any { it.alive }

    /**
     * Replaces a monster that has just fainted with one from the swap slots,
     * without using anyone's turn (like sending in the next one after a faint).
     */
    fun replaceFainted(inUid: String, outKey: String): Boolean {
        if (outcome != BattleOutcome.ONGOING) return false
        val out = team.find { it.key == outKey } ?: return false
        if (out.alive || out.isHero) return false
        val wasTurn = current?.key == outKey
        if (!swapIn(BattleAction.Swap(inUid, outKey))) return false
        // If it was the fallen monster's turn, the newcomer takes it.
        if (wasTurn) current = team.find { it.key == inUid }
        return true
    }

    /** Sends a monster in from the swap slots, in place of a teammate (who goes to the swap slots) or into a free spot. */
    private fun swapIn(a: BattleAction.Swap): Boolean {
        val incoming = reserve.find { it.key == a.inUid && it.alive } ?: return false
        val outKey = a.outKey
        if (outKey == null) {
            if (team.size - 1 >= MAX_PARTY_SIZE) return false
            reserve.remove(incoming)
            team += incoming
            log += "Go, ${incoming.name}!"
        } else {
            val i = team.indexOfFirst { it.key == outKey }
            if (i <= 0) return false
            val out = team[i]
            team[i] = incoming
            reserve.remove(incoming)
            reserve += out
            queue.remove(outKey)
            log += if (out.alive) "${out.name}, come back! Go, ${incoming.name}!" else "${incoming.name} takes ${out.name}'s place!"
        }
        fought += incoming.key
        record(incoming, incoming, StrikeStyle.WARD, Element.NEUTRAL)
        return true
    }

    private fun useItem(item: Item): Boolean {
        if (itemsRemainingThisBattle <= 0 || count(item) <= 0) return false
        bag[item] = count(item) - 1
        itemsUsed[item] = (itemsUsed[item] ?: 0) + 1
        return true
    }

    private fun refund(item: Item): Boolean {
        bag[item] = count(item) + 1
        val used = (itemsUsed[item] ?: 1) - 1
        if (used <= 0) itemsUsed.remove(item) else itemsUsed[item] = used
        return false
    }

    private fun heal(target: Combatant, amount: Int, prefix: String) {
        val before = target.hp
        target.hp = minOf(target.maxHp, target.hp + amount)
        log += "$prefix: ${target.name} +${target.hp - before} HP."
        record(target, target, StrikeStyle.HEAL, Element.NEUTRAL, target.hp - before)
    }

    /** Accuracy check, including blinding effects. Logs a miss. */
    private fun rollHit(a: Combatant, d: Combatant, accuracy: Int): Boolean {
        if (a.missNext) {
            a.missNext = false
            a.accuracyDown = false
            log += "${a.name} misses!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        var chance = accuracy / 100.0 * (1 - a.muddiedPct / 100.0)
        if (a.accuracyDown) {
            chance *= 0.6
            a.accuracyDown = false
        }
        if (rng.nextDouble() >= chance) {
            log += "${a.name}'s attack misses ${d.name}!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        if (d.evasion > 0 && rng.nextDouble() < d.evasion) {
            log += "${d.name} nimbly dodges!"
            record(a, d, StrikeStyle.MISS, Element.NEUTRAL)
            return false
        }
        return true
    }

    private fun MoveEffect.targetsFoe(): Boolean = when (this) {
        is MoveEffect.Inflict, is MoveEffect.LowerFoe, is MoveEffect.FoeAccuracyDown, is MoveEffect.FoeMissesNext,
        is MoveEffect.Flinch, is MoveEffect.Scorch, is MoveEffect.Nightmare, MoveEffect.Scare -> true
        else -> false
    }

    private fun useMove(a: Combatant, d: Combatant, move: Move, diveStrike: Boolean = false) {
        val isProtect = MoveEffect.Protect in move.effects
        if (isProtect && a.usedProtectLast) {
            a.usedProtectLast = false
            log += "${a.name} tries ${move.name}, but it fails twice in a row!"
            return
        }
        a.usedProtectLast = isProtect

        if (MoveEffect.TwoTurnDive in move.effects && !diveStrike) {
            a.diving = move
            log += "${a.name} uses ${move.name} and soars out of reach!"
            return
        }

        val hitsFoe = move.isDamaging || move.effects.any { it.targetsFoe() }
        log += "${a.name} uses ${move.name}!"
        if (!hitsFoe) {
            applyEffects(a, d, move, onFoe = true, onSelf = true)
            return
        }
        // Spread moves strike every foe (a little weaker each when there's more than one).
        val targets = if (MoveEffect.HitsAllFoes in move.effects) foesOf(a).filter { it.alive }.ifEmpty { listOf(d) } else listOf(d)
        val mult = if (targets.size > 1) 0.8 else 1.0
        var landed = false
        for (t in targets) {
            if (hitTarget(a, t, move, mult)) {
                applyEffects(a, t, move, onFoe = true, onSelf = false)
                landed = true
            }
        }
        if (landed) applyEffects(a, d, move, onFoe = false, onSelf = true)
    }

    /** The other side, from [a]'s point of view. */
    private fun foesOf(a: Combatant): List<Combatant> = if (Combatant.isEnemy(a.key)) allies else enemies

    /** [a]'s own side (including [a]). */
    private fun teamOf(a: Combatant): List<Combatant> = if (Combatant.isEnemy(a.key)) enemies else allies

    /** One target of a move: dodges, guards, accuracy, then damage. Returns true if it connected. */
    private fun hitTarget(a: Combatant, t: Combatant, move: Move, mult: Double): Boolean {
        val pierce = MoveEffect.Pierce in move.effects
        if (t.diving != null && move.id != "thunder_bolt") {
            log += "${t.name} is high in the air. It can't be reached!"
            return false
        }
        if (t.protecting && !pierce) {
            log += "${t.name}'s guard blocks it!"
            return false
        }
        if (t.protecting) log += "${move.name} pierces ${t.name}'s guard!"
        val smack = move.effects.filterIsInstance<MoveEffect.SmackDown>().firstOrNull()
        if (floatsOver(a, t, move.type, smackDown = smack != null)) return false
        if (MoveEffect.SureHit !in move.effects && !rollHit(a, t, move.accuracy)) return false
        if (!move.isDamaging) return true
        if (t.diving != null) log += "Thunder Bolt strikes ${t.name} mid-dive!"
        val style = if (move.type == Element.NEUTRAL) StrikeStyle.SLASH else StrikeStyle.BOLT
        val crit = if (MoveEffect.HighCrit in move.effects) 0.25 else 0.0
        val multi = move.effects.filterIsInstance<MoveEffect.MultiHit>().firstOrNull()
        val hits = multi?.let { rng.nextInt(it.min, it.max + 1) } ?: 1
        // Smack Down hits a hovering foe harder and knocks it to the ground.
        val airborne = smack != null && t.immuneEarth
        // Status Bonus: hits harder when the foe is already asleep, shocked, poisoned...
        val bonus = move.effects.filterIsInstance<MoveEffect.StatusBonus>().firstOrNull()
            ?.takeIf { t.status != null }?.multiplier ?: 1.0
        val power = ((if (airborne) smack!!.groundedPower else move.power) * mult * bonus).roundToInt()
        val useDefense = MoveEffect.DefenseStrike in move.effects
        var count = 0
        var dealt = 0
        while (count < hits && t.alive) {
            dealt += strike(a, t, power, move.type, useMagic = false, verb = null, style = style, extraCrit = crit, pierce = pierce, useDefense = useDefense)
            count++
        }
        if (multi != null) log += "Hit $count time${if (count == 1) "" else "s"}!"
        if (airborne && t.alive) {
            t.immuneEarth = false
            t.borrowedHover = false
            t.earthResistPct = 0
            t.grounded = true
            log += "${t.name} is knocked out of the air! Earth moves can hit it now."
        }
        move.effects.filterIsInstance<MoveEffect.LifeDrain>().firstOrNull()?.let { fx ->
            if (a.alive && dealt > 0 && a.hp < a.maxHp) heal(a, maxOf(1, dealt * fx.percent / 100), "${a.name} drains the foe")
        }
        return true
    }

    /** Hover and Levitate: Earth attacks pass beneath. Logs it and returns true if [t] is out of reach. */
    private fun floatsOver(a: Combatant, t: Combatant, type: Element, smackDown: Boolean = false): Boolean {
        if (type != Element.EARTH || !t.immuneEarth || smackDown) return false
        log += "${t.name} floats above it. Earth attacks can't reach!"
        record(a, t, StrikeStyle.MISS, Element.NEUTRAL)
        return true
    }

    /** Applies [move]'s effects: those aimed at the foe [d] ([onFoe]) and/or those on the user and its team ([onSelf]). */
    private fun applyEffects(a: Combatant, d: Combatant, move: Move, onFoe: Boolean, onSelf: Boolean) {
        for (fx in move.effects) {
            if (if (fx.targetsFoe()) !onFoe else !onSelf) continue
            when (fx) {
                is MoveEffect.Inflict -> if (d.alive && d.status == null && rng.nextDouble() < fx.chance) {
                    val turns = rng.nextInt(fx.minTurns, fx.maxTurns + 1)
                    if (fx.status == Status.SLEEP) {
                        if (!fallAsleep(d, turns)) continue
                    } else {
                        d.status = fx.status
                        d.statusTurns = turns
                    }
                    log += when (fx.status) {
                        Status.SLEEP -> "${d.name} fell asleep!"
                        Status.SHOCK -> "${d.name} is shocked!"
                        Status.POISON -> "${d.name} is poisoned!"
                        Status.BURN -> "${d.name} is burned!"
                        Status.FROSTBITE -> "${d.name} has frostbite!"
                    }
                } else if (move.power == 0 && d.status != null) {
                    log += "${d.name} is already ${d.status!!.label.lowercase()}."
                }
                is MoveEffect.Nightmare -> if (d.alive && d.status == null && rng.nextDouble() < fx.chance) {
                    if (!fallAsleep(d, rng.nextInt(3, 6))) continue
                    d.nightmareDamage = scaled(fx.base, a.level)
                    log += "${d.name} falls into a nightmare!"
                } else {
                    log += "${d.name} resists the nightmare."
                }
                is MoveEffect.LowerFoe -> if (d.alive && d.oblivious) {
                    log += "${d.name} is oblivious. Its stats don't drop!"
                } else if (d.alive && rng.nextDouble() < fx.chance) {
                    fx.stats.forEach { d.stages[it] = ((d.stages[it] ?: 0) - 1).coerceAtLeast(-4) }
                    log += "${d.name}'s ${fx.stats.joinToString(", ") { it.label }} fell!"
                }
                is MoveEffect.SetWeather -> if (rng.nextDouble() < fx.chance && weather != fx.weather) {
                    weather = fx.weather
                    log += when (fx.weather) {
                        Weather.RAIN -> "It started to rain! Water and Electric moves are stronger."
                        Weather.DROUGHT -> "A drought sets in! Fire and Corrupt moves are stronger."
                        Weather.CLEAR -> "The weather cleared."
                    }
                }
                is MoveEffect.FoeAccuracyDown -> if (rng.nextDouble() < fx.chance) {
                    d.accuracyDown = true
                    log += "${d.name}'s accuracy fell!"
                }
                is MoveEffect.FoeMissesNext -> if (rng.nextDouble() < fx.chance) {
                    d.missNext = true
                    log += "${d.name} is thrown off balance!"
                }
                is MoveEffect.Flinch -> if (d.alive && rng.nextDouble() < fx.chance) d.flinch = true
                is MoveEffect.Scorch -> if (d.alive) {
                    d.scorchTurns = fx.turns
                    d.scorchDamage = scaled(fx.base, a.level)
                    d.scorchVerb = fx.verb
                    log += if (fx.verb == "is scorched") "${d.name} is scorched by lingering flames!" else "${d.name} is caught in it!"
                }
                MoveEffect.Protect -> {
                    a.protecting = true
                    log += "${a.name} raises a ${move.name}!"
                }
                is MoveEffect.RaiseSelf -> if (a.alive) {
                    fx.stats.forEach { a.stages[it] = ((a.stages[it] ?: 0) + fx.stages).coerceAtMost(4) }
                    log += "${a.name}'s ${fx.stats.joinToString(" and ") { it.label }} ${if (fx.stages > 1) "sharply rose" else "rose"}!"
                }
                MoveEffect.CureSelf -> if (a.status != null || a.scorchTurns > 0) {
                    a.status = null
                    a.statusTurns = 0
                    a.nightmareDamage = 0
                    a.scorchTurns = 0
                    log += "${a.name} shakes off its ailments!"
                }
                is MoveEffect.TeamHeal -> teamOf(a).filter { it.alive && it.hp < it.maxHp }.forEach {
                    heal(it, maxOf(1, it.maxHp * fx.percent / 100), "${a.name}'s ${move.name}")
                }
                is MoveEffect.TeamRaise -> {
                    teamOf(a).filter { it.alive }.forEach { c ->
                        fx.stats.forEach { c.stages[it] = ((c.stages[it] ?: 0) + fx.stages).coerceAtMost(4) }
                    }
                    log += "The whole team's ${fx.stats.joinToString(" and ") { it.label }} rose!"
                }
                is MoveEffect.MultiHit, MoveEffect.HighCrit, MoveEffect.SureHit, MoveEffect.HitsAllFoes, MoveEffect.Pierce, is MoveEffect.LifeDrain,
                is MoveEffect.SmackDown, is MoveEffect.StatusBonus, MoveEffect.DefenseStrike -> {}
                MoveEffect.Swift -> if (a.alive) {
                    a.swift = true
                    log += "${a.name} will move first next round!"
                }
                MoveEffect.Recharge -> a.mustRecharge = true
                is MoveEffect.HealSelf -> if (a.alive && a.hp < a.maxHp) {
                    heal(a, maxOf(1, a.maxHp * fx.percent / 100), "${a.name} restores itself")
                }
                is MoveEffect.Recoil -> if (rng.nextDouble() < fx.chance) hurt(a, scaled(fx.base, a.level), "is hurt by recoil")
                is MoveEffect.PowerUp -> {
                    a.powerUp += fx.percent
                    log += "${a.name}'s power rises! (+${a.powerUp}%)"
                }
                MoveEffect.Scare -> when {
                    !d.alive -> {}
                    d.oblivious -> log += "${d.name} is too oblivious to be scared."
                    d.scared -> log += "${d.name} is already shaken."
                    else -> {
                        d.scared = true
                        log += "${d.name} is scared! Its Attack drops to 67%."
                    }
                }
                MoveEffect.ReverseOrder -> {
                    reversedRounds = 4
                    log += "Wild winds swirl! The slowest now move first."
                }
                MoveEffect.TwoTurnDive -> {}
            }
        }
    }

    /** Weapon effects after the hero lands a hit: lifesteal and on-hit statuses (and Trickster feints on [basic] attacks). */
    private fun afterHeroHit(a: Combatant, d: Combatant, dmg: Int, basic: Boolean = false) {
        // A cursed weapon's power: frostbite, life drain or flame. Every hit wears the curse down.
        if (basic && dmg > 0) {
            a.weaponUses++
            when (a.weaponCurse) {
                Curse.FROST -> if (d.alive && d.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    d.status = Status.FROSTBITE
                    d.statusTurns = 4
                    log += "${d.name} has frostbite!"
                }
                Curse.FLAME -> if (d.alive && d.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    d.status = Status.BURN
                    d.statusTurns = 4
                    log += "${d.name} is set on fire!"
                }
                Curse.LIFE_DRAIN -> if (a.alive && a.hp < a.maxHp) heal(a, maxOf(1, dmg * CURSE_DRAIN_HEAL / 100), "${a.name}'s weapon drinks life")
                null -> {}
            }
        }
        if (basic && a.blindPct > 0 && d.alive && !d.accuracyDown && rng.nextInt(100) < a.blindPct) {
            d.accuracyDown = true
            log += "${a.name} feints! ${d.name} is thrown off balance."
        }
        if (a.lifesteal > 0 && dmg > 0 && a.alive) {
            heal(a, maxOf(1, (dmg * a.lifesteal).roundToInt()), "${a.name}'s weapon drains life")
        }
        val s = a.onHitStatus
        if (s != null && d.alive && d.status == null && rng.nextDouble() < 0.15) {
            d.status = s
            d.statusTurns = 4
            log += "${d.name} is ${s.label.lowercase()}!"
        }
    }

    private fun strike(
        a: Combatant, d: Combatant, power: Int, type: Element, useMagic: Boolean, verb: String?, style: StrikeStyle,
        extraCrit: Double = 0.0,
        /** Ignores the target's raised Defense and its brace. */
        pierce: Boolean = false,
        /** Defense Strike: the user's Defense does the hitting. */
        useDefense: Boolean = false,
    ): Int {
        val atk = when {
            useMagic -> a.magic.toDouble()
            useDefense -> a.defense * a.stageMultiplier(BattleStat.DEFENSE)
            else -> a.attack * a.stageMultiplier(BattleStat.ATTACK) * (if (a.scared) SCARED_ATTACK else 1.0)
        }
        val defStage = d.stageMultiplier(BattleStat.DEFENSE).let { if (pierce) minOf(it, 1.0) else it }
        val def = d.defense * defStage
        val typeMult = type.multiplierAgainst(d.types)
        val sameType = if (type != Element.NEUTRAL && type in a.types) 1.2 else 1.0
        val crit = rng.nextDouble() < 0.06 + a.critBonus + extraCrit
        val raw = power * atk / (atk + def) *
            (1 + a.level * 0.08) *
            typeMult * sameType * weather.multiplier(type) *
            (1 + a.powerUp / 100.0) *
            (0.85 + rng.nextDouble() * 0.15) *
            (if (crit) 1.5 + a.critDamagePct / 100.0 else 1.0) *
            (if (d.defending && !pierce) 0.5 else 1.0) *
            // Subclass perks and skills.
            (if (useMagic) 1 + a.spellPct / 100.0 else 1.0) *
            (1 + a.ragePct / 100.0 * (1 - a.hpFraction)) *
            (1 - d.hidePct / 100.0) *
            (if (type == Element.EARTH) 1 - d.earthResistPct / 100.0 else 1.0)
        val dmg = max(1, raw.roundToInt())
        val absorbed = minOf(d.shield, dmg)
        d.shield -= absorbed
        d.hp = max(0, d.hp - (dmg - absorbed))
        record(a, d, style, type, dmg, crit, typeMult > 1.0)
        val notes = buildList {
            if (absorbed > 0) add(if (d.shield > 0) "The ward absorbs $absorbed." else "The ward absorbs $absorbed and shatters!")
            if (crit) add("Critical!")
            if (typeMult > 1.0) add("Super effective!")
            if (typeMult < 1.0) add("Not very effective...")
        }.joinToString(" ")
        log += (if (verb != null) "${a.name} $verb ${d.name} for $dmg damage. $notes" else "It hits ${d.name} for $dmg damage. $notes").trim()
        if (!d.alive) log += "${d.name} falls!"
        secondWind(d)
        // The team's borrowed hover breaks when the hero is hit (the hero keeps hovering).
        if (d.isHero && dmg > 0) breakTeamHover(d)
        // Cursed armor strikes back at physical attackers (and every hit wears the curse down).
        if (!useMagic && d.isHero && dmg > 0) {
            d.armorHits++
            for (c in d.armorCurses) when (c) {
                Curse.FROST -> if (a.alive && a.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    a.status = Status.FROSTBITE
                    a.statusTurns = 4
                    log += "${d.name}'s frozen armor gives ${a.name} frostbite!"
                }
                Curse.FLAME -> if (a.alive && a.status == null && rng.nextInt(100) < CURSE_STATUS_PCT) {
                    a.status = Status.BURN
                    a.statusTurns = 4
                    log += "${d.name}'s burning armor sets ${a.name} alight!"
                }
                Curse.LIFE_DRAIN -> if (a.alive && d.alive) {
                    val stolen = maxOf(1, dmg * CURSE_ARMOR_STEAL / 100).coerceAtMost(a.hp)
                    a.hp -= stolen
                    d.hp = minOf(d.maxHp, d.hp + stolen)
                    log += "${d.name}'s armor steals $stolen HP from ${a.name}."
                    if (!a.alive) log += "${a.name} falls!"
                }
            }
        }
        // Static, Flame Body and Poison Point punish whoever strikes in close.
        val touch = d.touchStatus
        if (touch != null && !useMagic && a.alive && a.status == null && rng.nextInt(100) < d.touchPct) {
            a.status = touch
            a.statusTurns = 3
            log += "${d.name}'s ${d.traits.keys.first { it in TOUCH_TRAITS }.label}: ${a.name} is ${touch.label.lowercase()}!"
        }
        return dmg
    }

    private fun pickTarget(): Combatant? {
        val targets = allies.filter { it.alive }
        if (targets.isEmpty()) return null
        val reachable = targets.filter { it.diving == null }.ifEmpty { targets }
        return if (rng.nextDouble() < 0.35) reachable.minBy { it.hpFraction } else reachable.random(rng)
    }

    /** Enemy AI: usually its best damaging move, sometimes a useful status move. */
    private fun enemyTurn(enemy: Combatant) {
        val target = pickTarget() ?: return
        val usable = enemy.moves.filter { m ->
            when {
                MoveEffect.Protect in m.effects -> !enemy.usedProtectLast && enemy.hpFraction < 0.5
                m.isDamaging -> true
                m.effects.any { it is MoveEffect.Inflict || it is MoveEffect.Nightmare } -> target.status == null
                m.effects.any { it is MoveEffect.SetWeather } -> m.effects.filterIsInstance<MoveEffect.SetWeather>().none { it.weather == weather }
                m.effects.contains(MoveEffect.ReverseOrder) -> reversedRounds == 0
                m.effects.contains(MoveEffect.CureSelf) && m.effects.none { it is MoveEffect.RaiseSelf } -> enemy.status != null
                m.effects.any { it is MoveEffect.HealSelf } && !m.isDamaging -> enemy.hpFraction < 0.6
                m.effects.any { it is MoveEffect.TeamHeal } -> enemies.any { it.alive && it.hpFraction < 0.6 }
                m.effects.any { it is MoveEffect.RaiseSelf } -> m.effects.filterIsInstance<MoveEffect.RaiseSelf>().any { r -> r.stats.any { (enemy.stages[it] ?: 0) < 2 } }
                else -> true
            }
        }.ifEmpty { listOf(Moves.get("tackle")) }
        val move = if (rng.nextDouble() < 0.7) {
            usable.maxBy { m ->
                val smack = m.effects.filterIsInstance<MoveEffect.SmackDown>().firstOrNull()
                when {
                    !m.isDamaging -> 25.0
                    // Earth moves pass under a hovering target, except Smack Down, which hits harder.
                    m.type == Element.EARTH && target.immuneEarth -> if (smack != null) smack.groundedPower * m.type.multiplierAgainst(target.types) else 0.0
                    else -> m.expectedPower * m.type.multiplierAgainst(target.types)
                }
            }
        } else {
            usable.random(rng)
        }
        useMove(enemy, target, move)
    }

    // ---- result -------------------------------------------------------------

    fun result(): BattleResult {
        check(outcome != BattleOutcome.ONGOING) { "Battle still in progress" }
        val lvl = enemies.maxOf { it.level }
        val bossMult = if (isBoss) 3 else 1
        val rewardMult = when (encounter) {
            EncounterType.WILD -> TeamScaling.reward(teamSize) * TeamScaling.packReward(enemies.size)
            EncounterType.DEN -> TeamScaling.reward(teamSize) * Dens.REWARD_MULTIPLIER
            // A thug's crew grows with your team, so the pay follows its size.
            EncounterType.THUG -> enemies.size * THUG_REWARD
            else -> TeamScaling.bossReward(teamSize)
        }
        // Wayfarer, Scholar and Treasure Hunter add to the spoils.
        val xpMult = 1 + heroPerks.xpPct / 100.0
        val heroXp = when (outcome) {
            BattleOutcome.WON -> lvl * 3 * bossMult
            BattleOutcome.CAPTURED -> GameEngine.captureXp(lvl) * bossMult
            else -> 0
        }.let { (it * rewardMult * xpMult).roundToInt() }
        val gold = if (outcome == BattleOutcome.WON) {
            ((lvl * 3 + rng.nextInt(lvl * 2 + 1)) * (if (isBoss) 4 else 1) * rewardMult * (1 + heroPerks.goldPct / 100.0)).roundToInt()
        } else {
            0
        }
        val monsterXp = if (outcome == BattleOutcome.WON || outcome == BattleOutcome.CAPTURED) {
            partyUids.associateWith { uid ->
                val base = (lvl * 12 * (if (isBoss) 2 else 1) * rewardMult * xpMult).roundToInt()
                // Swap-slot monsters that never stepped in get half, like those who fell.
                if (uid in fought && combatant(uid)?.alive == true) base else base / 2
            }
        } else {
            emptyMap()
        }
        val drops = mutableMapOf<Item, Int>()
        if (outcome == BattleOutcome.WON) {
            if (encounter == EncounterType.SILVER_BOSS) {
                drops[Item.GREAT_ORB] = 2
                drops[Item.HI_POTION] = 1
            } else if (isBoss) {
                drops[Item.MASTER_ORB] = 1
                drops[Item.HI_POTION] = 2
            } else {
                // Stronger (team-scaled) monsters drop more, and better.
                if (rng.nextDouble() < (0.25 * rewardMult).coerceAtMost(0.9)) {
                    drops[listOf(Item.POTION, Item.TAMING_ORB, Item.ETHER, Item.SWEET_BERRY).random(rng)] = 1
                    // Now and then a wild monster drops a rare Golden Berry.
                    if (rng.nextDouble() < 0.03) drops[Item.GOLDEN_BERRY] = 1
                }
                if (teamSize >= 3 && rng.nextDouble() < (0.2 * (teamSize - 2)).coerceAtMost(0.75)) {
                    val bonus = if (rng.nextDouble() < 0.04 * (teamSize - 1)) Item.RESTORE_POTION
                    else listOf(Item.HI_POTION, Item.GREAT_ORB, Item.PHOENIX_DOWN).random(rng)
                    drops[bonus] = (drops[bonus] ?: 0) + 1
                }
            }
        }
        val captured = if (outcome == BattleOutcome.CAPTURED) monsterFor(capturedKey ?: Combatant.ENEMY_KEY).copy(uid = newId(rng), xp = 0) else null
        val fainted = partyUids.filter { combatant(it)?.alive == false }.toSet()
        // Damage carries over: remember how hurt everyone still standing is.
        val damage = (allies + reserve).filter { it.alive }.associate { it.key to (it.maxHp - it.hp) }
        val heroFell = allies.first().let { !it.alive }
        return BattleResult(
            outcome, encounter, captured ?: enemyMonster, heroXp, gold, monsterXp, itemsUsed.toMap(), drops, captured, shrineId, fainted,
            damage, heroFell, allies.first().let { it.maxMp - it.mp },
            allies.first().weaponUses, allies.first().armorHits,
            if (outcome == BattleOutcome.WON) enemyMonsters.map { it.speciesId } else emptyList(),
            standing = allies.count { !it.isHero && it.alive },
        )
    }
}
