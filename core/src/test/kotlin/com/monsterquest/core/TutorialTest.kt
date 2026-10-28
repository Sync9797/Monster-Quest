package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TutorialTest {
    @Test
    fun firstStepsTickThemselvesOffAndPay() {
        var s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        assertTrue(Tutorial.showIntro(s))
        assertTrue(Tutorial.active(s))
        assertTrue(Tutorial.steps.none { it.done(s) })
        // Can't claim what isn't done.
        assertEquals(s, Tutorial.claim(s, "win_battle").state)
        s = s.copy(stats = s.stats.copy(tasksCompleted = 1, shrinesVisited = 1, battlesWon = 1, monstersTamed = 1, focusWins = 1))
        assertFalse(Tutorial.showIntro(s))
        val gold = s.gold
        Tutorial.steps.forEach { s = Tutorial.claim(s, it.id).state }
        assertEquals(gold + Tutorial.steps.sumOf { it.gold } + Tutorial.BONUS_GOLD, s.gold)
        assertFalse(Tutorial.active(s))
    }
}
