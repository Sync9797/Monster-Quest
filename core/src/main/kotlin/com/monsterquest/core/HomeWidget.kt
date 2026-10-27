package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * What the home-screen widget shows: today's reminders, upcoming calendar
 * quests and the to-do list, each kept short and in order.
 */
object HomeWidget {
    /** One line of the widget: a time (if any), what it is, and whether it's done or due soon. */
    data class Line(val time: String?, val title: String, val done: Boolean = false, val urgent: Boolean = false)

    data class Summary(
        val heroLevel: Int,
        val reminders: List<Line>,
        val calendar: List<Line>,
        val todos: List<Line>,
        /** The next thing coming up (for the 2x2 card), or null if nothing is planned. */
        val next: Line? = null,
    ) {
        val isEmpty: Boolean get() = reminders.isEmpty() && calendar.isEmpty() && todos.isEmpty()
    }

    private fun clock(ms: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "%d:%02d %s".format(h12, t.minute, if (t.hour < 12) "AM" else "PM")
    }

    fun summary(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault(), max: Int = 6): Summary {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val endOfTomorrow = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        // Reminders ringing today, by time; ticked ones show as done.
        val reminders = state.reminders.filter { it.ringsOn(today) }.sortedBy { it.hour * 60 + it.minute }.take(max).map { r ->
            Line(r.timeLabel, r.title, done = r.history[today.toEpochDay()] != null, urgent = Reminders.isOpen(r, nowMs, zone))
        }
        val open = state.tasks.filter { !it.completed }
        // Calendar quests from now until the end of tomorrow.
        val calendar = open.filter { it.source == TaskSource.CALENDAR && (it.endMs ?: it.startMs ?: 0) >= nowMs && (it.startMs ?: Long.MAX_VALUE) < endOfTomorrow }
            .sortedBy { it.startMs }
            .take(max)
            .map { t ->
                val start = t.startMs ?: nowMs
                val day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                Line((if (day != today) "Tmrw " else "") + clock(start, zone), t.title, urgent = Planner.isDueSoon(t, nowMs))
            }
        // To-dos: dated ones by due date (overdue first), then tasks without a date by priority.
        val manual = open.filter { it.source == TaskSource.MANUAL }
        val todos = (manual.filter { it.dueMs != null }.sortedBy { it.dueMs } + manual.filter { it.dueMs == null }.sortedByDescending { it.priority.ordinal })
            .take(max)
            .map { t ->
                Line(t.dueMs?.let { Planner.relative(it, nowMs) }, t.title, urgent = Planner.isDueSoon(t, nowMs) || Planner.isOverdue(t, nowMs))
            }
        return Summary(state.hero.level, reminders, calendar, todos, nextUp(state, nowMs, zone))
    }

    /** "3:00 PM" today, "Tomorrow 9:00 AM", or "Oct 30" further out. */
    private fun whenLabel(ms: Long, nowMs: Long, zone: ZoneId): String {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return when {
            d == today -> "Today ${clock(ms, zone)}"
            d == today.plusDays(1) -> "Tomorrow ${clock(ms, zone)}"
            else -> "${d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${d.dayOfMonth}"
        }
    }

    /**
     * The next thing to do: overdue to-dos first, then whatever is soonest of
     * today's open reminders, calendar quests and dated to-dos; otherwise the
     * top task without a date.
     */
    fun nextUp(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Line? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        data class Cand(val at: Long, val line: Line)
        val open = state.tasks.filter { !it.completed }
        val cands = buildList {
            state.reminders.filter { it.ringsOn(today) && it.history[today.toEpochDay()] == null }.forEach { r ->
                val at = Reminders.ringAt(r, today, zone)
                add(Cand(at, Line(if (at <= nowMs) "Now" else whenLabel(at, nowMs, zone), r.title, urgent = at <= nowMs)))
            }
            open.filter { it.source == TaskSource.CALENDAR && (it.endMs ?: it.startMs ?: 0) >= nowMs }.forEach { t ->
                val at = t.startMs ?: return@forEach
                add(Cand(at, Line(if (at <= nowMs) "Happening now" else whenLabel(at, nowMs, zone), t.title, urgent = Planner.isDueSoon(t, nowMs))))
            }
            open.filter { it.source == TaskSource.MANUAL && it.dueMs != null }.forEach { t ->
                val at = t.dueMs!!
                add(Cand(at, Line(if (at < nowMs) "Overdue" else "Due ${whenLabel(at, nowMs, zone)}", t.title, urgent = at < nowMs || Planner.isDueSoon(t, nowMs))))
            }
        }
        cands.minByOrNull { it.at }?.let { return it.line }
        return open.filter { it.source == TaskSource.MANUAL }.maxByOrNull { it.priority.ordinal }?.let { Line("Task", it.title) }
    }
}
