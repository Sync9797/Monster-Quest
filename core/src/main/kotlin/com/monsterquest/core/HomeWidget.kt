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
        return Summary(state.hero.level, reminders, calendar, todos)
    }
}
