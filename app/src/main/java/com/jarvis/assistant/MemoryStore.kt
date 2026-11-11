package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import java.util.Locale

class MemoryStore(context: Context) {

    class Result(val reply: String, val pendingFact: String? = null)

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    private val rememberRegex = Regex(
        "^(?:hey\\s+)?(?:jarvis[,\\s]+)?(?:yaad\\s+rakho|yaad\\s+rakhna|yaad\\s+kar\\s+lo|remember(?:\\s+that)?)\\b\\s*[:,\\-]?\\s*(?:ki\\s+|that\\s+)?(.+)$",
        RegexOption.IGNORE_CASE
    )
    private val forgetOneRegex =
        Regex("^(?:bhool\\s+jao|forget|delete\\s+memory|memory\\s+delete)\\s+(\\d+)$")
    private val showPhrases = listOf(
        "kya yaad hai", "tumhe kya yaad", "what do you remember",
        "meri memory", "memory dikhao", "memory padho", "show memory"
    )
    private val clearPhrases = listOf(
        "sab bhool jao", "forget everything", "memory clear", "memory saaf", "saari yaadein mita"
    )

    fun all(): List<String> {
        val raw = prefs.getString("facts", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(fact: String): Boolean {
        val list = all().toMutableList()
        val f = fact.trim().take(200)
        if (f.isEmpty() || list.size >= 30 || list.any { it.equals(f, ignoreCase = true) }) {
            return false
        }
        list.add(f)
        save(list)
        return true
    }

    fun clear() {
        save(emptyList())
    }

    private fun removeAt(n: Int): String? {
        val list = all().toMutableList()
        if (n < 1 || n > list.size) return null
        val removed = list.removeAt(n - 1)
        save(list)
        return removed
    }

    private fun save(list: List<String>) {
        prefs.edit().putString("facts", JSONArray(list).toString()).apply()
    }

    fun isSensitive(t: String): Boolean {
        val low = t.lowercase(Locale.ROOT)
        if (Regex("\\d{6,}").containsMatchIn(low)) return true
        val words = low.split(Regex("[^a-z0-9]+"))
        val blocked = setOf("password", "passwd", "otp", "cvv", "pin", "aadhaar", "aadhar", "upi", "atm")
        return words.any { it in blocked }
    }

    fun handle(input: String): Result? {
        val text = input.trim()
        val low = text.lowercase(Locale.ROOT)

        if (clearPhrases.any { low.contains(it) }) {
            clear()
            return Result("Maine saari yaadein mita di.")
        }

        val fm = forgetOneRegex.find(low)
        if (fm != null) {
            val removed = removeAt(fm.groupValues[1].toIntOrNull() ?: 0)
            return if (removed != null) {
                Result("Bhool gaya: $removed")
            } else {
                Result("Is number ki koi yaad nahi hai.")
            }
        }

        if (showPhrases.any { low.contains(it) }) {
            val list = all()
            if (list.isEmpty()) return Result("Abhi mujhe aapke baare mein kuch yaad nahi hai.")
            return Result(
                "Mujhe yeh yaad hai:\n" +
                    list.mapIndexed { i, f -> "${i + 1}. $f" }.joinToString("\n")
            )
        }

        val sm = rememberRegex.find(text)
        if (sm != null) {
            val fact = sm.groupValues[1].trim()
            if (isSensitive(fact)) {
                return Result("Yeh private lagta hai (password, OTP ya lamba number), isliye main ise yaad nahi rakhunga.")
            }
            return Result(
                "Kya main yeh yaad rakhun: \"$fact\"? Dialog mein YES ya NO dabao.",
                fact.take(200)
            )
        }

        return null
    }
}
