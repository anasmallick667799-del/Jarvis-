package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ChatStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_chat", Context.MODE_PRIVATE)

    fun load(): List<Pair<String, String>> {
        val raw = prefs.getString("chat", "[]") ?: "[]"
        val out = mutableListOf<Pair<String, String>>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getString("w") to o.getString("t"))
            }
        } catch (e: Exception) {
            // kharab data ho toh khaali chat se shuru
        }
        return out
    }

    fun save(list: List<Pair<String, String>>) {
        val arr = JSONArray()
        for ((who, text) in list.takeLast(60)) {
            arr.put(JSONObject().put("w", who).put("t", text))
        }
        prefs.edit().putString("chat", arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().putString("chat", "[]").apply()
    }
}
