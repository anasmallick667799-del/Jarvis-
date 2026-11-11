package com.jarvis.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiClient(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_ai", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val history: MutableList<Pair<String, String>> = loadHistory()

    var url: String = prefs.getString("url", "") ?: ""
        private set
    var token: String = prefs.getString("token", "") ?: ""
        private set

    fun isConfigured(): Boolean = url.isNotBlank() && token.isNotBlank()

    fun save(newUrl: String, newToken: String) {
        url = newUrl.trim().trimEnd('/')
        token = newToken.trim()
        prefs.edit().putString("url", url).putString("token", token).apply()
    }

    private fun loadHistory(): MutableList<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val arr = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(o.getString("r") to o.getString("t"))
            }
        } catch (e: Exception) {
            // kharab data ho toh khaali history
        }
        return out
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for ((r, t) in history) {
            arr.put(JSONObject().put("r", r).put("t", t))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    private fun post(msgs: JSONArray, done: (String?, String?) -> Unit) {
        val body = JSONObject().put("messages", msgs).toString()
        val endpoint = url
        val tok = token

        Thread {
            var reply: String? = null
            var error: String? = null
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 40000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("x-app-token", tok)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code == 401) {
                    error = "Token galat hai. AI SETUP mein token check karo."
                } else {
                    val obj = try {
                        JSONObject(raw)
                    } catch (e: Exception) {
                        null
                    }
                    val r = obj?.optString("reply", "") ?: ""
                    if (r.isNotBlank()) {
                        reply = r
                    } else {
                        val e = obj?.optString("error", "") ?: ""
                        error = if (e.isNotBlank()) e else "AI se jawab nahi mila (code $code)."
                    }
                }
            } catch (e: Exception) {
                error = "Connection error: " + e.javaClass.simpleName + " " + (e.message ?: "")
            }
            val finalReply = reply
            val finalError = error
            main.post { done(finalReply, finalError) }
        }.start()
    }

    fun ask(text: String, facts: List<String>, callback: (String, Boolean) -> Unit) {
        val sendText = if (facts.isEmpty()) {
            text
        } else {
            val factText = facts.joinToString("\n") { "- $it" }.take(1200)
            text + "\n\nUser ke baare mein saved baatein (sirf zaroorat padne par use karo, bina poochhe unka zikr mat karo):\n" + factText
        }

        val msgs = JSONArray()
        for ((role, t) in history) {
            msgs.put(JSONObject().put("role", role).put("text", t))
        }
        msgs.put(JSONObject().put("role", "user").put("text", sendText))

        post(msgs) { r, e ->
            if (r != null) {
                history.add("user" to text)
                history.add("model" to r)
                while (history.size > 12) history.removeAt(0)
                saveHistory()
                callback(r, true)
            } else {
                callback("AI se jawab nahi mila: " + (e ?: "pata nahi kyun"), false)
            }
        }
    }

    fun suggestFact(userText: String, callback: (String?) -> Unit) {
        val prompt = "Neeche user ka message hai. Agar usme user ke baare mein koi lambe samay tak kaam aane wali personal baat hai " +
            "(jaise naam, pasand ya napasand, kaam ya padhai, shauk, routine), toh use ek chhoti line mein likho, jaise 'User ka naam Rahul hai'. " +
            "Agar aisi koi baat nahi hai, ya baat private ya sensitive hai (password, OTP, bank, health, address), ya sirf ek-baar ka sawaal hai, " +
            "toh sirf NONE likho. Aur kuch bhi mat likho.\n\nUser ka message: \"" + userText.take(300) + "\""
        val msgs = JSONArray().put(JSONObject().put("role", "user").put("text", prompt))
        post(msgs) { r, _ -> callback(parseFact(r)) }
    }

    private fun parseFact(r: String?): String? {
        if (r == null) return null
        val t = r.trim().trim('"', '\'', '.', ' ').trim()
        if (t.isEmpty() || t.length > 120 || t.contains('\n')) return null
        if (t.contains("NONE", ignoreCase = true)) return null
        return t
    }
}
