package com.monsterquest.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import com.monsterquest.core.CalendarEvent
import java.util.Calendar

/**
 * Reads events from every calendar synced to the phone (Google, Samsung,
 * Outlook, ...) using Android's built-in calendar provider.
 */
object CalendarReader {
    private val skipCalendars = listOf("holiday", "birthday", "contacts", "week numbers")

    fun read(context: Context, daysBack: Int = 1, daysAhead: Int = 7): List<CalendarEvent> {
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
        )
        val out = mutableListOf<CalendarEvent>()
        context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val calName = c.getString(5)
                if (calName != null && skipCalendars.any { calName.contains(it, ignoreCase = true) }) continue
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
