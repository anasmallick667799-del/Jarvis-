package com.jarvis.assistant

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(val id: Long, val text: String, val timeMillis: Long, val daily: Boolean)

data class Task(val id: Long, val text: String, val done: Boolean)

class ReminderStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_planner", Context.MODE_PRIVATE)

    fun all(): List<Reminder> {
        val raw = Secure.decrypt(prefs.getString("reminders", "") ?: "")
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Reminder>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Reminder(o.getLong("id"), o.getString("t"), o.getLong("m"), o.optBoolean("d", false)))
            }
            out.sortedBy { it.timeMillis }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<Reminder>) {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().put("id", r.id).put("t", r.text).put("m", r.timeMillis).put("d", r.daily))
        }
        prefs.edit().putString("reminders", Secure.encrypt(arr.toString())).apply()
    }

    fun add(r: Reminder) {
        save(all() + r)
    }

    fun remove(id: Long) {
        save(all().filter { it.id != id })
    }

    fun update(r: Reminder) {
        save(all().map { if (it.id == r.id) r else it })
    }

    fun clear() {
        save(emptyList())
    }
}

class TaskStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_planner", Context.MODE_PRIVATE)

    fun all(): List<Task> {
        val raw = Secure.decrypt(prefs.getString("tasks", "") ?: "")
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Task>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Task(o.getLong("id"), o.getString("t"), o.optBoolean("x", false)))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<Task>) {
        val arr = JSONArray()
        for (t in list) {
            arr.put(JSONObject().put("id", t.id).put("t", t.text).put("x", t.done))
        }
        prefs.edit().putString("tasks", Secure.encrypt(arr.toString())).apply()
    }

    fun add(t: Task) {
        save(all() + t)
    }

    fun setDone(id: Long) {
        save(all().map { if (it.id == id) it.copy(done = true) else it })
    }

    fun remove(id: Long) {
        save(all().filter { it.id != id })
    }
}

object ReminderScheduler {
    const val CHANNEL = "jarvis_reminders"
    const val EXTRA_ID = "rid"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "JARVIS reminders", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "JARVIS ke reminder notifications"
            nm.createNotificationChannel(ch)
        }
    }

    private fun pending(context: Context, id: Long): PendingIntent {
        val i = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(r.timeMillis, show), pending(context, r.id))
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, id))
    }

    fun cancelAll(context: Context) {
        ReminderStore(context).all().forEach { cancel(context, it.id) }
    }

    fun nextDaily(from: Long, now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = from
        while (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun notify(context: Context, r: Reminder) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("JARVIS reminder")
            .setContentText(r.text)
            .setStyle(Notification.BigTextStyle().bigText(r.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(r.id.hashCode(), n)
    }

    fun rescheduleAll(context: Context) {
        val store = ReminderStore(context)
        val now = System.currentTimeMillis()
        for (r in store.all()) {
            if (r.timeMillis > now) {
                schedule(context, r)
            } else if (r.daily) {
                val u = r.copy(timeMillis = nextDaily(r.timeMillis, now))
                store.update(u)
                schedule(context, u)
            } else {
                notify(context, r.copy(text = "Chhuta hua: " + r.text))
                store.remove(r.id)
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        val store = ReminderStore(context)
        val r = store.all().firstOrNull { it.id == id } ?: return
        ReminderScheduler.notify(context, r)
        if (r.daily) {
            val u = r.copy(timeMillis = ReminderScheduler.nextDaily(r.timeMillis, System.currentTimeMillis()))
            store.update(u)
            ReminderScheduler.schedule(context, u)
        } else {
            store.remove(r.id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action
        if (a == Intent.ACTION_BOOT_COMPLETED || a == "android.intent.action.QUICKBOOT_POWERON") {
            ReminderScheduler.rescheduleAll(context)
        }
    }
}
