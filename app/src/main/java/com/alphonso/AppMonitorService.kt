package com.alphonso

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class AppMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "alphonso_watchdog"
    private val NOTIFICATION_ID = 999

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Show Notification Immediately (Prevents Crash)
        val notification = createNotification()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Start Watchdog Loop
        startWatchdog()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Alphonso Security", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alphonso Active")
            .setContentText("Device Protected")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun startWatchdog() {
        scope.launch {
            while (isActive) {
                try {
                    // Re-Enforce Policies Every 5 Seconds
                    // This "Un-toggles" the switch if user turned it off
                    PolicyManager.enforcePolicies(applicationContext)
                } catch (e: Exception) {
                    Log.e("AlphonsoWatchdog", "Watchdog error", e)
                }
                delay(5000L)
            }
        }
    }

    override fun onDestroy() {
        // If killed, try to restart
        val broadcastIntent = Intent(this, BootReceiver::class.java)
        sendBroadcast(broadcastIntent)
        super.onDestroy()
    }
}