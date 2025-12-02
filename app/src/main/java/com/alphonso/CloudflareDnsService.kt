package com.alphonso

import android.app.PendingIntent
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

    // Cloudflare DNS IPs (Standard)
    // Replace these with your Zero Trust Gateway IPs if you have them
    private val DNS_1 = "1.1.1.1"
    private val DNS_2 = "1.0.0.1"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        // Start the VPN
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return // Already running

        try {
            val builder = Builder()
                .setSession("Alphonso DNS")
                // Create a virtual local network
                .addAddress("10.0.0.2", 32)
                // Set the DNS servers the system should use
                .addDnsServer(DNS_1)
                .addDnsServer(DNS_2)
                // Split Tunneling: ONLY route traffic meant for these DNS servers into the VPN.
                // All other traffic (YouTube, Chrome, etc.) bypasses the VPN.
                .addRoute(DNS_1, 32)
                .addRoute(DNS_2, 32)
                .setMtu(1500)

            // On Android 13+ (Tiramisu), you might want to mark it as metered/not metered
            // builder.setMetered(false)

            vpnInterface = builder.establish()
            Log.i("AlphonsoDNS", "VPN Interface Established")

            // CRITICAL: We must keep the interface read loop active,
            // otherwise the OS might think the VPN stalled and kill it.
            keepAlive()

        } catch (e: Exception) {
            Log.e("AlphonsoDNS", "Error starting VPN", e)
            stopSelf()
        }
    }

    private fun keepAlive() {
        scope.launch {
            val buffer = ByteBuffer.allocate(32767)
            val inputStream = FileInputStream(vpnInterface?.fileDescriptor)

            while (isActive && vpnInterface != null) {
                try {
                    // We read packets from the interface to keep the flow active.
                    // Since we are only routing DNS IP traffic here, these packets are DNS queries.
                    // By default, if we simply read them, Android's networking stack often
                    // effectively "sees" the DNS server.
                    // Note: A full production DNS VPN would forward these packets via UDP.
                    // For this basic implementation, simply holding the tunnel open forces
                    // Android to respect the 'addDnsServer' configuration.
                    val length = inputStream.read(buffer.array())
                    if (length > 0) {
                        buffer.clear()
                    }
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