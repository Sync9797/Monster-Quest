package com.monsterquest.app.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.monsterquest.app.MainActivity
import com.monsterquest.app.R
import com.monsterquest.core.GameEngine
import java.text.DateFormat
import java.util.Date

/**
 * Runs during a phone-free session and watches for the phone being unlocked.
 * - Unlocking before the session ends fails it.
 * - Not locking the phone within the grace period fails it.
 * - Reaching the end time succeeds.
 * The outcome goes to [FocusStore]; the game applies rewards or penalties when opened.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var sessionId: String? = null
    private var endMs = 0L
    private var screenWentOff = false
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> screenWentOff = true
                Intent.ACTION_USER_PRESENT -> {
                    if (System.currentTimeMillis() < endMs) {
                        finish(FocusStore.Outcome.FAILED, "📱 Phone unlocked early", "The session failed. Open Monster Quest to see the damage.")
                    } else {
                        finish(FocusStore.Outcome.SUCCESS, "📵 Phone-free session complete!", "Open Monster Quest to claim your rewards.")
                    }
                }
            }
        }
    }

    private val graceCheck = Runnable {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!screenWentOff && pm.isInteractive) {
            finish(FocusStore.Outcome.FAILED, "📱 You didn't put your phone down", "Lock your phone right after starting a session.")
        }
    }

    private val endCheck = Runnable {
        finish(FocusStore.Outcome.SUCCESS, "📵 Phone-free session complete!", "Open Monster Quest to claim your rewards.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        val start = intent?.getLongExtra(EXTRA_START, 0L) ?: 0L
        val end = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
        if (id == null || end <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        sessionId = id
        endMs = end
        screenWentOff = false
        createChannels(this)

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ONGOING_ID, ongoingNotification(end), type)

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        handler.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()
        handler.postDelayed(graceCheck, (start + GameEngine.FOCUS_GRACE_MS - now).coerceAtLeast(0))
        handler.postDelayed(endCheck, (end - now).coerceAtLeast(0))
        return START_NOT_STICKY
    }

    private fun finish(outcome: FocusStore.Outcome, title: String, text: String) {
        val id = sessionId ?: return
        sessionId = null
        FocusStore.record(this, id, outcome)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            RESULT_ID,
            NotificationCompat.Builder(this, RESULT_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openAppIntent(this))
                .setAutoCancel(true)
                .build(),
        )
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun ongoingNotification(end: Long) =
        NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("📵 Phone-free session")
            .setContentText("Keep your phone locked until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(end))}.")
            .setOngoing(true)
            .setContentIntent(openAppIntent(this))
            .build()

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"
        private const val ONGOING_CHANNEL = "focus_ongoing"
        private const val RESULT_CHANNEL = "focus_result"
        private const val ONGOING_ID = 41
        private const val RESULT_ID = 42

        fun start(context: Context, id: String, startMs: Long, endMs: Long) {
            val intent = Intent(context, FocusService::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_START, startMs)
                .putExtra(EXTRA_END, endMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
        }

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun createChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(ONGOING_CHANNEL, "Phone-free session", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, "Phone-free results", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
