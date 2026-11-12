package com.jarvis.assistant

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.os.Handler
import android.os.Looper

class WakeWord(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jarvis_wake", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var manager: PorcupineManager? = null

    var accessKey: String = prefs.getString("key", "") ?: ""
        private set

    fun hasKey(): Boolean = accessKey.isNotBlank()

    fun saveKey(k: String) {
        stop()
        accessKey = k.trim()
        prefs.edit().putString("key", accessKey).apply()
    }

    fun start(onWake: () -> Unit): String? {
        if (manager != null) return null
        if (!hasKey()) return "Picovoice AccessKey nahi hai."
        return try {
            val m = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.7f)
                .build(appContext, PorcupineManagerCallback { _ -> main.post { onWake() } })
            m.start()
            manager = m
            null
        } catch (e: Exception) {
            manager = null
            "Wake word start nahi hua: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    fun stop() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: Exception) {
            // ignore
        }
        manager = null
    }
}
