package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import java.util.Locale

class ContactsHelper(private val context: Context) {

    private val noise = setOf(
        "call", "karo", "kar", "do", "lagao", "laga", "ko", "dial", "please",
        "jarvis", "hey", "mujhe", "ek", "ye", "zara", "phone", "me", "mein", "to", "ka", "ki"
    )
    private val verbs = setOf("karo", "lagao", "laga", "kar", "do", "dial")
    private val questionWords = setOf("kya", "what", "how", "kaise", "why", "kyun", "kaun", "which")

    fun callTarget(text: String): String? {
        val low = text.lowercase(Locale.ROOT)
        if (low.count { it.isDigit() } >= 5) return null
        val tk = low.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        if (tk.isEmpty()) return null
        if ("call" !in tk && "dial" !in tk) return null
        if (tk.any { it in questionWords }) return null
        if (tk.size > 5) return null
        if (tk.first() != "call" && tk.first() != "dial" && tk.none { it in verbs }) return null
        val name = tk.filter { it !in noise }.joinToString(" ")
        return if (name.isBlank() || name.split(" ").size > 3) null else name
    }

    fun dial(name: String): String {
        val found = search(name)
        if (found.isEmpty()) return "'$name' naam ka koi contact nahi mila."

        val exact = found.filter { it.first.equals(name, ignoreCase = true) }
        val pick = when {
            found.size == 1 -> found[0]
            exact.size == 1 -> exact[0]
            else -> null
        }
        if (pick == null) {
            val list = found.take(4).mapIndexed { i, c -> "${i + 1}. ${c.first}" }.joinToString(", ")
            return "Kai contacts mile: $list. Poora naam bolo, jaise: ${found[0].first} ko call karo"
        }
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(pick.second)))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "${pick.first} ka number dialer mein khol diya. Green button aapko dabana hoga."
        } catch (e: Exception) {
            "Dialer nahi khul paaya."
        }
    }

    private fun search(name: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            val cur = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cur?.use {
                while (it.moveToNext()) {
                    val n = it.getString(0) ?: continue
                    val num = it.getString(1) ?: continue
                    if (out.none { o -> o.first == n }) out.add(n to num)
                    if (out.size >= 8) break
                }
            }
        } catch (e: Exception) {
            // permission ya koi aur dikkat
        }
        return out
    }
}
