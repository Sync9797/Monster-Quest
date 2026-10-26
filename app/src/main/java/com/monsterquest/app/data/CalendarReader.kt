package com.monsterquest.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import com.monsterquest.core.CalendarEvent
import java.util.Calendar

/** One calendar on the phone (e.g. a Google account's calendar, "Holidays", a shared work calendar). */
data class PhoneCalendar(val id: Long, val name: String, val account: String, val color: Int) {
    /** Holiday and birthday calendars are left out unless chosen explicitly. */
    val isNoise: Boolean get() = CalendarReader.isNoise(name)
}

/**
 * Reads events from every calendar synced to the phone (Google, Samsung,
 * Outlook, ...) using Android's built-in calendar provider.
 */
object CalendarReader {
    private val skipCalendars = listOf("holiday", "birthday", "contacts", "week numbers")

    fun isNoise(name: String) = skipCalendars.any { name.contains(it, ignoreCase = true) }

    /** Every calendar synced to the phone, grouped by account. */
    fun calendars(context: Context): List<PhoneCalendar> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.CALENDAR_COLOR)
        val out = mutableListOf<PhoneCalendar>()
        context.contentResolver.query(Calendars.CONTENT_URI, projection, null, null, "${Calendars.ACCOUNT_NAME} ASC")?.use { c ->
            while (c.moveToNext()) {
                out += PhoneCalendar(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: "", c.getInt(3))
            }
        }
        return out
    }

    /**
     * Events from the chosen calendars ([calendarIds]), or, when [calendarIds]
     * is null, from every calendar except holidays and birthdays.
     */
    fun read(context: Context, daysBack: Int = 1, daysAhead: Int = 7, calendarIds: Set<Long>? = null): List<CalendarEvent> {
        if (calendarIds != null && calendarIds.isEmpty()) return emptyList()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24 * 60 * 60 * 1000L
        val begin = startOfToday - daysBack * day
        val end = startOfToday + (daysAhead + 1) * day

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.CALENDAR_DISPLAY_NAME,
            Instances.CALENDAR_ID,
        )
        val out = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val calName = c.getString(5)
                if (calendarIds == null) {
                    if (calName != null && isNoise(calName)) continue
                } else if (c.getLong(6) !in calendarIds) {
                    continue
                }
                val eventId = c.getLong(0)
                val start = c.getLong(2)
                out += CalendarEvent(
                    key = "cal:$eventId:$start",
                    title = c.getString(1) ?: "",
                    startMs = start,
                    endMs = c.getLong(3),
                    allDay = c.getInt(4) == 1,
                    calendarName = calName,
                )
            }
        }
        return out
    }
}
