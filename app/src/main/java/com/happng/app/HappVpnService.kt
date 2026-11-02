package com.happng.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the Android VPN descriptor and Xray lifecycle.
 *
 * Xray startup/shutdown can perform blocking native work. Never run it on the
 * Android service main thread: doing so can freeze the app and trigger an ANR.
 */
class HappVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val prefs by lazy { getSharedPreferences("happng", MODE_PRIVATE) }
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HappNG-Xray").apply { isDaemon = true }
    }
    private val closing = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                updateState(false, "در حال قطع اتصال…")
                worker.execute {
                    closeTunnel()
                    updateState(false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config.isNullOrBlank()) {
                    updateState(false, "کانفیگ خالی است.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                closing.set(false)
                updateState(false, "در حال آماده‌سازی اتصال…")
                worker.execute { startTunnel(config, startId) }
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(config: String, startId: Int) {
        if (closing.get()) return
        closeTunnel()
        updateState(false, "در حال راه‌اندازی هسته Xray…")
        try {
            val established = Builder()
                .setSession("HappNG")
                .setMtu(1500)
                .addAddress("10.66.0.1", 30)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .establish() ?: error("Android نتوانست رابط VPN را ایجاد کند")

            if (closing.get()) {
                established.close()
                return
            }
            vpnInterface = established
            Log.i(TAG, "Starting Xray; TUN fd=${established.fd}")
            XrayRuntime.start(this, config, established.fd)
            check(XrayRuntime.isRunning()) { "هسته Xray اجرا نشد." }

            if (closing.get()) {
                closeTunnel()
                return
            }
            updateState(true, "در حال آزمایش مسیر واقعی سرور…")
            updateNotification("هسته روشن شد؛ در حال آزمایش اینترنت")
            Log.i(TAG, "Xray core started; running outbound health checks")

            // Test through Xray itself. Core-running alone does not mean the
            // VLESS server is reachable or that the outbound can return data.
            val checks = listOf(
                "https://www.cloudflare.com/cdn-cgi/trace",
                "https://www.google.com/generate_204"
            )
            var lastFailure: Throwable? = null
            var latency: Long? = null
            for (url in checks) {
                try {
                    latency = XrayRuntime.measureDelay(url)
                    break
                } catch (failure: Exception) {
                    lastFailure = failure
                    Log.w(TAG, "Outbound health check failed for $url", failure)
                }
            }
            if (latency != null) {
                updateState(true, "اینترنت از تونل فعال است؛ تأخیر ${latency}ms")
                updateNotification("اتصال اینترنت برقرار است · ${latency}ms")
                Log.i(TAG, "Outbound health check passed: ${latency}ms")
            } else {
                val reason = lastFailure?.message ?: "پاسخی از سرور دریافت نشد"
                updateState(true, "هسته روشن است، اما اینترنت از تونل عبور نمی‌کند: $reason")
                updateNotification("هسته روشن؛ ارتباط سرور ناموفق")
                Log.e(TAG, "Core is running but outbound health checks failed: $reason", lastFailure)
            }
        } catch (error: Exception) {
            Log.e(TAG, "VPN startup failed", error)
            closeTunnel()
            val reason = error.message ?: error.javaClass.simpleName
            updateState(false, reason)
            updateNotification("خطا در اتصال: $reason")
            stopSelf(startId)
        }
    }

    private fun closeTunnel() {
        runCatching { XrayRuntime.stop() }
            .onFailure { Log.e(TAG, "Xray shutdown failed", it) }
        runCatching { vpnInterface?.close() }
            .onFailure { Log.e(TAG, "TUN close failed", it) }
        vpnInterface = null
    }

    private fun updateState(connected: Boolean, error: String? = null) {
        prefs.edit()
            .putBoolean("connected", connected)
            .putString("connection_error", error.orEmpty())
            .apply()
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
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle("HappNG")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "قطع اتصال", pending)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onRevoke() {
        closing.set(true)
        worker.execute {
            closeTunnel()
            updateState(false, "مجوز VPN لغو شد.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        closing.set(true)
        // Queue cleanup behind any in-flight start so the TUN fd is never closed
        // while Xray is still initializing against it.
        worker.execute {
            closeTunnel()
            updateState(false)
        }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.happng.app.START"
        const val ACTION_STOP = "com.happng.app.STOP"
        const val EXTRA_CONFIG = "xray_config"
        private const val TAG = "HappNG-VPN"
        private const val CHANNEL_ID = "happng_vpn"
        private const val NOTIFICATION_ID = 701
    }
}
