package com.example.calclocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.calclocker.data.LockerPrefs
import com.example.calclocker.service.LockerAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val label: String, val pkg: String, val icon: ImageBitmap)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupFlow(prefs: LockerPrefs, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<AppInfo?>(null) }
    var firstCode by remember { mutableStateOf("") }

    when (step) {
        0 -> AppPicker { app -> chosen = app; prefs.setTargetPackage(app.pkg); step = 1 }
        1 -> CodeEntry(
            title = "Create a 6-digit code",
            subtitle = "Type this into the calculator and press = to open ${chosen?.label ?: "the app"}."
        ) { code -> firstCode = code; step = 2 }
        2 -> CodeEntry(
            title = "Confirm your code",
            subtitle = "Enter the same 6 digits again.",
            mustMatch = firstCode
        ) { code -> prefs.setCode(code); step = 3 }
        3 -> AllSet(onDone)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(onPick: (AppInfo) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose an app to lock", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(apps) { app ->
                    ListItem(
                        leadingContent = {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                        },
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.pkg, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(app) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun CodeEntry(
    title: String,
    subtitle: String,
    mustMatch: String? = null,
    onSubmit: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { code = v; error = null } },
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = code.length == 6,
            onClick = {
                if (mustMatch != null && code != mustMatch) error = "Codes don't match. Try again."
                else onSubmit(code)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Continue") }
    }
}

@Composable
fun AllSet(onContinue: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u2705", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "From now on the app opens as a normal calculator. Type your code and press = to open the locked app.\n\n" +
                "IMPORTANT: to actually BLOCK the locked app when it's opened from its own icon, you must turn on BOTH permissions below. Without them the app stays openable as normal.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("1. Turn on Accessibility (required to block)")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { openOverlaySettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("2. Allow display over other apps")
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
fun SettingsScreen(prefs: LockerPrefs, onBack: () -> Unit, onReset: () -> Unit) {
    val context = LocalContext.current
    var sub by remember { mutableStateOf("main") }
    var firstCode by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset everything?") },
            text = {
                Text(
                    "This removes your 6-digit code and the locked-app choice, and returns the " +
                        "app to first-time setup. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            }
        )
    }

    when (sub) {
        "main" -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Locker settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Locked app: ${prefs.getTargetPackage() ?: "—"}")
            Spacer(Modifier.height(8.dp))
            Text(
                "Accessibility lock: ${if (isAccessibilityEnabled(context)) "ON" else "OFF"}  •  " +
                    "Overlay: ${if (Settings.canDrawOverlays(context)) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Accessibility lock settings")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { openOverlaySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Overlay permission settings")
            }
            Spacer(Modifier.height(20.dp))
            Text("Reset", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { sub = "code1" }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset code only")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { sub = "pickApp" }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset locked app only")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset everything")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back to calculator")
            }
        }
        "pickApp" -> AppPicker { app -> prefs.setTargetPackage(app.pkg); sub = "main" }
        "code1" -> CodeEntry("New 6-digit code", "Enter a new code.") { firstCode = it; sub = "code2" }
        "code2" -> CodeEntry("Confirm new code", "Re-enter the new code.", mustMatch = firstCode) {
            prefs.setCode(it); sub = "main"
        }
    }
}

/* --------------------------------- helpers --------------------------------- */

fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName) return@mapNotNull null
            val bitmap = runCatching { ri.loadIcon(pm).toBitmap(96, 96) }
                .getOrElse {
                    android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                }
            AppInfo(ri.loadLabel(pm).toString(), pkg, bitmap.asImageBitmap())
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun openOverlaySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, LockerAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
