package com.jarvis.assistant

import java.util.Calendar

object TimeParser {

    class Result(val millis: Long, val daily: Boolean, val rest: String)

    private const val DAY = 86_400_000L

    fun parse(low: String, now: Calendar = Calendar.getInstance()): Result? {
        var rest = " $low "
        var daily = false

        val repeatRe = Regex("\\b(har roz|rozana|roz|daily|every day|everyday)\\b")
        if (repeatRe.containsMatchIn(rest)) {
            daily = true
            rest = repeatRe.replace(rest, " ")
        }

        // relative: "10 minute baad", "in 2 hours"
        val unit = "minutes|minute|mins|min|ghante|ghanta|hours|hour|hrs|hr|seconds|second|secs|sec"
        val relA = Regex("(\\d+)\\s*($unit)\\s*(?:baad|mein|me|later|after)\\b")
        val relB = Regex("\\bin\\s+(\\d+)\\s*($unit)\\b")
        val rel = relA.find(rest) ?: relB.find(rest)
        if (rel != null) {
            val n = rel.groupValues[1].toLongOrNull() ?: return null
            val u = rel.groupValues[2]
            val ms = if (u.startsWith("sec")) {
                n * 1000L
            } else if (u.startsWith("min")) {
                n * 60_000L
            } else {
                n * 3_600_000L
            }
            rest = rest.replace(rel.value, " ")
            return Result(now.timeInMillis + ms, daily, rest)
        }

        // absolute time
        var hour = -1
        var minute = 0
        var ampm = ""

        val m1 = Regex("(\\d{1,2})\\s*([:.])\\s*(\\d{2})\\s*(am|pm|baje)?").find(rest)
            ?.takeIf { it.groupValues[2] == ":" || it.groupValues[4].isNotEmpty() }
        val m2 = Regex("\\bsaade\\s+(\\d{1,2})\\b").find(rest)
        val m3 = Regex("\\b(dedh|dhai)\\b\\s*(?:baje)?").find(rest)
        val m4 = Regex("(\\d{1,2})\\s*(baje|am|pm|o'clock|oclock)").find(rest)

        if (m1 != null) {
            hour = m1.groupValues[1].toInt()
            minute = m1.groupValues[3].toInt()
            val s = m1.groupValues[4]
            if (s == "am" || s == "pm") ampm = s
            rest = rest.replace(m1.value, " ")
        } else if (m2 != null) {
            hour = m2.groupValues[1].toInt()
            minute = 30
            rest = rest.replace(m2.value, " ")
        } else if (m3 != null) {
            hour = if (m3.groupValues[1] == "dedh") 1 else 2
            minute = 30
            rest = rest.replace(m3.value, " ")
        } else if (m4 != null) {
            hour = m4.groupValues[1].toInt()
            val s = m4.groupValues[2]
            if (s == "am" || s == "pm") ampm = s
            rest = rest.replace(m4.value, " ")
        }
        if (hour < 0 || hour > 24 || minute > 59) return null

        // subah / shaam / raat
        val periodRe = Regex("\\b(raat|night|tonight|shaam|sham|evening|afternoon|dopahar|subah|savere|morning)\\b")
        val night = Regex("\\b(raat|night|tonight)\\b").containsMatchIn(rest)
        val pmWord = night || Regex("\\b(shaam|sham|evening|afternoon|dopahar)\\b").containsMatchIn(rest)
        val amWord = Regex("\\b(subah|savere|morning)\\b").containsMatchIn(rest)
        rest = periodRe.replace(rest, " ")

        // aaj / kal / parso
        var dayOffset: Int? = null
        val dayRe = Regex("\\b(kal|tomorrow|parso|parson|aaj|today)\\b")
        val dm = dayRe.find(rest)
        if (dm != null) {
            val w = dm.groupValues[1]
            dayOffset = if (w == "kal" || w == "tomorrow") {
                1
            } else if (w == "parso" || w == "parson") {
                2
            } else {
                0
            }
            rest = dayRe.replace(rest, " ")
        }

        val hour24: Int? = if (ampm == "am") {
            hour % 12
        } else if (ampm == "pm") {
            hour % 12 + 12
        } else if (hour == 24) {
            0
        } else if (hour == 0 || hour >= 13) {
            hour
        } else if (amWord) {
            hour % 12
        } else if (pmWord) {
            if (hour == 12) (if (night) 0 else 12) else hour + 12
        } else {
            null
        }

        val c = now.clone() as Calendar
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (dayOffset != null) c.add(Calendar.DAY_OF_YEAR, dayOffset)

        fun at(h: Int): Long {
            val x = c.clone() as Calendar
            x.set(Calendar.HOUR_OF_DAY, h)
            x.set(Calendar.MINUTE, minute)
            return x.timeInMillis
        }

        var millis: Long
        if (hour24 != null) {
            millis = at(hour24)
            if (dayOffset == null && millis <= now.timeInMillis) millis += DAY
        } else if (dayOffset == null) {
            val h12 = hour % 12
            val a = at(h12)
            val p = at(h12 + 12)
            millis = listOf(a, p, a + DAY).filter { it > now.timeInMillis }.minOrNull() ?: (a + DAY)
        } else {
            val h = if (hour in 5..11) {
                hour
            } else if (hour == 12) {
                12
            } else if (hour in 1..4) {
                hour + 12
            } else {
                hour
            }
            millis = at(h)
        }

        return Result(millis, daily, rest)
    }
}
