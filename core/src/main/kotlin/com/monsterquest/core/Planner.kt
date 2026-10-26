package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * Organizes active quests like a planner: grouped by when they're due, the
 * most important first within each group, with anything due within the hour
 * pulled to the top.
 */
object Planner {
    const val SOON_MS = 60 * 60_000L

    enum class Group(val label: String) {
        OVERDUE("Overdue"),
        HAPPENING("Happening now"),
        SOON("Due within the hour"),
        TODAY("Later today"),
        TOMORROW("Tomorrow"),
        WEEK("This week"),
        LATER("Later"),
        NO_DATE("No due date"),
    }

    data class Section(val group: Group, val tasks: List<QuestTask>)

    /** Due (or starting) within the next hour. */
    fun isDueSoon(t: QuestTask, nowMs: Long): Boolean {
        val due = t.dueAt ?: return false
        return !t.completed && due in nowMs..(nowMs + SOON_MS)
    }

    /** Past due. A calendar event only counts once it has ended. */
    fun isOverdue(t: QuestTask, nowMs: Long): Boolean {
        if (t.completed) return false
        val end = if (t.source == TaskSource.CALENDAR) t.endMs ?: t.startMs else t.dueAt
        return end != null && end < nowMs
    }

    fun groupOf(t: QuestTask, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Group {
        val due = t.dueAt ?: return Group.NO_DATE
        if (isOverdue(t, nowMs)) return Group.OVERDUE
        if (t.source == TaskSource.CALENDAR && due <= nowMs) return Group.HAPPENING
        if (due <= nowMs + SOON_MS) return Group.SOON
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when {
            day == today -> Group.TODAY
            day == today.plusDays(1) -> Group.TOMORROW
            day.isBefore(today.plusDays(7)) -> Group.WEEK
            else -> Group.LATER
        }
    }

    /** Highest priority first, then soonest due, then by name. */
    private val order = compareByDescending<QuestTask> { it.priority.ordinal }
        .thenBy { it.dueAt ?: Long.MAX_VALUE }
        .thenBy { it.title.lowercase() }

    fun sections(tasks: List<QuestTask>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Section> =
        tasks.filter { !it.completed }
            .groupBy { groupOf(it, nowMs, zone) }
            .toSortedMap()
            .map { (g, ts) -> Section(g, if (g == Group.OVERDUE || g == Group.SOON || g == Group.HAPPENING) ts.sortedBy { it.dueAt } else ts.sortedWith(order)) }

    /** "in 25 min", "in 3 h", "2 h ago". */
    fun relative(dueMs: Long, nowMs: Long): String {
        val diff = dueMs - nowMs
        val mins = kotlin.math.abs(diff) / 60_000
        val text = when {
            mins < 1 -> "now"
            mins < 60 -> "$mins min"
            mins < 48 * 60 -> "${mins / 60} h" + if (mins % 60 >= 10 && mins < 6 * 60) " ${mins % 60} min" else ""
            else -> "${mins / (24 * 60)} days"
        }
        return when {
            text == "now" -> "now"
            diff >= 0 -> "in $text"
            else -> "$text ago"
        }
    }
}
