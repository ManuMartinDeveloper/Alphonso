package com.alphonso

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

    private val DNS_1 = "1.1.1.1"
    private val DNS_2 = "1.0.0.1"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
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

            // Attempt to create the interface
            val interfaceDescriptor = builder.establish()

            // FIX: Check if null (Permission denied or revoked)
            if (interfaceDescriptor == null) {
                Log.e("AlphonsoDNS", "VPN Permission not granted. Waiting for user/admin approval.")
                stopSelf()
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
            // FIX: Safe call to verify vpnInterface is not null
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