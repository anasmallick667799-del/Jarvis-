package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import java.util.Locale

class PhoneActions(private val context: Context) {

    fun handle(original: String): String? {
        val text = original.trim().lowercase(Locale.ROOT)
        val tk = text.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        return alarmReply(text, tk)
            ?: timerReply(text, tk)
            ?: torchReply(tk)
            ?: volumeReply(tk)
            ?: whatsappReply(original, tk)
            ?: callReply(text, tk)
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

    // ---------- alarm ----------

    private fun alarmReply(text: String, tk: List<String>): String? {
        if ("alarm" !in tk && "alaram" !in tk) return null

        val removeWords = setOf("hatao", "delete", "cancel", "band", "remove", "off")
        if (tk.any { it in removeWords }) {
            return if (start(Intent(AlarmClock.ACTION_SHOW_ALARMS))) {
                "Alarm hatane ke liye Clock app khol raha hoon, wahan se band kar do."
            } else {
                "Clock app nahi khul paaya."
            }
        }

        val m1 = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})").find(text)
        val m2 = Regex("(\\d{1,2})\\s*(?:baje|am|pm)").find(text)
        val m3 = Regex("\\b(\\d{1,2})\\b").find(text)
        val hm: Pair<Int, Int> = when {
            m1 != null -> Pair(m1.groupValues[1].toInt(), m1.groupValues[2].toInt())
            m2 != null -> Pair(m2.groupValues[1].toInt(), 0)
            m3 != null -> Pair(m3.groupValues[1].toInt(), 0)
            else -> return "Kitne baje ka alarm? Jaise: subah 6 baje alarm laga do"
        }
        var hour = hm.first
        val minute = hm.second

        val pmWords = setOf("pm", "shaam", "raat", "dopahar", "evening", "night", "afternoon")
        val amWords = setOf("am", "subah", "morning", "savere")
        if (tk.any { it in pmWords } && hour in 1..11) hour += 12
        if (tk.any { it in amWords } && hour == 12) hour = 0
        if ("raat" in tk && hour == 12) hour = 0

        if (hour > 23 || minute > 59) {
            return "Yeh samay sahi nahi lagta. Jaise: subah 6 baje ya shaam 7:30 baje alarm laga do"
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val label = String.format(Locale.US, "%02d:%02d", hour, minute)
        return if (start(intent)) {
            "Alarm $label ke liye laga diya. Clock app mein ek baar check kar lena."
        } else {
            "Alarm set nahi ho paaya. Clock app check karo."
        }
    }

    // ---------- timer ----------

    private fun timerReply(text: String, tk: List<String>): String? {
        if ("timer" !in tk) return null

        var secs = 0
        Regex("(\\d+)\\s*(ghante|ghanta|hour|hours|hr|minute|minutes|min|mins|second|seconds|sec|secs)")
            .findAll(text)
            .forEach {
                val n = it.groupValues[1].toIntOrNull() ?: 0
                secs += when (it.groupValues[2]) {
                    "ghante", "ghanta", "hour", "hours", "hr" -> n * 3600
                    "minute", "minutes", "min", "mins" -> n * 60
                    else -> n
                }
            }
        if (secs <= 0) return "Kitne der ka timer? Jaise: 10 minute ka timer"
        if (secs > 86400) return "Timer 24 ghante se zyada ka nahi laga sakta."

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val h = secs / 3600
        val m = (secs % 3600) / 60
        val s = secs % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts.add("$h ghanta")
        if (m > 0) parts.add("$m minute")
        if (s > 0) parts.add("$s second")
        val label = parts.joinToString(" ")
        return if (start(intent)) {
            "$label ka timer shuru kar diya."
        } else {
            "Timer set nahi ho paaya. Clock app check karo."
        }
    }

    // ---------- torch ----------

    private fun torchReply(tk: List<String>): String? {
        if (tk.none { it in setOf("torch", "flashlight", "flash", "tarch") }) return null
        val off = tk.any { it in setOf("band", "bandh", "off", "bujha", "bujhao", "stop") }
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (id == null) {
                "Is phone mein flashlight nahi mili."
            } else {
                cm.setTorchMode(id, !off)
                if (off) "Torch band kar di." else "Torch chalu kar di."
            }
        } catch (e: Exception) {
            "Torch control nahi ho paaya."
        }
    }

    // ---------- volume ----------

    private fun volumeReply(tk: List<String>): String? {
        if ("volume" !in tk) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val up = tk.any { it in setOf("badhao", "badha", "badhana", "up", "increase", "zyada", "tez") }
        val down = tk.any { it in setOf("ghatao", "ghata", "ghatana", "down", "decrease", "kam", "dheema") }
        val max = tk.any { it in setOf("max", "full", "maximum") }
        val mute = tk.any { it in setOf("mute", "band") }

        return when {
            max -> {
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    AudioManager.FLAG_SHOW_UI
                )
                "Volume full kar diya."
            }
            mute -> {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Volume mute kar diya."
            }
            up -> {
                repeat(3) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                "Volume badha diya."
            }
            down -> {
                repeat(3) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                "Volume ghata diya."
            }
            else -> "Volume badhao, ghatao, mute ya max bolo."
        }
    }

    // ---------- whatsapp message ----------

    private fun whatsappReply(original: String, tk: List<String>): String? {
        if ("whatsapp" !in tk) return null
        if (tk.none { it in setOf("message", "msg", "bhejo", "send", "likho") }) return null

        val nm = Regex("(\\+?\\d[\\d\\s-]{8,}\\d)").find(original)
            ?: return "Number ke saath bolo. Jaise: whatsapp message 9876543210 hello kaise ho"

        var digits = nm.value.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        if (digits.length < 10) return "Number poora nahi lagta. 10 akshar ka number bolo."

        val body = original.substring(nm.range.last + 1).trim()
            .replace(Regex("\\s*(bhejo|bhej do|bhej dena|send karo|send)$", RegexOption.IGNORE_CASE), "")
            .trim()

        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(body))
        return if (start(Intent(Intent.ACTION_VIEW, uri))) {
            "WhatsApp chat khol raha hoon. Message likha hua hai, Send aapko khud dabana hoga."
        } else {
            "WhatsApp nahi khul paaya."
        }
    }

    // ---------- call ----------

    private fun callReply(text: String, tk: List<String>): String? {
        if ("call" !in tk && "dial" !in tk) return null

        val nm = Regex("(\\+?\\d[\\d\\s-]{4,}\\d)").find(text)
        if (nm == null) {
            val verbs = setOf("karo", "lagao", "laga", "kar", "do")
            return if (tk.any { it in verbs }) {
                "Naam se call lagane ke liye contacts ki permission chahiye, wo abhi nahi hai. Number ke saath bolo: call 9876543210"
            } else {
                null
            }
        }

        val number = nm.value.filter { it.isDigit() || it == '+' }
        if (number.count { it.isDigit() } < 5) return null

        return if (start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))) {
            "Dialer khol raha hoon: $number. Call lagane ke liye green button aapko dabana hoga."
        } else {
            "Dialer nahi khul paaya."
        }
    }
}
