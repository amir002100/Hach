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
    private val prefs by lazy { getSharedPreferences("happng", MODE_PRIVATE) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_STOP -> {
                closeTunnel()
                updateState(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    updateState(false, "کانفیگ خالی است.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTunnel(config)
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(config: String) {
        // Close an old tunnel without stopping this service; the previous implementation
        // called stopSelf() here and could kill the service before the new tunnel started.
        closeTunnel()
        updateState(false, "در حال اتصال…")
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
            check(XrayRuntime.isRunning()) { "Xray core did not start" }
            updateState(true)
            updateNotification("VPN متصل است")
        } catch (error: Exception) {
            closeTunnel()
            val reason = error.message ?: error.javaClass.simpleName
            updateState(false, reason)
            updateNotification("خطا در اتصال: $reason")
            stopSelf()
        }
    }

    private fun closeTunnel() {
        XrayRuntime.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun updateState(connected: Boolean, error: String? = null) {
        prefs.edit().putBoolean("connected", connected)
            .putString("connection_error", error.orEmpty()).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "HappNG VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String = "در حال آماده‌سازی VPN"): Notification {
        val stopIntent = Intent(this, HappVpnService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setContentTitle("HappNG").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "قطع اتصال", pending).build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onRevoke() {
        closeTunnel()
        updateState(false, "مجوز VPN لغو شد.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunnel()
        updateState(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.happng.app.START"
        const val ACTION_STOP = "com.happng.app.STOP"
        const val EXTRA_CONFIG = "xray_config"
        private const val CHANNEL_ID = "happng_vpn"
        private const val NOTIFICATION_ID = 701
    }
}
