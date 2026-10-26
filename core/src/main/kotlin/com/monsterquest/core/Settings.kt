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

/** Screen refresh rate the game asks for. */
@Serializable
enum class FrameRate(val label: String, val hz: Float?, val description: String) {
    HIGH("120 fps", 120f, "Smoothest animation on screens that support it (up to 120 Hz)."),
    STANDARD("60 fps", 60f, "Uses less battery."),
    AUTO("Auto", null, "Let the phone decide."),
}

/** Player preferences, saved with the game. */
@Serializable
data class Settings(
    val stepSource: StepSource = StepSource.PHONE,
    val frameRate: FrameRate = FrameRate.HIGH,
    /** Turn off glows, auras, mist and engraving for smoother scrolling on older phones. */
    val reducedEffects: Boolean = false,
    /** Ask for location less often to save battery (the map updates more slowly). */
    val batterySaverGps: Boolean = false,
    /** On the map, turning the phone upside down blacks out the screen to save battery. */
    val flipBatterySaver: Boolean = true,
    /** Aggressive monsters that reach you can start a fight before you're ready. */
    val ambushes: Boolean = true,
    /** Vibrate when a new monster appears nearby. */
    val vibrateOnSpawn: Boolean = true,
    val calendarAutoSync: Boolean = true,
    val calendarDaysAhead: Int = 7,
    val includeAllDayEvents: Boolean = true,
)

val CALENDAR_DAYS_AHEAD_OPTIONS = listOf(1, 3, 7, 14)
