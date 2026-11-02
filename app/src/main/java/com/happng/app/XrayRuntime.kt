package com.happng.app

import android.content.Context
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

object XrayRuntime {
    private const val TAG = "HappNG-Xray"

    @Volatile private var controller: CoreController? = null

    @Synchronized
    fun start(context: Context, config: String, tunFd: Int) {
        if (controller?.isRunning == true) {
            Log.w(TAG, "Start ignored: Xray is already running")
            return
        }
        require(tunFd >= 0) { "Invalid Android TUN file descriptor" }

        val app = context.applicationContext
        val assetDir = File(app.filesDir, "xray-assets").apply { mkdirs() }
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            val target = File(assetDir, name)
            if (!target.exists() || target.length() == 0L) {
                runCatching {
                    app.assets.open(name).use { input ->
                        target.outputStream().use(input::copyTo)
                    }
                }.onFailure { Log.w(TAG, "Optional asset $name is not bundled", it) }
            }
        }

        Log.i(TAG, "Initializing Xray core; assets=${assetDir.absolutePath}")
        Seq.setContext(app)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        val callback = object : CoreCallbackHandler {
            override fun startup(): Long {
                Log.i(TAG, "Core callback: startup")
                return 0
            }

            override fun shutdown(): Long {
                Log.i(TAG, "Core callback: shutdown")
                return 0
            }

            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.i(TAG, "Core status $code: ${message.orEmpty()}")
                return 0
            }
        }

        val core = Libv2ray.newCoreController(callback)
        Log.i(TAG, "Calling startLoop with TUN fd=$tunFd")
        core.startLoop(config, tunFd)
        check(core.isRunning) { "Xray core did not enter running state" }
        controller = core
        Log.i(TAG, "Xray core is running")
    }

    @Synchronized
    fun stop() {
        val current = controller ?: return
        Log.i(TAG, "Stopping Xray core")
        runCatching { current.stopLoop() }
            .onFailure { Log.e(TAG, "Core stopLoop failed", it) }
        controller = null
    }

    fun isRunning(): Boolean = controller?.isRunning == true
}
