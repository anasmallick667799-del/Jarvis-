package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings

class SystemControl(private val context: Context) {

    private class Target(
        val label: String,
        val action: String,
        val note: String,
        val match: (String, List<String>) -> Boolean
    )

    private val toggleNote =
        "Android 12 mein app isse seedha on ya off nahi kar sakti, isliye settings khol raha hoon, wahan se aap kar do."

    private val actionWords = setOf(
        "kholo", "khol", "open", "settings", "setting", "on", "off", "chalu", "band", "bandh",
        "enable", "disable", "karo", "kar", "do", "show", "dikhao", "start", "laga"
    )

    private val photoWords = setOf("kheencho", "khicho", "kheench", "click", "take", "lo", "selfie")

    private val targets = listOf(
        Target("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, toggleNote) { _, tk -> "wifi" in tk },
        Target("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, toggleNote) { _, tk -> "bluetooth" in tk },
        Target("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, toggleNote) { n, tk ->
            "airplane" in tk || n.contains("flight mode")
        },
        Target("Mobile data aur network", Settings.ACTION_WIRELESS_SETTINGS, toggleNote) { n, tk ->
            n.contains("mobile data") || n.contains("internet settings") || ("data" in tk && "mobile" in tk)
        },
        Target("Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, "") { _, tk ->
            "saver" in tk && "battery" in tk
        },
        Target("Sound (Do Not Disturb bhi isi mein milega)", Settings.ACTION_SOUND_SETTINGS, "") { n, tk ->
            n.contains("do not disturb") || "dnd" in tk || "silent" in tk
        },
        Target("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "") { _, tk ->
            "location" in tk || "gps" in tk
        },
        Target("Display", Settings.ACTION_DISPLAY_SETTINGS, "") { _, tk -> "display" in tk },
        Target("NFC", Settings.ACTION_NFC_SETTINGS, "") { _, tk -> "nfc" in tk },
        Target("Phone", Settings.ACTION_SETTINGS, "") { _, tk -> "settings" in tk || "setting" in tk }
    )

    fun handle(low: String): String? {
        val n = low.replace("-", "").replace("wi fi", "wifi")
        val tk = n.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tk.isEmpty() || tk.size > 8) return null
        return volumePercent(n, tk)
            ?: brightness(n, tk)
            ?: camera(tk)
            ?: settingsPage(n, tk)
    }

    private fun start(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun volumePercent(n: String, tk: List<String>): String? {
        if ("volume" !in tk) return null
        val m = Regex("(\\d{1,3})").find(n) ?: return null
        val pct = m.groupValues[1].toInt()
        if (pct > 100) return "Volume 0 se 100 percent ke beech bolo."
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val v = Math.round(max * pct / 100.0).toInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, AudioManager.FLAG_SHOW_UI)
            "Volume $pct percent kar diya."
        } catch (e: Exception) {
            "Volume badal nahi paaya."
        }
    }

    private fun brightness(n: String, tk: List<String>): String? {
        if ("brightness" !in tk && "roshni" !in tk) return null
        if (!Settings.System.canWrite(context)) {
            start(
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
            )
            return "Brightness badalne ke liye JARVIS ko 'Modify system settings' ki permission chahiye. Settings khol raha hoon, JARVIS ko allow karo aur wapas aakar dobara bolo."
        }
        return try {
            val cr = context.contentResolver
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val num = Regex("(\\d{1,3})").find(n)
            val up = tk.any { it in setOf("badhao", "badha", "badhana", "increase", "up", "zyada", "tez") }
            val down = tk.any { it in setOf("ghatao", "ghata", "ghatana", "decrease", "down", "kam", "dim") }
            val max = tk.any { it in setOf("max", "full", "maximum") }
            val min = tk.any { it in setOf("min", "minimum", "lowest") }
            val target = when {
                num != null -> num.groupValues[1].toInt().coerceIn(1, 100) * 255 / 100
                max -> 255
                min -> 10
                up -> current + 50
                down -> current - 50
                else -> return "Brightness badhao, ghatao, max, min ya 50 percent bolo."
            }
            val value = target.coerceIn(10, 255)
            Settings.System.putInt(
                cr,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value)
            "Brightness ${value * 100 / 255} percent kar di."
        } catch (e: Exception) {
            "Brightness badal nahi paaya."
        }
    }

    private fun camera(tk: List<String>): String? {
        val selfie = "selfie" in tk
        val photo = "photo" in tk
        if ("camera" !in tk && !selfie && !photo) return null
        if (tk.none { it in actionWords || it in photoWords }) return null
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (selfie) intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
        return if (start(intent)) "Camera khol raha hoon." else null
    }

    private fun settingsPage(n: String, tk: List<String>): String? {
        if (tk.none { it in actionWords }) return null
        val t = targets.firstOrNull { it.match(n, tk) } ?: return null
        return if (start(Intent(t.action))) {
            ("${t.label} settings khol raha hoon. " + t.note).trim()
        } else {
            "${t.label} settings nahi khul paayi."
        }
    }
}
