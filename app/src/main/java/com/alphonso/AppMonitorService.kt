// Updated AppMonitorService.kt
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

class AppMonitorService : Service() {

    private val CHANNEL_ID = "alphonso_watchdog"
    private val NOTIFICATION_ID = 999

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Start Foreground IMMEDIATELY to avoid "ANR" (App Not Responding)
        startForegroundServiceCompat()

        // 2. We do NOT need a loop here.
        // The PolicyManager applied settings on Boot.
        // The Notification keeps this process alive so Accessibility isn't killed.

        return START_STICKY
    }

    private fun startForegroundServiceCompat() {
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Alphonso Security", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alphonso Active")
            .setContentText("Protected by Device Owner")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }
}