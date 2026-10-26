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
    BOX_BOSS;

    /** Enemy HP multiplier. */
    val hpMultiplier: Double
        get() = when (this) {
            WILD -> 1.0
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
    data class Capture(val orb: Item) : BattleAction
    data object Flee : BattleAction
}

class Combatant(
    /** "hero", "enemy", or the monster's uid. */
    val key: String,
    val name: String,
    val emoji: String,
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
    /** Chance to dodge incoming attacks (Rogue hero). */
    var evasion: Double = 0.0
    /** Type of the hero's basic Attack (from their weapon). */
    var attackElement: Element = Element.NEUTRAL
    var defending: Boolean = false

    // ---- conditions ----
    var status: Status? = null
    var statusTurns: Int = 0
    var nightmareDamage: Int = 0
    var scorchTurns: Int = 0
    var scorchDamage: Int = 0
    /** Next move has a 40% chance to miss. */
    var accuracyDown: Boolean = false
    /** Next move misses. */
    var missNext: Boolean = false
    var flinch: Boolean = false
    var mustRecharge: Boolean = false
    var protecting: Boolean = false
    var usedProtectLast: Boolean = false
    /** Mid-dive (Dive Bomb turn 1): out of reach until it strikes. */
    var diving: Move? = null
    val stages: MutableMap<BattleStat, Int> = mutableMapOf()
    /** Steel Wing stacks, in percent. */
    var powerUp: Int = 0
    var scared: Boolean = false

    val alive: Boolean get() = hp > 0
    val hpFraction: Double get() = hp.toDouble() / maxHp

    fun stageMultiplier(stat: BattleStat): Double = (1.0 + 0.15 * (stages[stat] ?: 0)).coerceIn(0.4, 2.0)
    val effectiveSpeed: Double get() = speed * stageMultiplier(BattleStat.SPEED)

    /** Short labels for the UI, e.g. "💤 Asleep 2", "Def ↓1", "🌧️". */
    fun tags(): List<String> = buildList {
        status?.let { add("${it.emoji} ${it.label}") }
        if (scorchTurns > 0) add("🔥 Scorched")
        stages.forEach { (s, n) -> if (n != 0) add("${s.label.take(3)} ${if (n < 0) "↓" else "↑"}${kotlin.math.abs(n)}") }
        if (powerUp > 0) add("Power +$powerUp%")
        if (scared) add("😨 Scared")
        if (diving != null) add("🪽 In the air")
        if (protecting) add("🛡️ Guarding")
        if (mustRecharge) add("Recharging")
        if (accuracyDown || missNext) add("🎯 Blinded")
    }

    companion object {
        const val HERO_KEY = "hero"
        const val ENEMY_KEY = "enemy"

        fun fromHero(h: Hero, weapon: Weapon? = null) = Combatant(
            HERO_KEY, h.name, h.heroClass.emoji, Element.NEUTRAL, h.level, h.maxHp, h.maxHp,
            h.attack + (weapon?.attack ?: 0), h.magic + (weapon?.magic ?: 0),
            h.defense, h.speed, h.maxMp, h.maxMp, true, emptyList(),
        ).apply {
            attackElement = weapon?.element ?: Element.NEUTRAL
            evasion = h.evasion
        }

        fun fromMonster(m: Monster, key: String = m.uid, hpMultiplier: Double = 1.0): Combatant {
            val s = m.stats
            val hp = (s.hp * hpMultiplier).toInt()
            return Combatant(
                key, m.displayName, m.species.emoji, m.species.element, m.level, hp, hp,
                s.attack, s.attack, s.defense, s.speed, 0, 0, false, m.activeMoves,
            )
        }
    }
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
)

