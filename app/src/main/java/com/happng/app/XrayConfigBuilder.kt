package com.happng.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Converts a VLESS share URI into an Xray JSON configuration.
 * Unsupported transports/security options fail explicitly instead of silently
 * producing a profile that appears connected but cannot carry traffic.
 */
object XrayConfigBuilder {
    fun fromShareLink(link: String): String {
        require(link.startsWith("vless://", ignoreCase = true)) {
            "فعلاً ساخت کانفیگ اجرایی فقط برای لینک‌های VLESS فعال است."
        }
        val uri = Uri.parse(link)
        val uuid = uri.userInfo?.substringBefore(':')?.takeIf { it.isNotBlank() }
            ?: error("شناسه UUID در لینک VLESS پیدا نشد.")
        val host = uri.host?.takeIf { it.isNotBlank() } ?: error("آدرس سرور در لینک موجود نیست.")
        val port = uri.port.takeIf { it in 1..65535 } ?: error("پورت سرور معتبر نیست.")
        val q = uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        val network = q["type"].orEmpty().ifBlank { "tcp" }
        val security = q["security"].orEmpty().ifBlank { "none" }

        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", q["encryption"].orEmpty().ifBlank { "none" })
        q["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }

        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(
                JSONObject()
                    .put("address", host)
                    .put("port", port)
                    .put("users", JSONArray().put(user))
            )))
        val stream = JSONObject().put("network", network).put("security", security)

        when (security) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", q["sni"].orEmpty().ifBlank { host })
                    .put("allowInsecure", q["allowInsecure"] == "1" || q["allowInsecure"] == "true")
                q["fp"]?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                stream.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", q["sni"].orEmpty().ifBlank { host })
                    .put("fingerprint", q["fp"].orEmpty().ifBlank { "chrome" })
                    .put("publicKey", q["pbk"].orEmpty())
                    .put("shortId", q["sid"].orEmpty())
                    .put("spiderX", q["spx"].orEmpty().ifBlank { "/" })
                require(reality.getString("publicKey").isNotBlank()) {
                    "پارامتر کلید عمومی Reality (pbk) در لینک موجود نیست."
                }
                stream.put("realitySettings", reality)
            }
            "none" -> Unit
            else -> error("نوع امنیت $security هنوز در مبدل VLESS پیاده‌سازی نشده است.")
        }

        when (network) {
            "tcp" -> {
                val headerType = q["headerType"].orEmpty()
                if (headerType.isNotBlank() && headerType != "none") {
                    stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", headerType)))
                }
            }
            "ws" -> {
                val headers = JSONObject()
                q["host"]?.takeIf { it.isNotBlank() }?.let { headers.put("Host", it) }
                stream.put("wsSettings", JSONObject()
                    .put("path", q["path"].orEmpty().ifBlank { "/" })
                    .put("headers", headers))
            }
            "grpc" -> {
                stream.put("grpcSettings", JSONObject()
                    .put("serviceName", q["serviceName"].orEmpty())
                    .put("multiMode", q["mode"] == "multi"))
            }
            "httpupgrade" -> {
                stream.put("httpupgradeSettings", JSONObject()
                    .put("path", q["path"].orEmpty().ifBlank { "/" })
                    .put("host", q["host"].orEmpty()))
            }
            else -> error("انتقال $network هنوز در مبدل VLESS پیاده‌سازی نشده است.")
        }
        outbound.put("streamSettings", stream)

        val tun = JSONObject()
            .put("tag", "tun")
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500).put("userLevel", 8))
        val direct = JSONObject().put("tag", "direct").put("protocol", "freedom")
        val block = JSONObject().put("tag", "block").put("protocol", "blackhole")
            .put("settings", JSONObject().put("response", JSONObject().put("type", "http")))

        val config = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", JSONArray().put(outbound).put(direct).put(block))
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray()
                    .put(JSONObject().put("type", "field").put("ip", JSONArray().put("geoip:private")).put("outboundTag", "direct"))
                    .put(JSONObject().put("type", "field").put("network", "tcp,udp").put("outboundTag", "proxy"))))
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
        return config.toString()
    }
}
