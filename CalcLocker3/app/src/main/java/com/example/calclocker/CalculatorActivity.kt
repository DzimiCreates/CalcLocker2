@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.calclocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.calclocker.data.LockerPrefs
import java.math.BigDecimal
import java.math.MathContext

class CalculatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) { App() }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val prefs = remember { LockerPrefs(context) }
    var configured by remember { mutableStateOf(prefs.isConfigured()) }

    if (configured) {
        ConfiguredApp(prefs, onReset = { configured = false })
    } else {
        SetupFlow(prefs) { configured = true }
    }
}

/** Once configured, the app is just a calculator. Long-press AC -> code gate -> Settings. */
@Composable
private fun ConfiguredApp(prefs: LockerPrefs, onReset: () -> Unit) {
    var screen by remember { mutableStateOf("calc") }
    var gateOpen by remember { mutableStateOf(false) }

    when (screen) {
        "calc" -> {
            CalculatorScreen(prefs) { gateOpen = true }
            if (gateOpen) {
                CodeGateDialog(
                    prefs = prefs,
                    onDismiss = { gateOpen = false },
                    onSuccess = { gateOpen = false; screen = "settings" }
                )
            }
        }
        "settings" -> SettingsScreen(
            prefs = prefs,
            onBack = { screen = "calc" },
            onReset = { prefs.clear(); onReset() }
        )
    }
}

private val OPERATORS = setOf("÷", "×", "−", "+")

@Composable
private fun CalculatorScreen(prefs: LockerPrefs, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var display by remember { mutableStateOf("0") }
    var justEvaluated by remember { mutableStateOf(false) }
    var codeAccepted by remember { mutableStateOf(false) }

    fun endsWithOperator(s: String) = s.isNotEmpty() && s.last().toString() in OPERATORS

    fun unlockAndLaunch() {
        UnlockGate.unlock()
        prefs.getTargetPackage()?.let { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        // The entered code only ever lived in `display`; clearing it deletes it.
        display = "0"
        justEvaluated = false
        codeAccepted = false
    }

    fun press(key: String) {
        if (codeAccepted) return   // ignore input during the green "code good" flash
        when (key) {
            "AC" -> { display = "0"; justEvaluated = false }
            "DEL" -> {
                if (justEvaluated) { display = "0"; justEvaluated = false }
                else {
                    display = if (display.length <= 1) "0" else display.dropLast(1)
                    if (display.isEmpty()) display = "0"
                }
            }
            "±" -> display = toggleSign(display)
            "%" -> CalcEngine.evaluate(display)?.let { display = fmt(it * 0.01); justEvaluated = true }
            "=" -> {
                val plain = display.trim()
                // Unlock ONLY when the 6 digits were typed in directly. `justEvaluated` is
                // true whenever the display is the result of a calculation (= or %), so a
                // code that merely *appears* on screen as a result will never unlock.
                if (!justEvaluated && plain.length == 6 && plain.all { it.isDigit() } && prefs.matchesCode(plain)) {
                    // Code is good: turn the digits green for a moment, then open the app.
                    codeAccepted = true
                    scope.launch {
                        delay(550)
                        unlockAndLaunch()
                    }
                } else {
                    val v = CalcEngine.evaluate(display)
                    display = v?.let { fmt(it) } ?: "Error"
                    justEvaluated = true
                }
            }
            in OPERATORS -> {
                if (display == "Error") return
                justEvaluated = false
                display = if (endsWithOperator(display)) display.dropLast(1) + key else display + key
            }
            "." -> {
                if (justEvaluated) { display = "0."; justEvaluated = false; return }
                val seg = display.takeLastWhile { it.toString() !in OPERATORS }
                if (!seg.contains(".")) display = if (display.isEmpty()) "0." else "$display."
            }
            else -> { // a digit
                if (justEvaluated || display == "0" || display == "Error") {
                    display = key; justEvaluated = false
                } else display += key
            }
        }
    }

    val rows = listOf(
        listOf("AC", "DEL", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                color = if (codeAccepted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.displayMedium
            )
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = key in OPERATORS || key == "="
                    CalcButton(
                        label = key,
                        modifier = Modifier.weight(1f),
                        container = when {
                            key == "=" -> MaterialTheme.colorScheme.primary
                            isOp -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        content = if (key == "=") MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        onClick = { press(key) },
                        onLongClick = if (key == "AC") onOpenSettings else null
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        color = container,
        shape = CircleShape,
        modifier = modifier
            .padding(6.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = content, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun CodeGateDialog(prefs: LockerPrefs, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter your code") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) { code = v; error = false } },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error) Text("Wrong code", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (code.length == 6 && prefs.matchesCode(code)) onSuccess() else error = true
            }) { Text("Open settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* --------------------------------- helpers --------------------------------- */

private fun fmt(d: Double): String {
    if (d.isNaN() || d.isInfinite()) return "Error"
    if (d == Math.floor(d) && Math.abs(d) < 1e15) return d.toLong().toString()
    return BigDecimal(d).round(MathContext(12)).stripTrailingZeros().toPlainString()
}

private fun toggleSign(expr: String): String {
    val m = Regex("(-?\\d*\\.?\\d+)$").find(expr) ?: return expr
    val num = m.value
    val prefix = expr.substring(0, m.range.first)
    val toggled = if (num.startsWith("-")) num.drop(1) else "-$num"
    return prefix + toggled
}
