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
import org.json.JSONArray

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
    var input by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(initialProfiles) }
    var selected by remember { mutableIntStateOf(if (initialProfiles.isEmpty()) -1 else 0) }
    var message by remember { mutableStateOf("لینک VLESS را اضافه و انتخاب کن.") }
    var pendingConfig by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(XrayRuntime.isRunning()) }

    fun startVpn(config: String) {
        val intent = Intent(context, HappVpnService::class.java)
            .setAction(HappVpnService.ACTION_START)
            .putExtra(HappVpnService.EXTRA_CONFIG, config)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            message = "درخواست اتصال ارسال شد؛ وضعیت نهایی در اعلان VPN مشخص می‌شود."
        } catch (e: Exception) {
            message = "شروع سرویس VPN ناموفق بود: ${e.message}"
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val config = pendingConfig
        pendingConfig = null
        if (result.resultCode == android.app.Activity.RESULT_OK && config != null) {
            startVpn(config)
        } else {
            message = "مجوز VPN داده نشد؛ اتصال شروع نشد."
        }
    }

    fun updateProfiles(next: List<String>) {
        profiles = next
        persist(next)
        selected = when {
            next.isEmpty() -> -1
            selected !in next.indices -> 0
            else -> selected
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("HappNG", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("مدیریت پروفایل‌های اتصال", color = MaterialTheme.colorScheme.onSurfaceVariant)

                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (selected >= 0) "پروفایل انتخاب‌شده: ${profileName(profiles[selected])}" else "هنوز سروری اضافه نشده",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(if (connected) "هسته Xray در حال اجراست" else "آمادهٔ اتصال", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                if (connected) {
                                    context.startService(Intent(context, HappVpnService::class.java).setAction(HappVpnService.ACTION_STOP))
                                    connected = false
                                    message = "درخواست قطع اتصال ارسال شد."
                                } else if (selected >= 0) {
                                    try {
                                        val config = XrayConfigBuilder.fromShareLink(profiles[selected])
                                        val prepare = VpnService.prepare(context)
                                        if (prepare == null) startVpn(config)
                                        else {
                                            pendingConfig = config
                                            vpnPermission.launch(prepare)
                                        }
                                    } catch (e: Exception) {
                                        message = e.message ?: "ساخت کانفیگ ناموفق بود."
                                    }
                                } else {
                                    message = "ابتدا یک پروفایل VLESS اضافه و انتخاب کن."
                                }
                            },
                            enabled = connected || selected >= 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (connected) "قطع اتصال" else "اتصال") }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("لینک سرور یا Subscription") },
                    placeholder = { Text("vless://…") },
                    minLines = 2
                )
                Button(
                    onClick = {
                        val value = input.trim()
                        if (value.startsWith("vless://", ignoreCase = true)) {
                            updateProfiles(profiles + value)
                            selected = profiles.lastIndex
                            input = ""
                            message = "پروفایل VLESS ذخیره شد."
                        } else {
                            message = "در این مرحله فقط لینک مستقیم VLESS برای اتصال قابل تبدیل است."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("افزودن پروفایل") }

                Text("پروفایل‌ها (${profiles.size})", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(profiles) { index, profile ->
                        Card(
                            onClick = {
                                selected = index
                                message = "پروفایل انتخاب شد."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == index) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(profileName(profile), fontWeight = FontWeight.SemiBold)
                                    Text("VLESS", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    updateProfiles(profiles.toMutableList().also { it.removeAt(index) })
                                    message = "پروفایل حذف شد."
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
                Text(
                    "پروفایل‌ها روی دستگاه ذخیره می‌شوند. فعلاً مبدل اتصال برای VLESS پیاده‌سازی شده؛ سایر پروتکل‌ها و اشتراک‌ها هنوز در دست توسعه‌اند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun profileName(link: String): String =
    runCatching {
        val fragment = link.substringAfter("#", "")
        if (fragment.isNotBlank()) java.net.URLDecoder.decode(fragment, "UTF-8")
        else link.substringAfter("://").substringBefore("@").take(32)
    }.getOrDefault("Server")
