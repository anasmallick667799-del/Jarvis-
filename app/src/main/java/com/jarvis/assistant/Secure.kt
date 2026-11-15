package com.jarvis.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object Secure {
    private const val ALIAS = "jarvis_secure_key"
    private const val PREFIX = "enc:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            plain
        }
    }

    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val ct = raw.copyOfRange(12, raw.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun deleteKey() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.deleteEntry(ALIAS)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }
}

class PinStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_pin", Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.getString("hash", null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Secure.hashPin(pin, salt))
            .putInt("fails", 0)
            .putLong("until", 0L)
            .apply()
    }

    fun remainingLockSeconds(): Long {
        val left = prefs.getLong("until", 0L) - System.currentTimeMillis()
        return if (left > 0) (left + 999) / 1000 else 0L
    }

    fun check(pin: String): Boolean {
        if (remainingLockSeconds() > 0) return false
        val saltS = prefs.getString("salt", null) ?: return false
        val hash = prefs.getString("hash", null) ?: return false
        val ok = try {
            Secure.hashPin(pin, Base64.decode(saltS, Base64.NO_WRAP)) == hash
        } catch (e: Exception) {
            false
        }
        val ed = prefs.edit()
        if (ok) {
            ed.putInt("fails", 0).putLong("until", 0L)
        } else {
            val f = prefs.getInt("fails", 0) + 1
            ed.putInt("fails", f)
            if (f % 5 == 0) ed.putLong("until", System.currentTimeMillis() + 30_000L * (f / 5))
        }
        ed.apply()
        return ok
    }

    fun remove() {
        prefs.edit().clear().apply()
    }
}
