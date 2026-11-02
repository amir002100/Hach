package com.happng.app

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = loadProfiles()
        setContent { HappNgApp(saved, ::saveProfiles) }
    }

    private fun loadProfiles(): List<String> {
        val raw = getSharedPreferences("happng", MODE_PRIVATE).getString("profiles", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.getString(index) }
        }.getOrDefault(emptyList())
    }

    private fun saveProfiles(profiles: List<String>) {
        val array = JSONArray()
        profiles.forEach(array::put)
        getSharedPreferences("happng", MODE_PRIVATE).edit().putString("profiles", array.toString()).apply()
    }
}

@Composable
private fun HappNgApp(initialProfiles: List<String>, persist: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(initialProfiles) }
    var selected by remember { mutableIntStateOf(if (initialProfiles.isEmpty()) -1 else 0) }
    var message by remember { mutableStateOf("لینک سرور یا Subscription را وارد کن.") }
    var pendingConfig by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            connected = context.getSharedPreferences("happng", 0).getBoolean("connected", false)
            kotlinx.coroutines.delay(700)
        }
    }

    fun startVpn(config: String) {
        val intent = Intent(context, HappVpnService::class.java)
            .setAction(HappVpnService.ACTION_START)
            .putExtra(HappVpnService.EXTRA_CONFIG, config)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            message = "درخواست اتصال ارسال شد؛ در حال بررسی وضعیت تونل…"
        } catch (e: Exception) {
            message = "شروع سرویس VPN ناموفق بود: ${e.message}"
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val config = pendingConfig
        pendingConfig = null
        if (result.resultCode == android.app.Activity.RESULT_OK && config != null) startVpn(config)
        else message = "مجوز VPN داده نشد؛ اتصال شروع نشد."
    }

    fun updateProfiles(next: List<String>) {
        profiles = next.distinct()
        persist(profiles)
        selected = when {
            profiles.isEmpty() -> -1
            selected !in profiles.indices -> 0
            else -> selected
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("HappNG", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("مدیریت پروفایل‌ها و اشتراک‌ها", color = MaterialTheme.colorScheme.onSurfaceVariant)

                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selected >= 0) "سرور انتخاب‌شده: ${profileName(profiles[selected])}" else "هنوز سروری اضافه نشده", style = MaterialTheme.typography.titleMedium)
                        Text(if (connected) "VPN متصل است" else "VPN قطع است", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = {
                            if (connected) {
                                val stop = Intent(context, HappVpnService::class.java).setAction(HappVpnService.ACTION_STOP)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(stop) else context.startService(stop)
                                message = "درخواست قطع اتصال ارسال شد."
                            } else if (selected >= 0) {
                                try {
                                    val config = XrayConfigBuilder.fromShareLink(profiles[selected])
                                    val prepare = VpnService.prepare(context)
                                    if (prepare == null) startVpn(config) else {
                                        pendingConfig = config
                                        vpnPermission.launch(prepare)
                                    }
                                } catch (e: Exception) {
                                    message = e.message ?: "ساخت کانفیگ ناموفق بود."
                                }
                            } else message = "ابتدا یک سرور اضافه و انتخاب کن."
                        }, enabled = connected || selected >= 0, modifier = Modifier.fillMaxWidth()) {
                            Text(if (connected) "قطع اتصال" else "اتصال")
                        }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("لینک مستقیم یا Subscription URL") },
                    placeholder = { Text("vless://… یا https://…") }, minLines = 2
                )
                Button(
                    enabled = input.isNotBlank() && !importing,
                    onClick = {
                        val value = input.trim()
                        importing = true
                        scope.launch {
                            message = "در حال دریافت و پردازش لینک…"
                            val result = withContext(Dispatchers.IO) { runCatching { importProfiles(value) } }
                            importing = false
                            result.onSuccess { imported ->
                                if (imported.isEmpty()) message = "لینک دریافت شد، اما کانفیگ پشتیبانی‌شده‌ای داخل آن پیدا نشد."
                                else {
                                    val before = profiles.size
                                    updateProfiles(profiles + imported)
                                    selected = profiles.lastIndex
                                    input = ""
                                    message = "${imported.size} کانفیگ VLESS اضافه شد (تکراری‌ها حذف شدند)."
                                }
                            }.onFailure { e ->
                                message = "دریافت Subscription ناموفق بود: ${e.message ?: "خطای نامشخص"}"
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (importing) "در حال دریافت…" else "دریافت و افزودن کانفیگ‌ها") }

                Text("سرورها (${profiles.size})", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(profiles) { index, profile ->
                        Card(onClick = { selected = index; message = "سرور انتخاب شد." }, modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (selected == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(profileName(profile), fontWeight = FontWeight.SemiBold)
                                    Text("VLESS", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { updateProfiles(profiles.toMutableList().also { it.removeAt(index) }); message = "سرور حذف شد." }) { Text("حذف") }
                            }
                        }
                    }
                }
                Text("ورود Subscription در این نسخه کانفیگ‌های VLESS موجود در متن اشتراک را استخراج می‌کند. پروتکل‌های دیگر هنوز اضافه نشده‌اند.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Accepts a direct share URI, a plain-text subscription, or a base64-encoded subscription. */
private fun importProfiles(value: String): List<String> {
    if (value.startsWith("vless://", ignoreCase = true)) return listOf(value)
    require(value.startsWith("https://", true) || value.startsWith("http://", true)) {
        "لینک باید با http، https یا vless شروع شود."
    }
    val connection = (URL(value).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "HappNG/0.2")
        setRequestProperty("Accept", "text/plain, */*")
    }
    val body = try {
        val code = connection.responseCode
        require(code in 200..299) { "سرور Subscription کد HTTP $code برگرداند." }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally { connection.disconnect() }

    val candidates = sequenceOf(body, decodeSubscription(body)).filterNotNull()
    val lines = candidates.flatMap { it.lineSequence() }
        .map { it.trim().removePrefix("\uFEFF") }
        .filter { it.startsWith("vless://", true) }
        .distinct()
        .toList()
    return lines
}

private fun decodeSubscription(body: String): String? {
    val compact = body.trim().replace("\\s".toRegex(), "")
    if (compact.isBlank() || compact.startsWith("vless://", true)) return null
    val normalized = compact.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
    return runCatching { String(Base64.getDecoder().decode(normalized), Charsets.UTF_8) }.getOrNull()
        ?: runCatching { String(android.util.Base64.decode(normalized, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
}

private fun profileName(link: String): String =
    runCatching {
        val fragment = link.substringAfter("#", "")
        if (fragment.isNotBlank()) java.net.URLDecoder.decode(fragment, "UTF-8")
        else link.substringAfter("://").substringBefore("@").take(32)
    }.getOrDefault("Server")
