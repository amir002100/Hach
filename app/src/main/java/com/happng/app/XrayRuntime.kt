package com.happng.app

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

object XrayRuntime {
    @Volatile private var controller: CoreController? = null

    @Synchronized
    fun start(context: Context, config: String, tunFd: Int) {
        if (controller?.isRunning == true) return
        val app = context.applicationContext
        val assetDir = File(app.filesDir, "xray-assets").apply { mkdirs() }
        listOf("geoip.dat", "geosite.dat").forEach { name ->
            runCatching {
                app.assets.open(name).use { input ->
                    File(assetDir, name).outputStream().use(input::copyTo)
                }
            }
        }
        Seq.setContext(app)
        Libv2ray.initCoreEnv(assetDir.absolutePath, "")
        val callback = object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, message: String?): Long = 0
        }
        val core = Libv2ray.newCoreController(callback)
        core.startLoop(config, tunFd)
        check(core.isRunning) { "Xray core did not enter running state" }
        controller = core
    }

    @Synchronized
    fun stop() {
        controller?.let { runCatching { it.stopLoop() } }
        controller = null
    }

    fun isRunning(): Boolean = controller?.isRunning == true
}
