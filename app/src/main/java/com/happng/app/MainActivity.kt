package com.happng.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        getSharedPreferences("happng", MODE_PRIVATE).edit()
            .putString("profiles", array.toString())
            .apply()
    }
}

@Composable
private fun HappNgApp(initialProfiles: List<String>, persist: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(initialProfiles) }
    var selected by remember { mutableIntStateOf(if (initialProfiles.isEmpty()) -1 else 0) }
    var message by remember { mutableStateOf("لینک سرور را اضافه و یک پروفایل را انتخاب کن.") }

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
                        Text("اتصال VPN تا تکمیل سرویس تونل غیرفعال است", color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = { message = "هسته Xray در حال ادغام است؛ هنوز اتصال واقعی فعال نشده." },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("اتصال") }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("لینک کانفیگ یا Subscription") },
                    placeholder = { Text("vless://… یا https://…") },
                    minLines = 2
                )
                Button(
                    onClick = {
                        val value = input.trim()
                        val valid = listOf("vless://", "vmess://", "trojan://", "ss://", "https://", "http://")
                            .any(value::startsWith)
                        if (valid) {
                            updateProfiles(profiles + value)
                            selected = profiles.lastIndex
                            input = ""
                            message = "پروفایل ذخیره شد و پس از بستن برنامه هم باقی می‌ماند."
                        } else {
                            message = "لینک خالی یا با قالب پشتیبانی‌نشده است."
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
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(profileName(profile), fontWeight = FontWeight.SemiBold)
                                    Text(profile.substringBefore("://"), style = MaterialTheme.typography.bodySmall)
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
                    "پروفایل‌ها روی همین دستگاه ذخیره می‌شوند. پشتیبانی از اشتراک و اتصال واقعی در مراحل بعدی تکمیل می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun profileName(link: String): String {
    return runCatching {
        val fragment = link.substringAfter("#", "")
        if (fragment.isNotBlank()) java.net.URLDecoder.decode(fragment, "UTF-8")
        else link.substringAfter("://").substringBefore("@").take(32)
    }.getOrDefault("Server")
}
