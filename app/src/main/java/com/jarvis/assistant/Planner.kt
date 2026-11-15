package com.jarvis.assistant

import android.app.NotificationManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Planner(context: Context) {

    private val appContext = context.applicationContext
    private val reminders = ReminderStore(appContext)
    private val tasks = TaskStore(appContext)

    init {
        ReminderScheduler.ensureChannel(appContext)
        ReminderScheduler.rescheduleAll(appContext)
    }

    private val listWords = setOf(
        "dikhao", "dikha", "batao", "bata", "list", "show", "upcoming", "kitne", "padho", "sunao"
    )

    private val stop = setOf(
        "reminder", "reminders", "remind", "me", "mujhe", "yaad", "dilao", "dilana", "dila", "dilado",
        "lagao", "laga", "lagana", "lagado", "set", "karo", "kar", "do", "de", "dena", "ka", "ki", "ke",
        "ko", "ek", "jarvis", "hey", "please", "plz", "baje", "baad", "mein", "at", "to", "for", "add",
        "bana", "banao", "mera", "meri", "mere", "o'clock", "oclock", "am", "pm", "in", "tak"
    )

    private val delAll = Regex(
        "(?:sab|saare|saari|all)\\s+reminders?\\s+(?:delete|hatao|cancel|clear|mita|saaf)"
    )
    private val delOne1 = Regex("reminder\\s+(\\d+)\\s+(?:ko\\s+)?(?:delete|hatao|cancel|remove|mita)")
    private val delOne2 = Regex("(?:delete|hatao|cancel|remove|mita)\\s+reminder\\s+(\\d+)")
    private val editRe = Regex("reminder\\s+(\\d+)")

    private val addTask = listOf(
        Regex(
            "^(?:ek\\s+)?(?:naya\\s+)?(?:task|todo|to-do)\\s+(?:add|jodo|likho|daalo|bana|banao)(?:\\s+karo|\\s+do)?\\s*[:,\\-]?\\s*(.+)$",
            RegexOption.IGNORE_CASE
        ),
        Regex("^(?:add|jodo|daalo)\\s+(?:a\\s+|ek\\s+)?(?:task|todo)\\s*[:,\\-]?\\s*(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:task|todo)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    )
    private val doneRe = Regex(
        "task\\s*(\\d+)\\s*(?:ko\\s+)?(?:done|ho\\s+gaya|ho\\s+gayi|complete|completed|khatam|pura|finished)"
    )
    private val delTaskRe = Regex(
        "task\\s+(\\d+)\\s+(?:ko\\s+)?(?:delete|hatao|remove|mita)|(?:delete|hatao|remove|mita)\\s+task\\s+(\\d+)"
    )

    private fun tokens(s: String): List<String> =
        s.split(Regex("[^\\p{L}\\p{M}\\p{N}']+")).filter { it.isNotEmpty() }

    fun handle(low: String, original: String): String? {
        return deleteReminder(low)
            ?: editReminder(low)
            ?: taskCommand(low, original)
            ?: showReminders(low)
            ?: scheduleReply(low)
            ?: createReminder(low)
    }

    // ---------- helpers ----------

    private fun dayStart(offset: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, offset)
        return c.timeInMillis
    }

    private fun describe(ms: Long): String {
        val time = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(ms))
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val diff = ((c.timeInMillis - dayStart(0)) / 86_400_000L).toInt()
        val day = if (diff == 0) {
            "aaj"
        } else if (diff == 1) {
            "kal"
        } else if (diff == 2) {
            "parso"
        } else {
            SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(ms))
        }
        return "$day $time"
    }

    private fun cleanText(rest: String): String {
        val words = tokens(rest).filter { it !in stop }
        val text = words.joinToString(" ").trim()
        if (text.isEmpty()) return "Yaad dilana"
        return text.replaceFirstChar { it.uppercase() }.take(120)
    }

    // ---------- reminders ----------

    private fun createReminder(low: String): String? {
        val tk = tokens(low)
        val wants = "reminder" in tk || "remind" in tk || ("yaad" in tk && tk.any { it.startsWith("dila") })
        if (!wants) return null

        val p = TimeParser.parse(low)
            ?: return "Kab yaad dilana hai? Jaise: kal subah 6 baje maths padhne ka reminder lagao, ya 10 minute baad paani peena yaad dilao."
        if (p.millis <= System.currentTimeMillis()) return "Yeh samay nikal chuka hai. Aage ka samay bolo."

        val text = cleanText(p.rest)
        val r = Reminder(System.currentTimeMillis(), text, p.millis, p.daily)
        reminders.add(r)
        ReminderScheduler.schedule(appContext, r)

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val warn = if (nm.areNotificationsEnabled()) "" else " Dhyan do: JARVIS ke notifications band hain, Settings mein on karo."
        return "Reminder laga diya: $text, ${describe(p.millis)}" + (if (p.daily) " (roz)" else "") + ". Galat ho toh bolo: reminder number ka time badlo." + warn
    }

    private fun showReminders(low: String): String? {
        val tk = tokens(low)
        if (("reminders" !in tk && "reminder" !in tk) || tk.size > 7) return null
        if (tk.none { it in listWords }) return null
        if (TimeParser.parse(low) != null) return null
        val list = reminders.all()
        if (list.isEmpty()) return "Abhi koi reminder nahi hai."
        return "Aapke reminders:\n" + list.mapIndexed { i, r ->
            "${i + 1}. ${r.text}, ${describe(r.timeMillis)}" + (if (r.daily) " (roz)" else "")
        }.joinToString("\n")
    }

    private fun deleteReminder(low: String): String? {
        if (delAll.containsMatchIn(low)) {
            reminders.all().forEach { ReminderScheduler.cancel(appContext, it.id) }
            reminders.clear()
            return "Saare reminders hata diye."
        }
        val m = delOne1.find(low) ?: delOne2.find(low) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val list = reminders.all()
        if (n < 1 || n > list.size) return "Is number ka koi reminder nahi hai."
        val r = list[n - 1]
        ReminderScheduler.cancel(appContext, r.id)
        reminders.remove(r.id)
        return "Reminder hata diya: ${r.text}"
    }

    private fun editReminder(low: String): String? {
        val tk = tokens(low)
        if (tk.none { it in setOf("time", "samay", "badlo", "change", "edit", "shift", "reschedule") }) return null
        val m = editRe.find(low) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val list = reminders.all()
        if (n < 1 || n > list.size) return "Is number ka koi reminder nahi hai."
        val p = TimeParser.parse(low.replace(m.value, " "))
            ?: return "Naya samay bolo. Jaise: reminder $n ka time kal subah 7 baje karo"
        if (p.millis <= System.currentTimeMillis()) return "Yeh samay nikal chuka hai."
        val old = list[n - 1]
        ReminderScheduler.cancel(appContext, old.id)
        val updated = old.copy(timeMillis = p.millis, daily = old.daily || p.daily)
        reminders.update(updated)
        ReminderScheduler.schedule(appContext, updated)
        return "Reminder $n ka samay badal diya: ${updated.text}, ${describe(updated.timeMillis)}"
    }

    private fun scheduleReply(low: String): String? {
        val tk = tokens(low)
        if ("schedule" !in tk && "agenda" !in tk) return null
        if (tk.none { it in setOf("aaj", "kal", "mera", "meri", "mere", "today", "tomorrow", "my") }) return null

        val offset = if ("kal" in tk || "tomorrow" in tk) 1 else 0
        val start = dayStart(offset)
        val end = start + 86_400_000L
        val label = if (offset == 0) "Aaj" else "Kal"
        val list = reminders.all().filter { it.timeMillis in start until end }
        val pending = tasks.all().filter { !it.done }

        val sb = StringBuilder()
        if (list.isEmpty()) {
            sb.append("$label koi reminder nahi hai.")
        } else {
            sb.append("$label ke reminders:\n")
            list.forEachIndexed { i, r ->
                val t = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(r.timeMillis))
                sb.append("${i + 1}. $t, ${r.text}\n")
            }
        }
        if (pending.isNotEmpty()) {
            sb.append("Baaki tasks: ${pending.size}. ")
            sb.append(pending.take(3).joinToString(", ") { it.text })
        }
        return sb.toString().trim()
    }

    // ---------- tasks ----------

    private fun taskCommand(low: String, original: String): String? {
        val t = original.trim()
        for (re in addTask) {
            val m = re.find(t)
            if (m != null) {
                val text = m.groupValues[1].trim()
                if (text.isEmpty()) return "Task kya hai? Jaise: task add karo maths homework"
                tasks.add(Task(System.currentTimeMillis(), text.take(120), false))
                val left = tasks.all().count { !it.done }
                return "Task jod diya: $text. Ab $left tasks baaki hain."
            }
        }

        val dm = doneRe.find(low)
        if (dm != null) {
            val n = dm.groupValues[1].toIntOrNull() ?: return null
            val list = tasks.all()
            if (n < 1 || n > list.size) return "Is number ka koi task nahi hai."
            tasks.setDone(list[n - 1].id)
            return "Shabaash! Task ho gaya: ${list[n - 1].text}"
        }

        val xm = delTaskRe.find(low)
        if (xm != null) {
            val n = (xm.groupValues[1].ifEmpty { xm.groupValues[2] }).toIntOrNull() ?: return null
            val list = tasks.all()
            if (n < 1 || n > list.size) return "Is number ka koi task nahi hai."
            tasks.remove(list[n - 1].id)
            return "Task hata diya: ${list[n - 1].text}"
        }

        val tk = tokens(low)
        if (("tasks" in tk || "task" in tk || "todo" in tk) && tk.size <= 6 && tk.any { it in listWords }) {
            val list = tasks.all()
            if (list.isEmpty()) return "Abhi koi task nahi hai."
            val left = list.count { !it.done }
            return "Aapke tasks ($left baaki):\n" + list.mapIndexed { i, x ->
                "${i + 1}. ${x.text}" + (if (x.done) " (ho gaya)" else " (baaki)")
            }.joinToString("\n")
        }
        return null
    }
}
