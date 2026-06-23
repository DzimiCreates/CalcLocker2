package com.example.calclocker.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Small persistence layer: which app is guarded + a salted SHA-256 hash of the code.
 * The code itself is never stored in plaintext.
 */
class LockerPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("calc_locker", Context.MODE_PRIVATE)

    fun getTargetPackage(): String? = sp.getString(KEY_TARGET, null)

    fun setTargetPackage(pkg: String) = sp.edit().putString(KEY_TARGET, pkg).apply()

    fun isConfigured(): Boolean =
        sp.contains(KEY_TARGET) && sp.contains(KEY_HASH) && sp.contains(KEY_SALT)

    fun setCode(code: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(code, salt))
            .apply()
    }

    /**
     * True if the LAST [CODE_LEN] digits of what's currently on the calculator display
     * match the stored code. Taking the trailing digits lets the user "clear" mistakes
     * by simply continuing to type the right code.
     */
    fun matchesCode(normalizedDigits: String): Boolean {
        if (normalizedDigits.length < CODE_LEN) return false
        val saltHex = sp.getString(KEY_SALT, null) ?: return false
        val stored = sp.getString(KEY_HASH, null) ?: return false
        val candidate = normalizedDigits.takeLast(CODE_LEN)
        return hash(candidate, saltHex.fromHex()) == stored
    }

    fun clear() = sp.edit().clear().apply()

    private fun hash(code: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(code.toByteArray())
        return md.digest().toHex()
    }

    companion object {
        const val CODE_LEN = 6
        private const val KEY_TARGET = "target_pkg"
        private const val KEY_HASH = "code_hash"
        private const val KEY_SALT = "code_salt"
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
private fun String.fromHex(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
