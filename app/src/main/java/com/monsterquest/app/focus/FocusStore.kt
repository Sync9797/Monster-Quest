package com.monsterquest.app.focus

import android.content.Context

/**
 * Tiny shared record between [FocusService] and the game: the service writes
 * the outcome here the moment the phone is unlocked; the game applies it the
 * next time it's opened.
 */
object FocusStore {
    private const val PREFS = "focus"

    enum class Outcome { SUCCESS, FAILED }

    fun record(context: Context, sessionId: String, outcome: Outcome) {
        prefs(context).edit().putString("outcome_$sessionId", outcome.name).apply()
    }

    fun outcome(context: Context, sessionId: String): Outcome? =
        prefs(context).getString("outcome_$sessionId", null)?.let { runCatching { Outcome.valueOf(it) }.getOrNull() }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
