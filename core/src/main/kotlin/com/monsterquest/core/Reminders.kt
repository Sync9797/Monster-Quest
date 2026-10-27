package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a reminder went on one day. */
@Serializable
enum class ReminderMark { DONE, SKIPPED, EARLY }

/**
 * A daily reminder: a time of day on chosen weekdays, like a gentle alarm
 * ("6:00, make the bed, every day but Saturday").
 */
@Serializable
data class Reminder(
    val id: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    /** ISO days of the week it rings on: 1 = Monday ... 7 = Sunday. */
    val days: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = true,
    /** Epoch day -> what happened that day (last ~60 days). */
    val history: Map<Long, ReminderMark> = emptyMap(),
    val bestStreak: Int = 0,
) {
    fun ringsOn(day: LocalDate): Boolean = enabled && day.dayOfWeek.value in days

    val timeLabel: String
        get() {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            return "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
        }

    /** e.g. "Every day", "Weekdays", "Every day but Sat", "Mon, Wed, Fri". */
    val daysLabel: String
        get() {
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            return when {
                days.size == 7 -> "Every day"
                days == (1..5).toSet() -> "Weekdays"
                days == setOf(6, 7) -> "Weekends"
                days.size == 6 -> "Every day but ${names[(1..7).first { it !in days } - 1]}"
                else -> days.sorted().joinToString { names[it - 1] }
            }
        }
}

/**
 * Reminders are habits, rewarded gently:
 * 1. Marking one done pays a little (like an Easy quest), but only on time:
 *    from its time until [ON_TIME_MS] after. Early ticks pay nothing.
 *    Paid reminders share the daily quest allowance ([QuestRewards]).
 * 2. Each reminder keeps a streak over the days it rings; a 7-day streak
 *    gives a Monster Treat and every 30 days something better.
 *    Skipped days don't break it (or add to it).
 * 3. Missing one costs nothing; the streak just starts again.
 */
object Reminders {
    const val ON_TIME_MS = 3 * 3_600_000L
    const val XP = 15
    const val GOLD = 8
    const val WEEK_STREAK = 7
    const val MONTH_STREAK = 30
    const val MONTH_GOLD = 150
    private const val HISTORY_DAYS = 60

    private fun date(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** When it rings on [day], in epoch ms. */
    fun ringAt(r: Reminder, day: LocalDate, zone: ZoneId): Long =
        day.atTime(r.hour, r.minute).atZone(zone).toInstant().toEpochMilli()

    /** The next time it rings after [nowMs], or null if it never does. */
    fun nextRing(r: Reminder, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!r.enabled || r.days.isEmpty()) return null
        val today = date(nowMs, zone)
        for (i in 0..7) {
            val d = today.plusDays(i.toLong())
            if (r.ringsOn(d)) ringAt(r, d, zone).takeIf { it > nowMs }?.let { return it }
        }
        return null
    }

    /** Days in a row it was done (skips don't count against it), up to today. */
    fun streak(r: Reminder, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        var day = date(nowMs, zone)
        var n = 0
        // Today only counts once it's done: an open reminder doesn't break the streak yet.
        if (r.history[day.toEpochDay()] == null) day = day.minusDays(1)
        repeat(HISTORY_DAYS) {
            if (r.ringsOn(day)) {
                when (r.history[day.toEpochDay()]) {
                    ReminderMark.DONE -> n++
                    ReminderMark.SKIPPED -> {}
                    else -> return n
                }
            }
            day = day.minusDays(1)
        }
        return n
    }

    /** Whether it's waiting to be ticked off right now (rang today, not handled yet). */
    fun isOpen(r: Reminder, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = date(nowMs, zone)
        return r.ringsOn(today) && nowMs >= ringAt(r, today, zone) && r.history[today.toEpochDay()] == null
    }

    fun add(state: GameState, title: String, hour: Int, minute: Int, days: Set<Int>, rng: kotlin.random.Random = kotlin.random.Random.Default): Update {
        val t = title.trim()
        if (t.isEmpty()) return Update(state, "A reminder needs a name.")
        if (days.isEmpty()) return Update(state, "Pick at least one day.")
        val r = Reminder(newId(rng), t, hour.coerceIn(0, 23), minute.coerceIn(0, 59), days.filter { it in 1..7 }.toSet())
        return Update(state.copy(reminders = state.reminders + r), "Reminder set: ${r.title} at ${r.timeLabel}, ${r.daysLabel.lowercase()}.")
    }

    fun edit(state: GameState, id: String, title: String, hour: Int, minute: Int, days: Set<Int>): Update {
        val t = title.trim()
        if (t.isEmpty() || days.isEmpty()) return Update(state, "A reminder needs a name and at least one day.")
        return Update(
            state.copy(reminders = state.reminders.map { if (it.id == id) it.copy(title = t, hour = hour, minute = minute, days = days) else it }),
            "Reminder updated.",
        )
    }

