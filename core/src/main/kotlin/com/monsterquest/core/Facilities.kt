package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * Real-world services on the map: pharmacies heal your team for free once a
 * day, gyms train your party for a torch, and hospitals wake every fainted
 * monster once a day. You have to walk there.
 */
object Facilities {
    const val RANGE_M = 60.0
    /** A training session at a gym: one torch for a share of a level ([XpRules.TRAINING_SHARES]) for each party monster. */
    const val TRAINING_TORCHES = 1

    /** Gym sessions already done today. */
    fun trainingsToday(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        if (state.trainingDay == day(nowMs, zone)) state.trainingCount else 0

    /** The share of a level the next session gives today, or null when they're used up. */
    fun nextTrainingShare(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Double? =
        XpRules.TRAINING_SHARES.getOrNull(trainingsToday(state, nowMs, zone))

    private fun day(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    fun label(role: VenueRole): String = when (role) {
        VenueRole.PHARMACY -> "Infirmary"
        VenueRole.GYM -> "Training ground"
        VenueRole.HOSPITAL -> "Healing hall"
        else -> ""
    }

    fun usedToday(state: GameState, role: VenueRole, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean = when (role) {
        VenueRole.PHARMACY -> state.infirmaryDay == day(nowMs, zone)
        VenueRole.HOSPITAL -> state.hospitalDay == day(nowMs, zone)
        VenueRole.GYM -> nextTrainingShare(state, nowMs, zone) == null
        else -> false
    }

    /** A pharmacy: your hero and every monster back to full health (not fainted ones), once a day. */
    fun infirmary(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        if (usedToday(state, VenueRole.PHARMACY, nowMs, zone)) return Update(state, "The infirmary has already patched you up today. Come back tomorrow, or walk to heal.")
        val s = state.copy(
            heroDamage = 0,
            monsters = state.monsters.map { if (it.fainted) it else it.copy(damage = 0) },
            infirmaryDay = day(nowMs, zone),
        )
        return Update(s, "The infirmary patches everyone up: your hero and monsters are at full health.")
    }

    /** A hospital: every fainted monster wakes at full health, once a day. */
    fun hospital(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        if (usedToday(state, VenueRole.HOSPITAL, nowMs, zone)) return Update(state, "The healing hall has already helped you today.")
        val down = state.monsters.count { it.fainted }
        if (down == 0) return Update(state, "None of your monsters need waking.")
        val s = state.copy(
            monsters = state.monsters.map { if (it.fainted) it.copy(fainted = false, damage = 0) else it },
            hospitalDay = day(nowMs, zone),
        )
        return Update(s, "The healing hall wakes $down fainted monster${if (down == 1) "" else "s"}, fully healed.")
    }

    /** A gym: spend a torch and every party monster gains part of a level. */
    fun train(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        val share = nextTrainingShare(state, nowMs, zone)
            ?: return Update(state, "Your team has trained ${XpRules.TRAINING_SHARES.size} times today. The gym opens again tomorrow.")
        if (state.torches < TRAINING_TORCHES) return Update(state, "Training costs $TRAINING_TORCHES torch. Walk to light more.")
        val notes = mutableListOf<String>()
        val cap = XpRules.monsterCap(state)
        val s = state.copy(
            torches = state.torches - TRAINING_TORCHES,
            trainingDay = day(nowMs, zone),
            trainingCount = trainingsToday(state, nowMs, zone) + 1,
            monsters = state.monsters.map { m ->
                if (m.uid !in state.party || m.fainted) return@map m
                val xp = (monsterXpForNext(m.level) * share).toInt()
                val (after, n) = GameEngine.grow(m, xp, cap)
                notes += n
                if (after.level > m.level) notes += "${after.displayName} reached Lv ${after.level}!"
                after
            },
        )
        return Update(s, "A hard workout! Your party gains ${(share * 100).toInt()}% of a level each. ${notes.joinToString(" ")}".trim())
    }
}
