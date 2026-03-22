package com.signalout.android.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.signalout.android.R
import com.signalout.android.call.CallManager
import com.signalout.android.call.CallState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service for maintaining active calls.
 * Shows ongoing call notification with hang-up action.
 */
class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        private const val NOTIFICATION_ID = 9901
        private const val CHANNEL_ID = "signalout_call_channel"
        const val ACTION_HANGUP = "com.signalout.android.action.HANGUP"
        const val ACTION_ACCEPT = "com.signalout.android.action.ACCEPT_CALL"
        const val ACTION_REJECT = "com.signalout.android.action.REJECT_CALL"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANGUP -> {
                CallManager.getInstance(applicationContext).endCall()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACCEPT -> {
                CallManager.getInstance(applicationContext).acceptCall()
            }
            ACTION_REJECT -> {
                CallManager.getInstance(applicationContext).rejectCall()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildNotification("Call in progress", "SignalOut call")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34+: declare all foreground service types used during calls
            val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: SecurityException) {
                // Fallback: try without camera (voice-only call)
                Log.w(TAG, "Failed with camera type, falling back: ${e.message}")
                try {
                    startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } catch (e2: SecurityException) {
                    Log.w(TAG, "Failed with microphone type, minimal fallback: ${e2.message}")
                    startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Monitor call state and stop service when call ends
        scope.launch {
            CallManager.getInstance(applicationContext).callState.collectLatest { state ->
                when (state) {
                    is CallState.Idle, is CallState.Ended -> {
                        delay(1000)
                        stopSelf()
                    }
                    is CallState.Connected -> {
                        updateNotification("On call with ${state.peerName}", formatDuration(state.startTimeMs))
                    }
                    is CallState.Incoming -> {
                        updateNotification("Incoming call", state.callerName)
                    }
                    is CallState.Outgoing -> {
                        updateNotification("Calling...", state.peerName)
                    }
                    else -> {}
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SignalOut Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active call notifications"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val hangupIntent = Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getService(
            this, 0, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_notification,
                "Hang Up",
                hangupPendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        try {
            val notification = buildNotification(title, content)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }

    private fun formatDuration(startTimeMs: Long): String {
        val secs = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
        return String.format("%02d:%02d", secs / 60, secs % 60)
    }
}
