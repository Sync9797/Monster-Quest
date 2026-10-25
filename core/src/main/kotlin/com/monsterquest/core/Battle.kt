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
}

enum class BattleOutcome { ONGOING, WON, LOST, CAPTURED, FLED }

sealed interface BattleAction {
    data object Attack : BattleAction
    data object Defend : BattleAction
    data class CastSpell(val skillId: String, val targetKey: String? = null) : BattleAction
    data object Special : BattleAction
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
    val special: Skill?,
) {
    var cooldown: Int = 0
    var defending: Boolean = false
    val alive: Boolean get() = hp > 0
    val hpFraction: Double get() = hp.toDouble() / maxHp

    companion object {
        const val HERO_KEY = "hero"
        const val ENEMY_KEY = "enemy"
        const val SPECIAL_COOLDOWN = 3
        const val BOSS_HP_MULTIPLIER = 2

        fun fromHero(h: Hero) = Combatant(
            HERO_KEY, h.name, "🧙", Element.NEUTRAL, h.level, h.maxHp, h.maxHp,
            h.attack, h.magic, h.defense, h.speed, h.maxMp, h.maxMp, true, null,
        )

        /** Bosses get [BOSS_HP_MULTIPLIER] times the HP. */
        fun fromMonster(m: Monster, key: String = m.uid, boss: Boolean = false): Combatant {
            val s = m.stats
            val hp = if (boss) s.hp * BOSS_HP_MULTIPLIER else s.hp
            return Combatant(
                key, m.displayName, m.species.emoji, m.species.element, m.level, hp, hp,
                s.attack, s.magic, s.defense, s.speed, 0, 0, false, m.species.special,
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
) {
    val allies: List<Combatant> = listOf(Combatant.fromHero(hero)) +
        party.take(MAX_PARTY_SIZE).map { Combatant.fromMonster(it) }
    val enemy: Combatant = Combatant.fromMonster(enemyMonster, Combatant.ENEMY_KEY, boss = encounter != EncounterType.WILD)
    private val heroLevel = hero.level
    private val heroSkills = HeroSkills.unlockedAt(hero.level)
    private val partyUids = party.take(MAX_PARTY_SIZE).map { it.uid }

    private val bag: MutableMap<Item, Int> = inventory.toMutableMap()
    private val itemsUsed = mutableMapOf<Item, Int>()
    val itemsUsedCount: Int get() = itemsUsed.values.sum()
    val itemsRemainingThisBattle: Int get() = MAX_ITEMS_PER_BATTLE - itemsUsedCount

    val log = mutableListOf<String>()
    var outcome: BattleOutcome = BattleOutcome.ONGOING
        private set
    var round: Int = 0
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

    fun captureOdds(orb: Item): Double =
        captureChance(enemy.hpFraction, enemyMonster.species.rarity, enemy.level - heroLevel, orb)

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
            c.defending = false
            if (c.cooldown > 0) c.cooldown--
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
        val everyone = (allies + enemy).filter { it.alive }
        // Sort by speed with a small random tiebreak.
        val order = everyone.sortedByDescending { it.speed + rng.nextDouble() }.map { it.key }
        queue.addAll(order)
        // Bosses act twice per round.
        if (isBoss) queue.addLast(Combatant.ENEMY_KEY)
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
                strike(actor, enemy, 20, SkillKind.PHYSICAL, Element.NEUTRAL, "attacks")
            }
            BattleAction.Defend -> {
                actor.defending = true
                log += "${actor.name} braces for impact."
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
                    strike(actor, enemy, skill.power, skill.kind, skill.element, "casts ${skill.name} on")
                }
            }
            BattleAction.Special -> {
                val skill = actor.special ?: return false
                if (actor.cooldown > 0) return false
                actor.cooldown = Combatant.SPECIAL_COOLDOWN
                strike(actor, enemy, skill.power, skill.kind, skill.element, "uses ${skill.name} on")
            }
            is BattleAction.UseItem -> {
                val target = combatant(action.targetKey)?.takeIf { it.key != Combatant.ENEMY_KEY } ?: return false
                if (!useItem(action.item)) return false
                when (action.item.kind) {
                    ItemKind.HEAL_HP -> {
                        if (!target.alive) return refund(action.item)
                        heal(target, action.item.power.toInt(), "${actor.name} uses ${action.item.label}")
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

    private fun strike(a: Combatant, d: Combatant, power: Int, kind: SkillKind, element: Element, verb: String) {
        val atk = if (kind == SkillKind.PHYSICAL) a.attack else a.magic
        val def = if (kind == SkillKind.PHYSICAL) d.defense else (d.defense + d.magic) / 2
        val elementMult = element.multiplierAgainst(d.element)
        val sameElement = if (element != Element.NEUTRAL && element == a.element) 1.2 else 1.0
        val crit = rng.nextDouble() < 0.06
        val raw = power * atk.toDouble() / (atk + def) *
            (1 + a.level * 0.08) *
            elementMult * sameElement *
            (0.85 + rng.nextDouble() * 0.15) *
            (if (crit) 1.5 else 1.0) *
            (if (d.defending) 0.5 else 1.0)
        val dmg = max(1, raw.roundToInt())
        d.hp = max(0, d.hp - dmg)
        val notes = buildList {
            if (crit) add("Critical!")
            if (elementMult > 1.0) add("Super effective!")
            if (elementMult < 1.0) add("Not very effective...")
        }.joinToString(" ")
        log += "${a.name} $verb ${d.name} for $dmg damage. $notes".trim()
        if (!d.alive) log += "${d.name} falls!"
    }

    private fun enemyTurn() {
        val targets = allies.filter { it.alive }
        if (targets.isEmpty()) return
        val target = if (rng.nextDouble() < 0.35) targets.minBy { it.hpFraction } else targets.random(rng)
        val special = enemy.special
        if (special != null && enemy.cooldown == 0 && rng.nextDouble() < 0.5) {
            enemy.cooldown = Combatant.SPECIAL_COOLDOWN
            strike(enemy, target, special.power, special.kind, special.element, "uses ${special.name} on")
        } else {
            strike(enemy, target, 20, SkillKind.PHYSICAL, Element.NEUTRAL, "attacks")
        }
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
            if (isBoss) {
                drops[Item.MASTER_ORB] = 1
                drops[Item.HI_POTION] = 2
            } else if (rng.nextDouble() < 0.25) {
                drops[listOf(Item.POTION, Item.TAMING_ORB, Item.ETHER).random(rng)] = 1
            }
        }
        val captured = if (outcome == BattleOutcome.CAPTURED) enemyMonster.copy(uid = newId(rng), xp = 0) else null
        return BattleResult(
            outcome, encounter, enemyMonster, heroXp, gold, monsterXp, itemsUsed.toMap(), drops, captured, shrineId,
        )
    }
}
