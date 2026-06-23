package com.example.calclocker

import android.os.SystemClock

/**
 * In-process signal (calculator and accessibility service share one process).
 * The calculator calls [unlock] right before launching the guarded app; the guard
 * service reads [isUnlocked] for a short window to let the app come to the foreground,
 * then keeps it unlocked for that session until the user leaves it.
 */
object UnlockGate {
    @Volatile private var until = 0L
    private const val WINDOW_MS = 8000L

    fun unlock() { until = SystemClock.elapsedRealtime() + WINDOW_MS }
    fun isUnlocked() = SystemClock.elapsedRealtime() < until
    fun lock() { until = 0L }
}
