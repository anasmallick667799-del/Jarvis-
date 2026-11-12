package com.jarvis.assistant

import android.content.Context
import java.util.Locale

class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)

    var suggestions: Boolean
        get() = prefs.getBoolean("suggestions", true)
        set(value) {
            prefs.edit().putBoolean("suggestions", value).apply()
        }
}

object Jokes {

    private val list = listOf(
        "Pappu ne dukaandaar se pucha: Bhaiya, yeh ghadi kitne ki hai? Dukaandaar bola: Paanch hazaar. Pappu bola: Itni mehngi? Mere paas toh time hi nahi hai!",
        "Doctor ne kaha: Aapko aaram ki zaroorat hai. Mareez bola: Par mere paas Wi-Fi nahi hai. Doctor bola: Isliye toh aaram bol raha hoon.",
        "Teacher ne pucha: Agar tumhare paas 10 aam hon aur 3 mujhe de do, toh kitne bachenge? Student bola: Sir, 10. Kyunki main aam kisi ko dene wala hi nahi hoon.",
        "Computer ne doctor se kaha: Mujhe virus ho gaya hai. Doctor bola: Main insaan ka doctor hoon, aap antivirus ke paas jaaiye.",
        "Mummy ne kaha: Beta, phone chhodo aur padhai karo. Bete ne kaha: Mummy, padhai hi kar raha hoon, Google pe dekh raha hoon!",
        "Ek aadmi bola: Mujhe aisi cheez do jo sab kuch theek kar de. Dukaandaar bola: Sir, woh toh kal ki neend hai."
    )

    fun match(text: String): String? {
        val tk = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
        val keys = setOf("joke", "jokes", "chutkula", "chutkule", "चुटकुला")
        return if (tk.any { it in keys }) list.random() else null
    }
}
