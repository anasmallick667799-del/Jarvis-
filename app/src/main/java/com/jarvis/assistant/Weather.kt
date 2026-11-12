package com.jarvis.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class Weather(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_weather", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    private val triggers = setOf("mausam", "weather", "मौसम")
    private val noise = setOf(
        "kaisa", "kaisi", "kaise", "hai", "ka", "ki", "ke", "batao", "bata", "do", "aaj", "abhi",
        "today", "in", "of", "the", "what", "whats", "is", "how", "kya", "me", "mein", "mai",
        "hoga", "kal", "check", "karo", "tell", "show", "now", "current", "temperature", "temp",
        "का", "कैसा", "है", "आज", "बताओ"
    )
    private val cityRegex = Regex(
        "^(?:mera|meri)\\s+(?:shehar|sheher|city)\\s+(?:hai\\s+)?(.+?)(?:\\s+hai)?$",
        RegexOption.IGNORE_CASE
    )

    fun savedCity(): String = prefs.getString("city", "") ?: ""

    fun setCityCommand(text: String): String? {
        val m = cityRegex.find(text.trim()) ?: return null
        val city = m.groupValues[1].trim()
        if (city.isBlank() || city.length > 40) return null
        prefs.edit().putString("city", city).apply()
        return "Theek hai, aapka shehar $city yaad rakh liya. Ab mausam batao bolo."
    }

    fun parse(text: String): String? {
        val tk = text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
            .filter { it.isNotEmpty() }
        if (tk.none { it in triggers }) return null
        if (tk.size > 7) return null
        val city = tk.filter { it !in triggers && it !in noise }
        if (city.size > 2) return null
        return city.joinToString(" ")
    }

    fun fetch(city: String, callback: (String) -> Unit) {
        Thread {
            val reply = lookup(city)
            main.post { callback(reply) }
        }.start()
    }

    private fun lookup(city: String): String {
        val geoRaw = httpGet(
            "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                URLEncoder.encode(city, "UTF-8")
        ) ?: return "Mausam nahi mil paaya. Internet check karo."

        val results = try {
            JSONObject(geoRaw).optJSONArray("results")
        } catch (e: Exception) {
            null
        }
        if (results == null || results.length() == 0) {
            return "'$city' shehar nahi mila. Spelling check karo."
        }
        val g = results.getJSONObject(0)
        val lat = g.optDouble("latitude")
        val lon = g.optDouble("longitude")
        val name = g.optString("name", city)

        val wRaw = httpGet(
            "https://api.open-meteo.com/v1/forecast?current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&latitude=$lat&longitude=$lon"
        ) ?: return "Mausam nahi mil paaya. Thodi der baad try karo."

        return try {
            val cur = JSONObject(wRaw).getJSONObject("current")
            val t = Math.round(cur.getDouble("temperature_2m"))
            val h = Math.round(cur.optDouble("relative_humidity_2m", 0.0))
            val w = Math.round(cur.optDouble("wind_speed_10m", 0.0))
            val d = describe(cur.optInt("weather_code", -1))
            "$name mein abhi $t degree celsius hai, $d. Humidity $h percent aur hawa $w kilometer per hour."
        } catch (e: Exception) {
            "Mausam ka jawab samajh nahi aaya."
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "saaf aasmaan"
        1, 2, 3 -> "thode badal"
        45, 48 -> "kohra"
        in 51..57 -> "halki phuhaar"
        in 61..67 -> "baarish"
        in 71..77 -> "barf"
        in 80..82 -> "baarish ki bauchhar"
        95, 96, 99 -> "aandhi toofan"
        else -> "mausam badalta hua"
    }

    private fun httpGet(u: String): String? {
        return try {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
            conn.disconnect()
            if (code in 200..299) body else null
        } catch (e: Exception) {
            null
        }
    }
}
