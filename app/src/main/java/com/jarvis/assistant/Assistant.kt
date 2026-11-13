package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Locale

private const val UNKNOWN_PREFIX = "Yeh command abhi samajh nahi aaya"

private val PERSONAL_WORDS = setOf(
    "mera", "meri", "mere", "mujhe", "main", "mai", "my", "i", "i'm", "hum", "humare", "hamara"
)

private fun looksPersonal(t: String): Boolean {
    val words = t.lowercase().split(Regex("[^a-z']+"))
    return words.any { it in PERSONAL_WORDS }
}

interface AssistantHost {
    fun say(text: String)
    fun setBusy()
    fun suggestMemory(fact: String, fromAi: Boolean)
    fun askContactsPermission(name: String)
    fun clearChat()
}

class Assistant(context: Context, private val host: AssistantHost) {

    private val appContext = context.applicationContext
    private val engine = CommandEngine(appContext)
    private val weather = Weather(appContext)
    private val contacts = ContactsHelper(appContext)

    val ai = AiClient(appContext)
    val memory = MemoryStore(appContext)
    val settings = AppSettings(appContext)

    private var lastAnswer = ""

    private fun answer(text: String) {
        lastAnswer = text
        host.say(text)
    }

    fun dialContact(name: String) {
        answer(contacts.dial(name))
    }

    private fun hasAny(low: String, vararg keys: String): Boolean = keys.any { low.contains(it) }

    private fun followUpInstruction(low: String): String? {
        if (low.length > 40) return null
        if (hasAny(low, "dobara samjhao", "phir se samjhao", "explain again", "simple mein samjhao",
                "aasan bhasha mein samjhao", "samajh nahi aaya", "samjha nahi")) {
            return "Apna pichhla jawab aur saral aur aasan bhasha mein, ek example ke saath, dobara samjhao."
        }
        if (hasAny(low, "chhota karke", "short mein", "in short", "sankshep mein")) {
            return "Apna pichhla jawab sirf 1 ya 2 chhote vakya mein batao."
        }
        if (hasAny(low, "detail mein", "aur detail", "tell me more", "explain more")) {
            return "Apne pichhle jawab ke baare mein thoda aur detail mein batao."
        }
        return null
    }

    private fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    private fun askAi(question: String, suggest: Boolean) {
        if (!ai.isConfigured()) {
            answer("AI abhi set nahi hai. Upar AI SETUP dabake Worker URL aur token daalo.")
            return
        }
        ai.ask(question, memory.all()) { reply, ok ->
            answer(reply)
            if (ok && suggest && settings.suggestions && question.length in 8..300 && looksPersonal(question)) {
                ai.suggestFact(question) { fact ->
                    if (fact != null &&
                        !memory.isSensitive(fact) &&
                        memory.all().none { it.equals(fact, ignoreCase = true) }
                    ) {
                        host.suggestMemory(fact, true)
                    }
                }
            }
        }
    }

    fun handle(text: String) {
        host.setBusy()
        val t = text.trim()
        val low = t.lowercase(Locale.ROOT)
        val tk = low.split(Regex("[^\\p{L}\\p{M}\\p{N}']+")).filter { it.isNotEmpty() }

        // 1. chat saaf
        if (hasAny(low, "chat saaf", "clear chat", "chat clear", "chat delete")) {
            host.clearChat()
            ai.clearHistory()
            answer("Chat saaf kar di. Yaadein (memory) waise hi hain.")
            return
        }

        // 2. dobara bolo
        if (low == "repeat" || hasAny(low, "dobara bolo", "phir se bolo", "dobara sunao", "repeat that", "say that again")) {
            if (lastAnswer.isNotEmpty()) {
                answer(lastAnswer)
            } else {
                answer("Abhi dobara bolne ke liye kuch nahi hai.")
            }
            return
        }

        // 3. follow-up (samjhao, chhota karo, detail)
        val instruction = followUpInstruction(low)
        if (instruction != null) {
            if (lastAnswer.isEmpty()) {
                answer("Pehle koi sawaal ya command do, phir main dobara samjha sakta hoon.")
            } else {
                askAi("Tumhara pichhla jawab tha: " + lastAnswer.take(600) + "\n\n" + instruction, false)
            }
            return
        }

        // 4. memory
        val mem = memory.handle(t)
        if (mem != null) {
            val pf = mem.pendingFact
            if (pf != null) host.suggestMemory(pf, false)
            answer(mem.reply)
            return
        }

        // 5. schedule/reminder abhi bana nahi
        val personalDay = tk.any { it in setOf("mera", "meri", "mere", "aaj", "kal", "my", "today", "tomorrow") }
        val plannerWord = tk.any { it in setOf("schedule", "reminder", "reminders", "homework", "task", "tasks") } ||
            low.contains("yaad dila")
        if (personalDay && plannerWord) {
            answer("Reminder, task aur schedule ka system abhi bana nahi hai, yeh agle phases mein aayega. Abhi alarm ya timer laga sakta hoon.")
            return
        }

        // 6. mausam
        val setCity = weather.setCityCommand(t)
        if (setCity != null) {
            answer(setCity)
            return
        }
        val wq = weather.parse(t)
        if (wq != null) {
            val city = if (wq.isBlank()) weather.savedCity() else wq
            if (city.isBlank()) {
                answer("Kis shehar ka mausam? Jaise: mausam Delhi. Ya pehle bolo: mera shehar Delhi hai")
            } else {
                weather.fetch(city) { result -> answer(result) }
            }
            return
        }

        // 7. joke
        val joke = Jokes.match(low)
        if (joke != null) {
            answer(joke)
            return
        }

        // 8. naam se call
        val callTarget = contacts.callTarget(t)
        if (callTarget != null) {
            if (hasContactsPermission()) {
                answer(contacts.dial(callTarget))
            } else {
                host.askContactsPermission(callTarget)
                answer("Naam se call ke liye contacts ki permission chahiye. Dialog mein ALLOW dabao.")
            }
            return
        }

        // 9. offline engine, warna AI
        val forceAi = low.startsWith("ai ")
        if (!forceAi) {
            val offline = engine.handle(t)
            if (!offline.startsWith(UNKNOWN_PREFIX)) {
                answer(offline)
                return
            }
        }
        val question = if (forceAi) t.substring(3).trim() else t
        askAi(question, true)
    }
}
