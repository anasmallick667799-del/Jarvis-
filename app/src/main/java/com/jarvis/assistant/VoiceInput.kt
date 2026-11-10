package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInput(
    context: Context,
    private val partialCb: (String) -> Unit,
    private val finalCb: (String) -> Unit,
    private val errorCb: (String) -> Unit,
    private val listeningCb: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listeningCb(true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            listeningCb(false)
            errorCb(errorText(error))
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                finalCb(text)
            } else {
                errorCb("Kuch samajh nahi aaya. Dobara bolo.")
            }
            listeningCb(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) partialCb(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start(lang: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            errorCb("Is phone mein speech recognition service nahi mili. Google app ya Speech Services install/update karo.")
            return
        }
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "hi") "hi-IN" else "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer = r
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.cancel()
        listeningCb(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorText(code: Int): String = when (code) {
        1, 2 -> "Internet ki problem hai. Net check karo ya offline language pack download karo."
        3 -> "Mic se awaaz nahi aa rahi. Mic check karo."
        4 -> "Speech service mein problem aayi."
        5 -> "Sunne mein problem aayi. Dobara try karo."
        6 -> "Kuch bola nahi gaya. MIC dabake dobara bolo."
        7 -> "Kuch samajh nahi aaya. Dobara bolo."
        8 -> "Speech service busy hai. Thodi der baad try karo."
        9 -> "Mic permission nahi hai."
        12, 13 -> "Yeh language abhi available nahi. Google app mein language pack download karo."
        else -> "Sunne mein problem aayi (code $code)."
    }
}
