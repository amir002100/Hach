package com.happng.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor

class HappVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_STOP -> stopTunnel()
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTunnel(config)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(config: String) {
        stopTunnel()
        try {
            val established = Builder()
                .setSession("HappNG")
                .setMtu(1500)
                .addAddress("10.66.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .establish() ?: error("Android did not establish the VPN interface")
            vpnInterface = established
            XrayRuntime.start(this, config, established.fd)
            updateNotification("VPN متصل است")
        } catch (error: Exception) {
            stopTunnel()
            updateNotification("خطا در اتصال: ${error.message ?: "unknown"}")
            stopSelf()
        }
    }

    private fun stopTunnel() {
        XrayRuntime.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "HappNG VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String = "در حال آماده‌سازی VPN"): Notification {
        val stopIntent = Intent(this, HappVpnService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder.setContentTitle("HappNG")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "قطع اتصال", pending)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.happng.app.START"
        const val ACTION_STOP = "com.happng.app.STOP"
        const val EXTRA_CONFIG = "xray_config"
        private const val CHANNEL_ID = "happng_vpn"
        private const val NOTIFICATION_ID = 701
    }
}
