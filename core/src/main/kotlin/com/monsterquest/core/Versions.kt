package com.monsterquest.core

/** Version numbers like "3.15.1" (a leading "v" is fine), for the update check. */
object Versions {
    /** The numbers in a version, or null if it isn't one ("latest", "3.15.1-beta" counts as 3.15.1). */
    fun parse(v: String): List<Int>? {
        val core = v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore(' ')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    /** Whether [a] is a later version than [b]. */
    fun newer(a: String, b: String): Boolean {
        val x = parse(a) ?: return false
        val y = parse(b) ?: return true
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** The newest of [tags] that's later than [current], or null if you're up to date. */
    fun newest(tags: List<String>, current: String): String? =
        tags.filter { parse(it) != null && newer(it, current) }.maxWithOrNull { a, b -> if (newer(a, b)) 1 else if (newer(b, a)) -1 else 0 }
}
