package com.happng.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HappNgApp() }
    }
}

@Composable
private fun HappNgApp() {
    var input by remember { mutableStateOf("") }
    var profiles by remember { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf("برای شروع، لینک کانفیگ یا اشتراک را اضافه کن.") }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("HappNG", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("مدیریت پروفایل‌های اتصال", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("آمادهٔ پیکربندی", style = MaterialTheme.typography.titleLarge)
                        Text("هستهٔ تونل هنوز متصل نشده است", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { message = "اتصال واقعی تا زمان ادغام و آزمون هستهٔ Xray غیرفعال است." }, modifier = Modifier.fillMaxWidth()) { Text("اتصال") }
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("لینک کانفیگ یا Subscription") }, placeholder = { Text("vless://… یا https://…") }, minLines = 2)
                Button(onClick = {
                    val value = input.trim()
                    if (value.startsWith("vless://") || value.startsWith("vmess://") || value.startsWith("trojan://") || value.startsWith("ss://") || value.startsWith("https://") || value.startsWith("http://")) {
                        profiles = profiles + value
                        input = ""
                        message = "پروفایل به فهرست محلی اضافه شد."
                    } else message = "لینک پشتیبانی‌نشده یا خالی است."
                }, modifier = Modifier.fillMaxWidth()) { Text("افزودن پروفایل") }
                Text("پروفایل‌ها (${profiles.size})", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profiles) { profile ->
                        Card(Modifier.fillMaxWidth()) { Text(profile.take(72), Modifier.padding(14.dp)) }
                    }
                }
                Text("نسخهٔ اولیه: ذخیرهٔ موقت پروفایل‌ها؛ اتصال VPN و همگام‌سازی Subscription در مراحل بعدی اضافه می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