/** Chance (0..1) that throwing [orb] tames the enemy right now. 0 = not allowed. */
fun captureChance(hpFraction: Double, rarity: Rarity, levelDiff: Int, orb: Item): Double {
    if (hpFraction > 0.5) return 0.0
    val base = (1.0 - hpFraction * 1.6) * 0.9
    val levelPenalty = 0.92.pow(max(0, levelDiff))
    return (base * rarity.captureMultiplier * orb.power * levelPenalty).coerceIn(0.03, 0.95)
}

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
    weapon: Weapon? = null,
) {
    private val fighters = party.filter { !it.fainted }.take(MAX_PARTY_SIZE)
    val allies: List<Combatant> = listOf(Combatant.fromHero(hero, weapon)) + fighters.map { Combatant.fromMonster(it) }
    val enemy: Combatant = Combatant.fromMonster(enemyMonster, Combatant.ENEMY_KEY, encounter.hpMultiplier)
    private val heroLevel = hero.level
    private val heroSkills = Spells.all.filter { it.id in hero.knownSpells }
    private val partyUids = fighters.map { it.uid }

    private val bag: MutableMap<Item, Int> = inventory.toMutableMap()
    private val itemsUsed = mutableMapOf<Item, Int>()
    val itemsUsedCount: Int get() = itemsUsed.values.sum()
    val itemsRemainingThisBattle: Int get() = MAX_ITEMS_PER_BATTLE - itemsUsedCount

    val log = mutableListOf<String>()
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

    val isBoss: Boolean get() = encounter != EncounterType.WILD
    val captureLocked: Boolean get() = encounter == EncounterType.BOSS_CHALLENGE

    init {
        val intro = when (encounter) {
            EncounterType.WILD -> "A wild ${enemy.name} (Lv ${enemy.level}) appears!"
            EncounterType.BOSS_CHALLENGE -> "BOSS: ${enemy.name} (Lv ${enemy.level}) guards this shrine! Defeat it to earn the right to tame it."
            EncounterType.BOSS_TAMING -> "BOSS: ${enemy.name} (Lv ${enemy.level}) remembers you. Weaken it and try to tame it!"
            EncounterType.SILVER_BOSS -> "SILVER GUARDIAN: ${enemy.name} (Lv ${enemy.level}) blocks your path! It's hard to tame, but it has perfect genes."
            EncounterType.BOX_BOSS -> "The loot box bursts open... ${enemy.name} (Lv ${enemy.level}) erupts out of it!"
        }
        log += intro
        advance()
    }

    fun combatant(key: String): Combatant? =
        if (key == Combatant.ENEMY_KEY) enemy else allies.find { it.key == key }

    fun count(item: Item): Int = bag[item] ?: 0

    // ---- queries for the UI -------------------------------------------------

    fun spellsFor(actor: Combatant): List<Skill> = if (actor.isHero) heroSkills else emptyList()

    fun battleItems(): List<Item> =
        Item.entries.filter { it.usableInBattle && it.kind != ItemKind.CAPTURE && count(it) > 0 }

    fun orbs(): List<Item> = Item.entries.filter { it.kind == ItemKind.CAPTURE && count(it) > 0 }

    /** Null when capture is allowed, otherwise a reason to show the player. */
    fun captureBlockedReason(): String? = when {
        captureLocked -> "Defeat this boss first. Then it can be tamed on a later visit."
        storageFull -> "Monster storage is full. Release a monster or upgrade storage."
        orbs().isEmpty() -> "No orbs left."
        itemsRemainingThisBattle <= 0 -> "Item limit reached for this battle ($MAX_ITEMS_PER_BATTLE)."
        enemy.hpFraction > 0.5 -> "Too strong to tame. Get it below 50% HP."
        else -> null
    }

    /** Status conditions (sleep, shock, poison, burn) make taming easier. */
    fun captureOdds(orb: Item): Double =
        (
            captureChance(enemy.hpFraction, enemyMonster.species.rarity, enemy.level - heroLevel, orb) *
                encounter.captureMultiplier * (enemy.status?.captureBonus ?: 1.0)
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
            if (!c.alive) continue
            val canAct = beginTurn(c)
            checkEnd()
            if (!canAct || outcome != BattleOutcome.ONGOING) continue
            if (key == Combatant.ENEMY_KEY) {
                enemyTurn()
                checkEnd()
            } else {
                current = c
                return
            }
        }
        current = null
    }

    private fun startRound() {
        round++
        if (reversedRounds > 0) reversedRounds--
        val everyone = (allies + enemy).filter { it.alive }
        val bySpeed = everyone.sortedByDescending { it.effectiveSpeed + rng.nextDouble() }
        val order = (if (reversedRounds > 0) bySpeed.reversed() else bySpeed).map { it.key }
        queue.addAll(order)
        // Bosses act twice per round.
        if (isBoss) queue.addLast(Combatant.ENEMY_KEY)
    }

    /** Damage of a fixed "base" amount, scaled by level like normal attacks. */
    private fun scaled(base: Int, level: Int) = (base * (1 + level * 0.08)).roundToInt()

    private fun hurt(c: Combatant, amount: Int, text: String) {
        c.hp = max(0, c.hp - amount)
        log += "${c.name} $text ($amount)."
        if (!c.alive) log += "${c.name} falls!"
    }

    /** Ticks conditions at the start of a fighter's turn. Returns false if it loses the turn. */
    private fun beginTurn(c: Combatant): Boolean {
        c.defending = false
        c.protecting = false
        if (c.scorchTurns > 0) {
            c.scorchTurns--
            hurt(c, c.scorchDamage, "is scorched")
        }
        when (c.status) {
            Status.POISON -> hurt(c, max(1, c.maxHp / 10), "is hurt by poison")
            Status.BURN -> hurt(c, max(1, c.maxHp * 6 / 100), "is hurt by its burn")
            else -> {}
        }
        if (c.status == Status.POISON || c.status == Status.BURN) tickStatus(c)
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
            val target = if (c.key == Combatant.ENEMY_KEY) pickTarget() else enemy
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
        if (!enemy.alive) {
            outcome = BattleOutcome.WON
            log += "${enemy.name} is defeated!"
        } else if (allies.none { it.alive }) {
            outcome = BattleOutcome.LOST
            log += "Your party has fallen..."
        }
    }

    /** Returns false if the action was invalid (turn not used). */
    private fun perform(actor: Combatant, action: BattleAction): Boolean {
        when (action) {
            BattleAction.Attack -> {
                if (actor.isHero) {
                    if (!rollHit(actor, enemy, 100)) return true
                    strike(actor, enemy, 40, actor.attackElement, useMagic = false, verb = "attacks")
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
                if (skill.kind == SkillKind.HEAL) {
                    val target = combatant(action.targetKey ?: actor.key)?.takeIf { it.alive && it.key != Combatant.ENEMY_KEY }
                        ?: return false
                    actor.mp -= skill.mpCost
                    heal(target, (skill.power + actor.magic * 1.5).roundToInt(), "${actor.name} casts ${skill.name}")
                } else {
                    actor.mp -= skill.mpCost
                    if (!rollHit(actor, enemy, 100)) return true
                    strike(actor, enemy, skill.power, skill.element, useMagic = true, verb = "casts ${skill.name} on")
                }
            }
            is BattleAction.UseItem -> {
                val target = combatant(action.targetKey)?.takeIf { it.key != Combatant.ENEMY_KEY } ?: return false
                if (!useItem(action.item)) return false
                when (action.item.kind) {
                    ItemKind.HEAL_HP -> {
                        if (!target.alive) return refund(action.item)
                        heal(target, max(50, (target.maxHp * action.item.power).roundToInt()), "${actor.name} uses ${action.item.label}")
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
                val chance = captureOdds(action.orb)
                if (rng.nextDouble() < chance) {
                    outcome = BattleOutcome.CAPTURED
                    log += "${actor.name} throws a ${action.orb.label}... ${enemy.name} was tamed!"
                } else {
                    log += "${actor.name} throws a ${action.orb.label}... ${enemy.name} broke free!"
                }
            }
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
    }

    /** Accuracy check, including blinding effects. Logs a miss. */
    private fun rollHit(a: Combatant, d: Combatant, accuracy: Int): Boolean {
        if (a.missNext) {
            a.missNext = false
            a.accuracyDown = false
            log += "${a.name} misses!"
            return false
        }
        var chance = accuracy / 100.0
        if (a.accuracyDown) {
            chance *= 0.6
            a.accuracyDown = false
        }
        if (rng.nextDouble() >= chance) {
            log += "${a.name}'s attack misses ${d.name}!"
            return false
        }
        if (d.evasion > 0 && rng.nextDouble() < d.evasion) {
            log += "${d.name} nimbly dodges!"
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
        if (hitsFoe) {
            log += "${a.name} uses ${move.name}!"
            if (d.diving != null && move.id != "thunder_bolt") {
                log += "${d.name} is high in the air. It can't be reached!"
                return
            }
            if (d.protecting) {
                log += "${d.name}'s Wave Guard blocks it!"
                return
            }
            if (!rollHit(a, d, move.accuracy)) return
            if (move.isDamaging) {
                if (d.diving != null) log += "Thunder Bolt strikes ${d.name} mid-dive!"
                strike(a, d, move.power, move.type, useMagic = false, verb = null)
            }
        } else {
            log += "${a.name} uses ${move.name}!"
        }
        applyEffects(a, d, move)
    }

    private fun applyEffects(a: Combatant, d: Combatant, move: Move) {
        for (fx in move.effects) {
            when (fx) {
                is MoveEffect.Inflict -> if (d.alive && d.status == null && rng.nextDouble() < fx.chance) {
                    d.status = fx.status
                    d.statusTurns = rng.nextInt(fx.minTurns, fx.maxTurns + 1)
                    log += when (fx.status) {
                        Status.SLEEP -> "${d.name} fell asleep!"
                        Status.SHOCK -> "${d.name} is shocked!"
                        Status.POISON -> "${d.name} is poisoned!"
                        Status.BURN -> "${d.name} is burned!"
                    }
                } else if (move.power == 0 && d.status != null) {
                    log += "${d.name} is already ${d.status!!.label.lowercase()}."
                }
                is MoveEffect.Nightmare -> if (d.alive && d.status == null && rng.nextDouble() < fx.chance) {
                    d.status = Status.SLEEP
                    d.statusTurns = rng.nextInt(3, 6)
                    d.nightmareDamage = scaled(fx.base, a.level)
                    log += "${d.name} falls into a nightmare!"
                } else {
                    log += "${d.name} resists the nightmare."
                }
                is MoveEffect.LowerFoe -> if (d.alive && rng.nextDouble() < fx.chance) {
                    fx.stats.forEach { d.stages[it] = ((d.stages[it] ?: 0) - 1).coerceAtLeast(-4) }
                    log += "${d.name}'s ${fx.stats.joinToString(", ") { it.label }} fell!"
                }
                is MoveEffect.SetWeather -> if (rng.nextDouble() < fx.chance && weather != fx.weather) {
                    weather = fx.weather
                    log += when (fx.weather) {
                        Weather.RAIN -> "🌧️ It started to rain! Water and Electric moves are stronger."
                        Weather.DROUGHT -> "☀️ A drought sets in! Fire and Corrupt moves are stronger."
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
                    log += "${d.name} is scorched by lingering flames!"
                }
                MoveEffect.Protect -> {
                    a.protecting = true
                    log += "${a.name} raises a Wave Guard!"
                }
                MoveEffect.Recharge -> a.mustRecharge = true
                is MoveEffect.Recoil -> if (rng.nextDouble() < fx.chance) hurt(a, scaled(fx.base, a.level), "is hurt by recoil")
                is MoveEffect.PowerUp -> {
                    a.powerUp += fx.percent
                    log += "${a.name}'s power rises! (+${a.powerUp}%)"
                }
                MoveEffect.Scare -> {
                    d.scared = true
                    log += "${d.name} is scared and will deal less damage!"
                }
                MoveEffect.ReverseOrder -> {
                    reversedRounds = 4
                    log += "🌀 Wild winds swirl! The slowest now move first."
                }
                MoveEffect.TwoTurnDive -> {}
            }
        }
    }

    private fun strike(a: Combatant, d: Combatant, power: Int, type: Element, useMagic: Boolean, verb: String?) {
        val atk = (if (useMagic) a.magic.toDouble() else a.attack * a.stageMultiplier(BattleStat.ATTACK))
        val def = d.defense * d.stageMultiplier(BattleStat.DEFENSE)
        val typeMult = type.multiplierAgainst(d.element)
        val sameType = if (type != Element.NEUTRAL && type == a.element) 1.2 else 1.0
        val crit = rng.nextDouble() < 0.06
        val raw = power * atk / (atk + def) *
            (1 + a.level * 0.08) *
            typeMult * sameType * weather.multiplier(type) *
            (1 + a.powerUp / 100.0) *
            (if (a.scared) 0.75 else 1.0) *
            (0.85 + rng.nextDouble() * 0.15) *
            (if (crit) 1.5 else 1.0) *
            (if (d.defending) 0.5 else 1.0)
        val dmg = max(1, raw.roundToInt())
        d.hp = max(0, d.hp - dmg)
        val notes = buildList {
            if (crit) add("Critical!")
            if (typeMult > 1.0) add("Super effective!")
            if (typeMult < 1.0) add("Not very effective...")
        }.joinToString(" ")
        log += (if (verb != null) "${a.name} $verb ${d.name} for $dmg damage. $notes" else "It hits ${d.name} for $dmg damage. $notes").trim()
        if (!d.alive) log += "${d.name} falls!"
    }

    private fun pickTarget(): Combatant? {
        val targets = allies.filter { it.alive }
        if (targets.isEmpty()) return null
        val reachable = targets.filter { it.diving == null }.ifEmpty { targets }
        return if (rng.nextDouble() < 0.35) reachable.minBy { it.hpFraction } else reachable.random(rng)
    }

    /** Enemy AI: usually its best damaging move, sometimes a useful status move. */
    private fun enemyTurn() {
        val target = pickTarget() ?: return
        val usable = enemy.moves.filter { m ->
            when {
                MoveEffect.Protect in m.effects -> !enemy.usedProtectLast && enemy.hpFraction < 0.5
                m.isDamaging -> true
                m.effects.any { it is MoveEffect.Inflict || it is MoveEffect.Nightmare } -> target.status == null
                m.effects.any { it is MoveEffect.SetWeather } -> m.effects.filterIsInstance<MoveEffect.SetWeather>().none { it.weather == weather }
                m.effects.contains(MoveEffect.Scare) -> !target.scared
                m.effects.contains(MoveEffect.ReverseOrder) -> reversedRounds == 0
                else -> true
            }
        }.ifEmpty { listOf(Moves.get("tackle")) }
        val move = if (rng.nextDouble() < 0.7) {
            usable.maxBy { m -> if (m.isDamaging) m.power * m.type.multiplierAgainst(target.element) * m.accuracy / 100 else 25.0 }
        } else {
            usable.random(rng)
        }
        useMove(enemy, target, move)
    }

    // ---- result -------------------------------------------------------------

    fun result(): BattleResult {
        check(outcome != BattleOutcome.ONGOING) { "Battle still in progress" }
        val lvl = enemy.level
        val bossMult = if (isBoss) 3 else 1
        val heroXp = when (outcome) {
            BattleOutcome.WON -> lvl * 3 * bossMult
            BattleOutcome.CAPTURED -> lvl * 2 * bossMult
            else -> 0
        }
        val gold = if (outcome == BattleOutcome.WON) (lvl * 3 + rng.nextInt(lvl * 2 + 1)) * (if (isBoss) 4 else 1) else 0
        val monsterXp = if (outcome == BattleOutcome.WON || outcome == BattleOutcome.CAPTURED) {
            partyUids.associateWith { uid ->
                val base = lvl * 12 * (if (isBoss) 2 else 1)
                if (combatant(uid)?.alive == true) base else base / 2
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
            } else if (rng.nextDouble() < 0.25) {
                drops[listOf(Item.POTION, Item.TAMING_ORB, Item.ETHER).random(rng)] = 1
            }
        }
        val captured = if (outcome == BattleOutcome.CAPTURED) enemyMonster.copy(uid = newId(rng), xp = 0) else null
        val fainted = partyUids.filter { combatant(it)?.alive == false }.toSet()
        return BattleResult(
            outcome, encounter, enemyMonster, heroXp, gold, monsterXp, itemsUsed.toMap(), drops, captured, shrineId, fainted,
        )
    }
}
