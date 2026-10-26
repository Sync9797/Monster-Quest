package com.monsterquest.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
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
}