    fun setEnabled(state: GameState, id: String, on: Boolean): Update = Update(
        state.copy(reminders = state.reminders.map { if (it.id == id) it.copy(enabled = on) else it }),
        if (on) "Reminder on." else "Reminder paused.",
    )

    fun delete(state: GameState, id: String): Update =
        Update(state.copy(reminders = state.reminders.filterNot { it.id == id }), "Reminder deleted.")

    private fun mark(r: Reminder, day: Long, m: ReminderMark) =
        r.copy(history = r.history.filterKeys { it > day - HISTORY_DAYS } + (day to m))

    /** Busy today: let it go without breaking the streak. */
    fun skip(state: GameState, id: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        val r = state.reminders.find { it.id == id } ?: return Update(state, "Reminder not found.")
        val day = date(nowMs, zone).toEpochDay()
        if (r.history[day] != null) return Update(state, "Already handled today.")
        return Update(
            state.copy(reminders = state.reminders.map { if (it.id == id) mark(it, day, ReminderMark.SKIPPED) else it }),
            "Skipped ${r.title} today. Your streak is safe.",
        )
    }

    /** Done: a small reward if it's on time, and the streak grows (with bonuses at 7 and 30 days). */
    fun complete(state: GameState, id: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
        val r = state.reminders.find { it.id == id } ?: return Update(state, "Reminder not found.")
        val today = date(nowMs, zone)
        val day = today.toEpochDay()
        if (r.history[day] != null) return Update(state, "Already handled today.")
        val ring = ringAt(r, today, zone)
        if (!r.ringsOn(today) || nowMs < ring) {
            // Early (or not a reminder day): done, but no reward and no streak.
            val s = state.copy(reminders = state.reminders.map { if (it.id == id) mark(it, day, ReminderMark.EARLY) else it })
            return Update(s, "${r.title}: done! Reminders pay out from their time (${r.timeLabel}), so no reward this time.")
        }
        val onTime = nowMs <= ring + ON_TIME_MS
        var marked = mark(r, day, if (onTime) ReminderMark.DONE else ReminderMark.EARLY)
        val streak = if (onTime) streak(marked, nowMs, zone) else 0
        marked = marked.copy(bestStreak = maxOf(r.bestStreak, streak))
        var s = state.copy(reminders = state.reminders.map { if (it.id == id) marked else it })
        if (!onTime) return Update(s, "${r.title}: done! It's more than 3 hours late, so no reward or streak this time.")

        // The reward shares the daily quest allowance.
        val limits = s.questLimits.on(GameEngine.epochDay(nowMs, zone))
        val share = when {
            limits.rewarded < QuestRewards.FULL_PER_DAY -> 1.0
            limits.rewarded < QuestRewards.FULL_PER_DAY + QuestRewards.HALF_PER_DAY -> 0.5
            else -> 0.0
        }
        val xp = (XP * share).toInt()
        val gold = (GOLD * share).toInt()
        s = s.copy(gold = s.gold + gold, questLimits = limits.copy(rewarded = limits.rewarded + if (share > 0) 1 else 0))
        val rewards = mutableListOf<Reward>()
        if (gold > 0) rewards += Reward.Gold(gold)
        val msg = StringBuilder("${r.title}: done on time!")
        if (share > 0) msg.append(" +$xp XP, +$gold gold.") else msg.append(" (Today's rewards are used up.)")
        msg.append(" Streak: $streak day${if (streak == 1) "" else "s"}.")
        // Streak bonuses.
        val bonus = mutableMapOf<Item, Int>()
        if (streak > 0 && streak % WEEK_STREAK == 0) bonus[Item.TREAT] = 1
        if (streak > 0 && streak % MONTH_STREAK == 0) {
            bonus[Item.RESTORE_POTION] = 1
            s = s.copy(gold = s.gold + MONTH_GOLD)
            rewards += Reward.Gold(MONTH_GOLD)
            msg.append(" A $MONTH_STREAK-day streak! +$MONTH_GOLD gold.")
        }
        if (bonus.isNotEmpty()) {
            val (withItems, lost) = GameEngine.addItems(s, bonus)
            s = withItems
            bonus.forEach { (i, n) -> rewards += Reward.Items(i, n) }
            msg.append(" Streak bonus: ${GameEngine.boxText(bonus)}!")
            if (lost > 0) msg.append(" (Bag full: make room to collect streak bonuses.)")
        }
        val gain = GameEngine.addHeroXp(s, xp)
        return Update(gain.state, msg.toString() + GameEngine.levelUpText(gain), rewards)
    }
}
