package com.alphonso

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class CloudflareDnsService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Cloudflare DNS IPs
    private val DNS_1 = "1.1.1.1"
    private val DNS_2 = "1.0.0.1"

    // Notification Constants
    private val CHANNEL_ID = "alphonso_vpn_channel"
    private val NOTIFICATION_ID = 1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // --- FIX: Prevent Crash by showing Notification immediately ---
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        startVpn()
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alphonso DNS Protection",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alphonso Security")
            .setContentText("DNS Protection is Active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("Alphonso DNS")
                .addAddress("10.0.0.2", 32)
                .addDnsServer(DNS_1)
                .addDnsServer(DNS_2)
                .addRoute(DNS_1, 32)
                .addRoute(DNS_2, 32)
                .setMtu(1500)

            val interfaceDescriptor = builder.establish()

            // FIX: Don't crash if permission is missing, just wait.
            if (interfaceDescriptor == null) {
                Log.e("AlphonsoDNS", "VPN Permission not granted yet.")
                return
            }

            vpnInterface = interfaceDescriptor
            Log.i("AlphonsoDNS", "VPN Interface Established")

            keepAlive()

        } catch (e: Exception) {
            Log.e("AlphonsoDNS", "Error starting VPN", e)
            stopSelf()
        }
    }

    private fun keepAlive() {
        scope.launch {
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) return@launch

            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(fd)

            while (isActive && vpnInterface != null) {
                try {
                    val length = inputStream.read(buffer.array())
                    if (length > 0) buffer.clear()
                } catch (e: Exception) {
                    if (isActive) Log.e("AlphonsoDNS", "Traffic loop error", e)
                }
            }
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            scope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e("AlphonsoDNS", "Error stopping VPN", e)
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}