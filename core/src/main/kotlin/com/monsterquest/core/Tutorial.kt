package com.monsterquest.core

/** One of the first steps: a hint, how to tell it's done, and a small reward. */
data class TutorialStep(
    val id: String,
    val title: String,
    val hint: String,
    val gold: Int,
    val items: Map<Item, Int> = emptyMap(),
    val done: (GameState) -> Boolean,
)

/**
 * First steps for a new player: a short checklist on Home that teaches the
 * loop (quests, the map, battles, taming, phone-free time), ticking itself off
 * as they play, each with a small reward and a bonus for finishing it.
 */
object Tutorial {
    val steps: List<TutorialStep> = listOf(
        TutorialStep("add_quest", "Add a quest", "Tap + Task on Home and add something you need to do today.", 50) { it.tasks.isNotEmpty() || it.stats.tasksCompleted > 0 },
        TutorialStep("finish_quest", "Complete a quest", "Do it in real life, then tick it off: XP, gold and a hunt token.", 100) { it.stats.tasksCompleted > 0 },
        TutorialStep("visit_shrine", "Visit a shrine", "Open the Map and walk to a blue crystal shrine (within 60 m) to collect items.", 100, mapOf(Item.POTION to 2)) { it.stats.shrinesVisited > 0 },
        TutorialStep("win_battle", "Win a battle", "Walk up to a monster on the Map (or spend a hunt token on the Hero tab) and fight it.", 100) { it.stats.battlesWon > 0 },
        TutorialStep("tame", "Tame a monster", "Get a wild monster below half HP, then Tame: pick a crystal and trace the glyph.", 150, mapOf(Item.TAMING_ORB to 3)) { it.stats.monstersTamed > 0 },
        TutorialStep("phone_free", "Go phone-free", "Start a phone-free session (15 minutes is fine) and put the phone down.", 150) { it.stats.focusWins > 0 },
    )

    /** Finishing every step. */
    const val BONUS_GOLD = 300
    val BONUS_ITEMS = mapOf(Item.TREAT to 1, Item.GREAT_ORB to 3)

    /** The checklist shows until every step is claimed (or it's dismissed). */
    fun active(state: GameState): Boolean = state.starterChosen && !state.tutorialDismissed && steps.any { it.id !in state.tutorialClaimed }

    /** A brand-new player: no quests or battles yet. The welcome shows once. */
    fun showIntro(state: GameState): Boolean = state.starterChosen && !state.tutorialIntroSeen &&
        state.stats.tasksCompleted == 0 && state.stats.battlesWon == 0 && state.tasks.isEmpty()

    fun claim(state: GameState, id: String): Update {
        val step = steps.find { it.id == id } ?: return Update(state, "")
        if (id in state.tutorialClaimed || !step.done(state)) return Update(state, "")
        var s = state.copy(gold = state.gold + step.gold, tutorialClaimed = state.tutorialClaimed + id)
        if (step.items.isNotEmpty()) s = GameEngine.addItems(s, step.items).first
        var text = "First steps: ${step.title}! +${step.gold} gold" + step.items.entries.joinToString("") { ", ${it.key.label} x${it.value}" }
        if (steps.all { it.id in s.tutorialClaimed }) {
            s = GameEngine.addItems(s.copy(gold = s.gold + BONUS_GOLD), BONUS_ITEMS).first
            text += ". All done: +$BONUS_GOLD gold and " + BONUS_ITEMS.entries.joinToString { "${it.key.label} x${it.value}" } + ". You're ready, adventurer!"
        }
        return Update(s, "$text.")
    }
}
