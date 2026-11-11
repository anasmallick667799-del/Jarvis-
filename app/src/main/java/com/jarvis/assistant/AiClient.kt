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
    private val history = mutableListOf<Pair<String, String>>()

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

    fun ask(text: String, callback: (String) -> Unit) {
        val msgs = JSONArray()
        for ((role, t) in history) {
            msgs.put(JSONObject().put("role", role).put("text", t))
        }
        msgs.put(JSONObject().put("role", "user").put("text", text))
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
                error = "Internet nahi hai ya Worker tak pahunch nahi paaya."
            }
            val finalReply = reply
            val finalError = error
            main.post {
                if (finalReply != null) {
                    history.add("user" to text)
                    history.add("model" to finalReply)
                    while (history.size > 12) history.removeAt(0)
                    callback(finalReply)
                } else {
                    callback("AI se jawab nahi mila: " + (finalError ?: "pata nahi kyun"))
                }
            }
        }.start()
    }
}
