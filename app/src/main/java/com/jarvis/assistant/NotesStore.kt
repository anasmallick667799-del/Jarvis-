package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray

class NotesStore(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)

    fun all(): List<String> {
        val raw = prefs.getString("notes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(note: String) {
        val list = all().toMutableList()
        list.add(note)
        save(list)
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<String>) {
        prefs.edit().putString("notes", JSONArray(list).toString()).apply()
    }
}
