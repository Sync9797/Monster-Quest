package com.monsterquest.core

import kotlinx.serialization.Serializable

/** Where walking is counted from. */
@Serializable
enum class StepSource(val label: String, val description: String) {
    PHONE("Phone step counter", "The phone's built-in step sensor. Counts all day, even when the game is closed."),
    SAMSUNG_HEALTH(
        "Samsung Health",
        "Steps from Samsung Health (including a Galaxy Watch). Samsung Health shares them through Health Connect: " +
            "turn it on in Samsung Health, then Settings, then Health Connect.",
    ),
    HEALTH_CONNECT("Health Connect", "Steps from any app that writes to Health Connect: Google Fit, Fitbit, Garmin, Samsung Health and more."),
    GPS("GPS distance", "Distance walked while the game is open. Use this if nothing else is available."),
    ;

    val usesHealthConnect: Boolean get() = this == SAMSUNG_HEALTH || this == HEALTH_CONNECT
}

/** Player preferences, saved with the game. */
@Serializable
data class Settings(
    val stepSource: StepSource = StepSource.PHONE,
    /** Turn off glows, auras, mist and engraving for smoother scrolling on older phones. */
    val reducedEffects: Boolean = false,
    /** Ask for location less often to save battery (the map updates more slowly). */
    val batterySaverGps: Boolean = false,
    val calendarAutoSync: Boolean = true,
    val calendarDaysAhead: Int = 7,
    val includeAllDayEvents: Boolean = true,
)

val CALENDAR_DAYS_AHEAD_OPTIONS = listOf(1, 3, 7, 14)
