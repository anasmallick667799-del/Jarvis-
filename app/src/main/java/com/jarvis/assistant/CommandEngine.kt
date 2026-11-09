package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandEngine(private val context: Context) {

    private val notes = NotesStore(context)

    fun handle(input: String): String {
        val original = input.trim()
        val text = original.lowercase(Locale.ROOT)
        if (text.isEmpty()) return "Kuch likho toh sahi."
        return helpReply(text)
            ?: notesReply(original, text)
            ?: openAppReply(text)
            ?: batteryReply(text)
            ?: timeDateReply(text)
            ?: calcReply(text)
            ?: greetingReply(text)
            ?: "Yeh command abhi samajh nahi aaya. 'help' likho toh bata dunga ki main kya kar sakta hoon. (Internet wala AI baad ke phase mein aayega.)"
    }

    // ---------- helpers ----------

    private fun tokens(t: String): List<String> =
        t.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun has(text: String, vararg keys: String): Boolean {
        val tk = tokens(text)
        return keys.any { k -> if (k.contains(" ")) text.contains(k) else k in tk }
    }

    // ---------- help ----------

    private fun helpReply(text: String): String? {
        if (!has(text, "help", "madad", "commands", "kya kar sakte", "मदद")) return null
        return "Main yeh offline kaam kar sakta hoon:\n" +
            "• time / date batao\n" +
            "• calculator: 5 plus 3, 12 * 4, (10+2)*3\n" +
            "• battery kitni hai\n" +
            "• note karo kal doodh lana hai\n" +
            "• mere notes padho / notes delete karo\n" +
            "• whatsapp kholo (koi bhi app)\n" +
            "Online AI baad ke phase mein aayega."
    }

    // ---------- notes ----------

    private val saveRegex = Regex(
        "^(?:hey\\s+)?(?:jarvis[,\\s]+)?(?:note\\s+(?:karo|kar\\s+lo|kar\\s+do|likho|save\\s+karo|save)|yaad\\s+rakho|save\\s+note|add\\s+note|write\\s+note|remember|note)\\b\\s*[:,\\-]?\\s*(.+)$",
        RegexOption.IGNORE_CASE
    )

    private fun notesReply(original: String, text: String): String? {
        if (has(text, "notes") && has(text, "delete", "clear", "hatao", "mita", "mitao", "remove")) {
            notes.clear()
            return "Saare notes delete kar diye."
        }

        val wantsRead = has(text, "notes") ||
            has(text, "note padho", "read note", "note dikhao", "note sunao", "show note", "note batao")
        if (wantsRead) {
            val list = notes.all()
            if (list.isEmpty()) return "Abhi koi note saved nahi hai."
            return "Aapke notes:\n" + list.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")
        }

        val m = saveRegex.find(original) ?: return null
        var body = m.groupValues[1].trim()
        if (body.lowercase(Locale.ROOT) in setOf("karo", "likho", "save", "kar do", "kar lo")) {
            return "Kya note karna hai? Jaise: note karo kal doodh lana hai"
        }
        body = body.replaceFirst(Regex("^(ki|that)\\s+", RegexOption.IGNORE_CASE), "")
        if (body.isBlank()) return "Kya note karna hai?"
        notes.add(body)
        return "Note save kar diya: $body"
    }

    // ---------- open app ----------

    private fun appKey(s: String): String =
        s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    @Suppress("DEPRECATION")
    private fun openAppReply(text: String): String? {
        if (!has(text, "open", "kholo", "khol", "chalao", "chalu", "launch", "start", "खोलो", "खोल")) return null

        val noise = setOf(
            "open", "kholo", "khol", "chalao", "chalu", "launch", "start",
            "karo", "kar", "do", "de", "app", "apps", "application", "ko", "please",
            "jarvis", "hey", "mera", "meri", "mujhe", "ek", "the", "a",
            "खोलो", "खोल"
        )
        val name = tokens(text).filter { it !in noise }.joinToString("")
        if (name.isEmpty()) return "Kaunsa app kholna hai? Jaise: whatsapp kholo"

        return try {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, 0)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }

            val match = apps.firstOrNull { appKey(it.first) == name }
                ?: apps.firstOrNull { appKey(it.first).contains(name) }
                ?: apps.firstOrNull { appKey(it.first).length >= 3 && name.contains(appKey(it.first)) }

            if (match == null) {
                "'$name' naam ka app nahi mila. App ka naam wahi likho jo phone mein dikhta hai."
            } else {
                val launch = pm.getLaunchIntentForPackage(match.second)
                if (launch == null) {
                    "${match.first} khul nahi paaya."
                } else {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    "${match.first} khol raha hoon."
                }
            }
        } catch (e: Exception) {
            "App kholte waqt problem aayi."
        }
    }

    // ---------- battery ----------

    private fun batteryReply(text: String): String? {
        if (!has(text, "battery", "batary", "charge", "charging", "बैटरी")) return null
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (pct <= 0) {
                "Battery ka percentage abhi pata nahi chal paaya."
            } else if (charging) {
                "Battery $pct% hai aur charging ho rahi hai."
            } else {
                "Battery $pct% hai."
            }
        } catch (e: Exception) {
            "Battery ka percentage abhi pata nahi chal paaya."
        }
    }

    // ---------- time and date ----------

    private fun timeDateReply(text: String): String? {
        val wantTime = has(text, "time", "samay", "waqt", "baje", "टाइम", "समय", "बजे")
        val wantDate = has(text, "date", "tarikh", "tareekh", "day", "kaunsa din", "kya din", "aaj ka din", "तारीख", "तारिख")
        if (!wantTime && !wantDate) return null
        val now = Date()
        val t = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now)
        return when {
            wantTime && wantDate -> "Abhi $t baje hain, aur aaj $d hai."
            wantTime -> "Abhi samay hai $t."
            else -> "Aaj $d hai."
        }
    }

    // ---------- calculator ----------

    private val opWords = mapOf(
        "plus" to "+", "jod" to "+", "add" to "+",
        "minus" to "-", "ghata" to "-", "ghatao" to "-",
        "into" to "*", "guna" to "*", "times" to "*", "multiply" to "*", "x" to "*", "×" to "*",
        "divide" to "/", "divided" to "/", "bhag" to "/", "bhaag" to "/", "÷" to "/"
    )

    private fun calcReply(text: String): String? {
        val parts = Regex("\\d+(?:\\.\\d+)?|[a-z]+|[+\\-*/()×÷]").findAll(text).map { it.value }.toList()
        val symbols = setOf("+", "-", "*", "/", "(", ")")
        val out = mutableListOf<String>()
        for (p in parts) {
            when {
                p[0].isDigit() -> out.add(p)
                p in symbols -> out.add(p)
                p in opWords -> out.add(opWords.getValue(p))
            }
        }
        val hasNumber = out.any { it[0].isDigit() }
        val hasOp = out.any { it == "+" || it == "-" || it == "*" || it == "/" }
        if (!hasNumber || !hasOp) return null

        val expr = out.joinToString(" ")
        return try {
            val v = Parser(expr).parse()
            if (v.isNaN() || v.isInfinite()) {
                "Zero se divide nahi ho sakta."
            } else {
                "$expr = ${fmt(v)}"
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fmt(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            v.toLong().toString()
        } else {
            String.format(Locale.US, "%.6f", v).trimEnd('0').trimEnd('.')
        }

    // ---------- greetings ----------

    private fun greetingReply(text: String): String? {
        if (has(text, "kaise ho", "kya haal", "how are you", "kaisa hai")) {
            return "Main bilkul theek hoon. Aap bataiye, main kya kar sakta hoon?"
        }
        if (has(text, "thanks", "thank you", "shukriya", "dhanyavad", "धन्यवाद")) {
            return "Koi baat nahi! Aur kuch chahiye toh bolo."
        }
        if (has(text, "hello", "hi", "hey", "namaste", "namaskar", "salaam", "good morning", "good evening", "नमस्ते")) {
            return "Namaste! Main JARVIS hoon. Bataiye, kya madad karun?"
        }
        return null
    }

    // ---------- expression parser ----------

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            if (peek() != null) throw IllegalArgumentException("extra")
            return v
        }

        private fun peek(): Char? {
            while (pos < s.length && s[pos] == ' ') pos++
            return if (pos < s.length) s[pos] else null
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                val c = peek()
                if (c == '+') {
                    pos++
                    v += term()
                } else if (c == '-') {
                    pos++
                    v -= term()
                } else {
                    break
                }
            }
            return v
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                val c = peek()
                if (c == '*') {
                    pos++
                    v *= factor()
                } else if (c == '/') {
                    pos++
                    v /= factor()
                } else {
                    break
                }
            }
            return v
        }

        private fun factor(): Double {
            val c = peek()
            if (c == '-') {
                pos++
                return -factor()
            }
            if (c == '(') {
                pos++
                val v = expr()
                if (peek() != ')') throw IllegalArgumentException("bracket")
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("number")
            return s.substring(start, pos).toDouble()
        }
    }
}
