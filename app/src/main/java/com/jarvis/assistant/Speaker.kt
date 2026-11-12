package com.jarvis.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class Speaker(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jarvis_voice", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    var isReady: Boolean = false
        private set
    var enabled: Boolean = prefs.getBoolean("enabled", true)
        private set
    var lang: String = prefs.getString("lang", "en") ?: "en"
        private set
    var rate: Float = prefs.getFloat("rate", 1.0f)
        private set
    var pitch: Float = prefs.getFloat("pitch", 1.0f)
        private set

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            report(true)
        }

        override fun onDone(utteranceId: String?) {
            report(false)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            report(false)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            report(false)
        }
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(listener)
                isReady = true
                applyVoice()
            }
        }
    }

    private fun report(speaking: Boolean) {
        main.post { onSpeakingChanged(speaking) }
    }

    private fun locale(): Locale =
        if (lang == "hi") Locale("hi", "IN") else Locale("en", "IN")

    private fun voicesForLang(): List<Voice> {
        val all: Set<Voice> = try {
            tts?.voices ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        val code = locale().language
        return all
            .filter { it.locale.language == code }
            .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
            .sortedWith(
                compareBy({ it.locale.country != "IN" }, { it.isNetworkConnectionRequired }, { it.name })
            )
    }

    private fun applyVoice() {
        val t = tts ?: return
        val list = voicesForLang()
        val saved = prefs.getString("voice_$lang", null)
        val chosen = list.firstOrNull { it.name == saved } ?: list.firstOrNull()
        if (chosen != null) {
            t.setVoice(chosen)
        } else {
            t.setLanguage(locale())
        }
    }

    fun setVoiceStyle(newRate: Float, newPitch: Float) {
        rate = newRate
        pitch = newPitch
        prefs.edit().putFloat("rate", rate).putFloat("pitch", pitch).apply()
    }

    fun speak(text: String) {
        if (!enabled || !isReady) return
        val clean = text
            .replace("•", ",")
            .replace(" + ", " plus ")
            .replace(" - ", " minus ")
            .replace(" * ", " times ")
            .replace(" / ", " divided by ")
            .replace(" = ", " equals ")
            .replace("\n", ". ")
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    fun stop() {
        tts?.stop()
        report(false)
    }

    fun turnVoice(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
        if (!on) stop()
    }

    fun nextVoice(): String {
        if (!isReady) {
            return "Text-to-speech abhi ready nahi hai. Phone mein TTS engine check karo."
        }
        val list = voicesForLang()
        if (list.isEmpty()) {
            return "Is language ki koi voice nahi mili. Settings mein Text-to-speech se voice data download karo."
        }
        val current = tts?.voice?.name ?: prefs.getString("voice_$lang", null)
        val idx = list.indexOfFirst { it.name == current }
        val next = list[(idx + 1) % list.size]
        prefs.edit().putString("voice_$lang", next.name).apply()
        tts?.setVoice(next)
        return "Voice ${list.indexOf(next) + 1}/${list.size} (${next.name}). Female nahi lagi toh dobara NEXT dabao."
    }

    fun toggleLang(): String {
        lang = if (lang == "en") "hi" else "en"
        prefs.edit().putString("lang", lang).apply()
        if (!isReady) return "Language badal di, par text-to-speech abhi ready nahi hai."
        applyVoice()
        val count = voicesForLang().size
        val name = if (lang == "hi") "Hindi" else "English"
        return if (count == 0) {
            "$name voice nahi mili. Settings mein Text-to-speech se $name voice data download karo."
        } else {
            "Awaaz ki language: $name ($count voices mili). Female voice ke liye NEXT dabao."
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
