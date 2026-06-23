package com.example.calclocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.example.calclocker.UnlockGate
import com.example.calclocker.data.LockerPrefs
import com.example.calclocker.lock.LockScreenActivity

/**
 * Optional enforcement. Because the disguised calculator is now the only intended way in,
 * this service exists to stop the guarded app being opened directly from its launcher icon.
 *
 * It does NOT read any app's content — it only looks at which app is in the foreground.
 * The unlock signal comes from our own calculator via [UnlockGate].
 */
class LockerAccessibilityService : AccessibilityService() {

    private lateinit var prefs: LockerPrefs

    private val ignored = setOf("com.example.calclocker", "com.android.systemui")
    private var sessionUnlocked = false
    private var lastLockShownAt = 0L

    override fun onServiceConnected() {
        prefs = LockerPrefs(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::prefs.isInitialized) prefs = LockerPrefs(this)
        if (!prefs.isConfigured()) return

        val pkg = event.packageName?.toString() ?: return
        val target = prefs.getTargetPackage() ?: return

        when {
            pkg == target -> {
                if (sessionUnlocked || UnlockGate.isUnlocked()) sessionUnlocked = true
                else showLock()
            }
            isIgnored(pkg) -> { /* keep current state */ }
            else -> sessionUnlocked = false   // left the app -> re-lock for next time
        }
    }

    override fun onInterrupt() {}

    private fun showLock() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLockShownAt < 700) return
        lastLockShownAt = now
        if (Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(this, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        } else {
            // Without "display over other apps" we can't reliably launch our lock screen
            // from the background, so at minimum bounce the user out of the locked app.
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun isIgnored(pkg: String): Boolean =
        pkg in ignored || pkg.contains("inputmethod", ignoreCase = true) || pkg.endsWith(".ime")
}
