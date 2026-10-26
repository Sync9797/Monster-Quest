package com.monsterquest.app.focus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.monsterquest.app.MainActivity
import com.monsterquest.app.R
import java.text.DateFormat
import java.util.Date

/**
 * Runs during a phone-free session. The game keeps the screen on with the
 * timer showing, so locking or unlocking the phone doesn't matter:
 * - Leaving Monster Quest for another app (and not coming back within
 *   [LEAVE_GRACE_MS]) fails the session.
 * - Phone calls (ringing, regular or app calls) never count as leaving: the
 *   grace period starts again once the call ends.
 * - Reaching the end time succeeds.
 * The outcome goes to [FocusStore]; the game applies rewards or penalties when opened.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var sessionId: String? = null
    private var endMs = 0L

    /** A call was going on during this time away; after it ends the player gets a fresh grace period. */
    private var callSeen = false

    private val leaveCheck = Runnable { checkLeft() }

    private fun checkLeft() {
        when {
            inCall() -> {
                callSeen = true
                handler.postDelayed(leaveCheck, CALL_POLL_MS)
            }
            callSeen -> {
                callSeen = false
                handler.postDelayed(leaveCheck, LEAVE_GRACE_MS)
            }
            else -> finish(FocusStore.Outcome.FAILED, "You left Monster Quest", "The phone-free session failed. Open Monster Quest to see the damage.")
        }
    }

    /** Ringing, on a phone call, or on a call in another app (WhatsApp, Teams, ...). No permission needed. */
    private fun inCall(): Boolean {
        val mode = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode
        val callModes = buildSet {
            add(AudioManager.MODE_RINGTONE)
            add(AudioManager.MODE_IN_CALL)
            add(AudioManager.MODE_IN_COMMUNICATION)
            if (Build.VERSION.SDK_INT >= 30) add(AudioManager.MODE_CALL_SCREENING)
            if (Build.VERSION.SDK_INT >= 33) {
                add(AudioManager.MODE_CALL_REDIRECT)
                add(AudioManager.MODE_COMMUNICATION_REDIRECT)
            }
        }
        return mode in callModes
    }

    private val endCheck = Runnable {
        finish(FocusStore.Outcome.SUCCESS, "Phone-free session complete!", "Open Monster Quest to claim your rewards.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LEFT -> {
                if (sessionId != null) {
                    handler.removeCallbacks(leaveCheck)
                    callSeen = false
                    handler.postDelayed(leaveCheck, LEAVE_GRACE_MS)
                }
                return START_NOT_STICKY
            }
            ACTION_RETURNED -> {
                handler.removeCallbacks(leaveCheck)
                callSeen = false
                return START_NOT_STICKY
            }
        }
        val id = intent?.getStringExtra(EXTRA_ID)
        val start = intent?.getLongExtra(EXTRA_START, 0L) ?: 0L
        val end = intent?.getLongExtra(EXTRA_END, 0L) ?: 0L
        if (id == null || end <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        sessionId = id
        endMs = end
        createChannels(this)

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ONGOING_ID, ongoingNotification(end), type)

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(endCheck, (end - System.currentTimeMillis()).coerceAtLeast(0))
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
        super.onDestroy()
    }

    private fun ongoingNotification(end: Long) =
        NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Phone-free session")
            .setContentText("Stay in Monster Quest until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(end))}.")
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
        private const val ACTION_LEFT = "com.monsterquest.app.focus.LEFT"
        private const val ACTION_RETURNED = "com.monsterquest.app.focus.RETURNED"

        /** Time allowed outside the game (an accidental tap on Home) before the session fails. */
        const val LEAVE_GRACE_MS = 15_000L

        /** How often to check whether a call has ended. */
        private const val CALL_POLL_MS = 5_000L

        /** The player switched to another app (not just turned the screen off). */
        fun appLeft(context: Context) = send(context, ACTION_LEFT)

        fun appReturned(context: Context) = send(context, ACTION_RETURNED)

        private fun send(context: Context, action: String) {
            // Only reaches a running session; plain startService is allowed while the service is in the foreground.
            runCatching { context.startService(Intent(context, FocusService::class.java).setAction(action)) }
        }

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
