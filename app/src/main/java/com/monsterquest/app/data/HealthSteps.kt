package com.monsterquest.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads today's step total from Health Connect, Android's shared health store.
 * Samsung Health, Google Fit, Fitbit and others write their steps there.
 */
object HealthSteps {
    const val PROVIDER = "com.google.android.apps.healthdata"
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"

    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    enum class Status { READY, NEEDS_PERMISSION, NEEDS_INSTALL, UNAVAILABLE }

    private fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

    suspend fun status(context: Context): Status = when (sdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> {
            val granted = runCatching {
                HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
            }.getOrDefault(emptySet())
            if (granted.containsAll(permissions)) Status.READY else Status.NEEDS_PERMISSION
        }
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_INSTALL
        else -> Status.UNAVAILABLE
    }

    /** Opens the Play Store to install or update Health Connect. */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", "com.monsterquest.app")
    }

    /**
     * Steps walked today, or null if Health Connect can't be read.
     * [samsungOnly] counts only steps recorded by Samsung Health.
     */
    suspend fun stepsToday(context: Context, samsungOnly: Boolean): Long? = runCatching {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val client = HealthConnectClient.getOrCreate(context)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                dataOriginFilter = if (samsungOnly) setOf(DataOrigin(SAMSUNG_HEALTH)) else emptySet(),
            ),
        )
        result[StepsRecord.COUNT_TOTAL] ?: 0L
    }.getOrNull()

    /**
     * Steps in each hour (0..23) of [day], straight from Health Connect, so
     * the chart also shows hours from before the game was opened. Null if
     * Health Connect can't be read.
     */
    suspend fun stepsByHour(context: Context, day: LocalDate, samsungOnly: Boolean): List<Int>? = runCatching {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
        if (!end.isAfter(start)) return List(24) { 0 }
        val client = HealthConnectClient.getOrCreate(context)
        // The raw records, so each one's steps can be spread over the time it covers
        // (some apps write long records that start at midnight).
        val records = mutableListOf<StepsRecord>()
        var page: String? = null
        do {
            val res = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = if (samsungOnly) setOf(DataOrigin(SAMSUNG_HEALTH)) else emptySet(),
                    pageToken = page,
                ),
            )
            records += res.records
            page = res.pageToken
        } while (page != null && records.size < 5_000)
        // Several apps may record the same walk: use the one with the most steps, then
        // scale it to Health Connect's de-duplicated total for the day.
        val main = records.groupBy { it.metadata.dataOrigin.packageName }.maxByOrNull { (_, rs) -> rs.sumOf { it.count } }?.value.orEmpty()
        val spans = main.map { com.monsterquest.core.Health.StepSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.count) }
        val hours = com.monsterquest.core.Health.spreadByHour(spans, day, zone) ?: return null
        val total = stepsToday(context, samsungOnly) ?: hours.sum().toLong()
        val sum = hours.sum()
        if (sum <= 0 || total <= 0) hours else hours.map { Math.round(it.toDouble() * total / sum).toInt() }
    }.getOrNull()

    // ---- sleep -------------------------------------------------------------------

    /** Asked for separately, so step syncing keeps working whether or not sleep is allowed. */
    val sleepPermissions = setOf(HealthPermission.getReadPermission(SleepSessionRecord::class))

    /** Whether the game may read sleep; null when Health Connect isn't available at all. */
    suspend fun sleepAllowed(context: Context): Boolean? = runCatching {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(sleepPermissions)
    }.getOrNull()

    /** Sleep sessions that ended in the last [days] days, with their stages, or null if they can't be read. */
    suspend fun recentSleep(context: Context, days: Long = 7): List<com.monsterquest.core.SleepNight>? = runCatching {
        if (sdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val end = Instant.now()
        val records = HealthConnectClient.getOrCreate(context).readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(end.minus(Duration.ofDays(days)), end)),
        ).records
        records.map { r ->
            fun mins(vararg types: Int) = r.stages.filter { it.stage in types }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
            com.monsterquest.core.SleepNight(
                startMs = r.startTime.toEpochMilli(),
                endMs = r.endTime.toEpochMilli(),
                deepMin = mins(SleepSessionRecord.STAGE_TYPE_DEEP),
                lightMin = mins(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING),
                remMin = mins(SleepSessionRecord.STAGE_TYPE_REM),
                awakeMin = mins(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
            )
        }
    }.getOrNull()
}
