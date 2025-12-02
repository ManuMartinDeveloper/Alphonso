package com.alphonso

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class CloudflareDnsService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    // SupervisorJob ensures the scope doesn't die if one child fails
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val DNS_1 = "1.1.1.1"
    private val DNS_2 = "1.0.0.1"
    private val CHANNEL_ID = "alphonso_vpn_channel"
    private val NOTIFICATION_ID = 999

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // --- 1. DEFINE THE NOTIFICATION FIRST ---
        // This fixes the "Unresolved reference: notification" error
        val notification = createNotification()

        // --- 2. START FOREGROUND IMMEDIATELY ---
        try {
            if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback if special use isn't allowed or older SDK
            startForeground(NOTIFICATION_ID, notification)
        }

        // --- 3. START WATCHDOG (Freezes Settings) ---
        startPolicyWatchdog()

        // --- 4. START VPN ---
        startVpn()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alphonso Active Defense",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Alphonso Security")
            .setContentText("Active Defense & DNS Protection Running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun startPolicyWatchdog() {
        serviceScope.launch {
            while (isActive) {
                // WATCHDOG LOOP: Checks every 2 seconds
                try {
                    // This calls your "God Mode" function from PolicyManager
                    // If Accessibility is OFF, this turns it back ON instantly.
                    PolicyManager.enforcePolicies(applicationContext)
                    Log.d("AlphonsoWatchdog", "Policy enforcement check passed")
                } catch (e: Exception) {
                    Log.e("AlphonsoWatchdog", "Watchdog error", e)
                }
                delay(2000L)
            }
        }
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

            // FIX: Don't crash if permission is missing (null)
            // Just wait for the PolicyManager or User to grant it.
            val interfaceDescriptor = builder.establish() ?: run {
                Log.w("AlphonsoDNS", "VPN Permission missing. Waiting...")
                return
            }

            vpnInterface = interfaceDescriptor
            Log.i("AlphonsoDNS", "VPN Interface Established")

            startTrafficLoop()

        } catch (e: Exception) {
            Log.e("AlphonsoDNS", "Error starting VPN", e)
        }
    }

    private fun startTrafficLoop() {
        serviceScope.launch {
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) return@launch

            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(fd)

            while (isActive && vpnInterface != null) {
                try {
                    // Keep the VPN alive by reading packets
                    val length = inputStream.read(buffer.array())
                    if (length > 0) buffer.clear()
                } catch (e: Exception) {
                    if (isActive) Log.e("AlphonsoDNS", "Traffic loop error", e)
                    break
                }
            }
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            serviceScope.cancel() // Kills the Watchdog
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            Log.i("AlphonsoDNS", "VPN Stopped")
        } catch (e: Exception) {
            Log.e("AlphonsoDNS", "Error stopping VPN", e)
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}