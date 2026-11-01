package com.monsterquest.core

/**
 * Plays [action], or, when everyone out front is down and the fight waits for
 * a replacement, sends in the first one standing on the bench (as a player must).
 */
fun Battle.step(action: BattleAction) {
    val fallen = current
    if (mustSwap && fallen != null) {
        val w = swapsWaiting.first { it.alive }
        act(BattleAction.Swap(w.key, fallen.key))
    } else {
        act(action)
    }
}
