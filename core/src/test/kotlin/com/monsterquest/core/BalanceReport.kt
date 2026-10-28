package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test

/**
 * Balance check: every fully evolved monster against every other, one on one
 * at the same level, with random wild movesets and skills. Writes win rates
 * by species, skill and move. Run with BALANCE_REPORT=<file>.
 */
class BalanceReport {
    /** One duel; true if [a] wins. Both sides pick moves like the wild AI. */
    private fun duel(a: Monster, b: Monster, seed: Int): Boolean? {
        val rng = Random(seed)
        val battle = Battle(Hero(level = 1), listOf(a), b, EncounterType.THUG, emptyMap(), true, rng = rng, fullTeam = true)
        // The hero steps back: monster against monster.
        var guard = 0
        while (battle.outcome == BattleOutcome.ONGOING && guard++ < 400) {
            val c = battle.current ?: break
            if (c.isHero) {
                if (battle.allies.any { it.key == a.uid }) battle.act(BattleAction.Defend)
                else battle.act(BattleAction.Swap(a.uid, Combatant.HERO_KEY))
                continue
            }
            val foe = battle.enemy
            val usable = c.usableMoves
            val move = if (rng.nextDouble() < 0.7) usable.maxBy { it.expectedPower * it.type.multiplierAgainst(foe.types) + if (it.isDamaging) 0.0 else 25.0 }
            else usable.random(rng)
            battle.act(BattleAction.UseMove(move.id))
        }
        return when (battle.outcome) {
            BattleOutcome.WON -> true
            BattleOutcome.LOST -> false
            else -> null
        }
    }

    @Test fun report() {
        val out = System.getenv("BALANCE_REPORT") ?: return
        val level = (System.getenv("BALANCE_LEVEL") ?: "50").toInt()
        val finals = Bestiary.all.filter { it.evolvesTo == null }
        val wins = mutableMapOf<String, Int>().withDefault { 0 }
        val games = mutableMapOf<String, Int>().withDefault { 0 }
        val traitW = mutableMapOf<Trait, Int>().withDefault { 0 }
        val traitG = mutableMapOf<Trait, Int>().withDefault { 0 }
        val moveW = mutableMapOf<String, Int>().withDefault { 0 }
        val moveG = mutableMapOf<String, Int>().withDefault { 0 }
        var seed = 0
        for (x in finals) for (y in finals) {
            if (x == y) continue
            repeat(4) {
                seed++
                val r = Random(seed * 7919L)
                val a = Monster.create(x, level, r).withWildMoves(r)
                val b = Monster.create(y, level, r).withWildMoves(r)
                val won = duel(a, b, seed) ?: return@repeat
                for ((m, w) in listOf(a to won, b to !won)) {
                    games[m.speciesId] = games.getValue(m.speciesId) + 1
                    if (w) wins[m.speciesId] = wins.getValue(m.speciesId) + 1
                    val t = m.trait?.first
                    if (t != null) {
                        traitG[t] = traitG.getValue(t) + 1
                        if (w) traitW[t] = traitW.getValue(t) + 1
                    }
                    m.activeMoves.forEach { mv ->
                        moveG[mv.id] = moveG.getValue(mv.id) + 1
                        if (w) moveW[mv.id] = moveW.getValue(mv.id) + 1
                    }
                }
            }
        }
        fun pct(w: Int, g: Int) = if (g == 0) 0.0 else 100.0 * w / g
        val sb = StringBuilder("Level $level, ${finals.size} final forms, ${seed} pairings x4\n\n== Species (win %) ==\n")
        finals.sortedByDescending { pct(wins.getValue(it.id), games.getValue(it.id)) }.forEach {
            sb.append("%5.1f  %-20s %-16s %s  total %d\n".format(pct(wins.getValue(it.id), games.getValue(it.id)), it.name, it.typeLabel, it.rarity, MaxStats.total(it)))
        }
        sb.append("\n== Skills (win %, games) ==\n")
        traitG.keys.sortedByDescending { pct(traitW.getValue(it), traitG.getValue(it)) }.forEach {
            sb.append("%5.1f  %-16s %d\n".format(pct(traitW.getValue(it), traitG.getValue(it)), it.label, traitG.getValue(it)))
        }
        sb.append("\n== Moves (win % when carried, games; min 60) ==\n")
        moveG.filter { it.value >= 60 }.keys.sortedByDescending { pct(moveW.getValue(it), moveG.getValue(it)) }.forEach {
            val mv = Moves.get(it)
            sb.append("%5.1f  %-18s %-8s pow %3d acc %3d  %s  %d\n".format(pct(moveW.getValue(it), moveG.getValue(it)), mv.name, mv.type.label, mv.power, mv.accuracy, mv.role.label, moveG.getValue(it)))
        }
        java.io.File(out).writeText(sb.toString())
    }
}
