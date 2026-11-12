package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private const val UNKNOWN_PREFIX = "Yeh command abhi samajh nahi aaya"
private val ON_COLOR = Color(0xFF4FC3F7)
private val OFF_COLOR = Color(0xFF546E7A)

private val PERSONAL_WORDS = setOf(
    "mera", "meri", "mere", "mujhe", "main", "mai", "my", "i", "i'm", "hum", "humare", "hamara"
)

private fun looksPersonal(t: String): Boolean {
    val words = t.lowercase().split(Regex("[^a-z']+"))
    return words.any { it in PERSONAL_WORDS }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisScreen() }
    }
}

@Composable
fun SmallButton(label: String, color: Color = Color(0xFF4FC3F7), onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun SetupField(hint: String, value: String, secret: Boolean, onChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Color(0xFF020814), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (value.isEmpty()) {
            Text(hint, color = Color(0xFF607D8B), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFFE3F2FD), fontSize = 14.sp),
            cursorBrush = SolidColor(Color(0xFF4FC3F7)),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun JarvisScreen() {
    val context = LocalContext.current
    val engine = remember { CommandEngine(context.applicationContext) }
    val ai = remember { AiClient(context.applicationContext) }
    val memory = remember { MemoryStore(context.applicationContext) }
    val chat = remember { ChatStore(context.applicationContext) }
    val settings = remember { AppSettings(context.applicationContext) }
    val weather = remember { Weather(context.applicationContext) }
    val contacts = remember { ContactsHelper(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var aiOn by remember { mutableStateOf(ai.isConfigured()) }
    var showSetup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showContactsWhy by remember { mutableStateOf(false) }
    var pendingCallName by remember { mutableStateOf<String?>(null) }
    var pendingFact by remember { mutableStateOf<String?>(null) }
    var pendingFromAi by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(ai.url) }
    var tokenInput by remember { mutableStateOf(ai.token) }
    var suggestOn by remember { mutableStateOf(settings.suggestions) }
    val messages = remember {
        val saved = chat.load()
        mutableStateListOf<Pair<String, String>>().apply {
            if (saved.isEmpty()) {
                add("JARVIS" to "Namaste! Main JARVIS hoon. MIC dabake bolo ya text likho. Time, battery, calculator, notes, alarm, timer, torch, mausam, joke, naam se call, sab try karo. 'help' likho toh list milegi.")
            } else {
                addAll(saved)
            }
        }
    }
    val listState = rememberLazyListState()

    val speaker = remember {
        Speaker(context) { speaking ->
            state = if (speaking) {
                JState.SPEAKING
            } else if (state == JState.SPEAKING || state == JState.THINKING) {
                JState.SLEEPING
            } else {
                state
            }
        }
    }
    var voiceOn by remember { mutableStateOf(speaker.enabled) }
    var lang by remember { mutableStateOf(speaker.lang) }
    var rate by remember { mutableStateOf(speaker.rate) }
    var pitch by remember { mutableStateOf(speaker.pitch) }

    fun reply(r: String) {
        messages.add("JARVIS" to r)
        speaker.speak(r)
        if (!speaker.enabled || !speaker.isReady) state = JState.SLEEPING
    }

    fun hasContacts(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    fun process(text: String) {
        messages.add("YOU" to text)
        state = JState.THINKING

        val low = text.lowercase()
        if (low.contains("chat saaf") || low.contains("clear chat") || low.contains("chat clear") || low.contains("chat delete")) {
            messages.clear()
            chat.clear()
            ai.clearHistory()
            reply("Chat saaf kar di. Yaadein (memory) waise hi hain.")
            return
        }

        val mem = memory.handle(text)
        if (mem != null) {
            if (mem.pendingFact != null) {
                pendingFromAi = false
                pendingFact = mem.pendingFact
            }
            reply(mem.reply)
            return
        }

        val setCity = weather.setCityCommand(text)
        if (setCity != null) {
            reply(setCity)
            return
        }

        val wq = weather.parse(text)
        if (wq != null) {
            val city = if (wq.isBlank()) weather.savedCity() else wq
            if (city.isBlank()) {
                reply("Kis shehar ka mausam? Jaise: mausam Delhi. Ya pehle bolo: mera shehar Delhi hai")
            } else {
                weather.fetch(city) { answer -> reply(answer) }
            }
            return
        }

        val joke = Jokes.match(low)
        if (joke != null) {
            reply(joke)
            return
        }

        val callTarget = contacts.callTarget(text)
        if (callTarget != null) {
            if (hasContacts()) {
                reply(contacts.dial(callTarget))
            } else {
                pendingCallName = callTarget
                showContactsWhy = true
                reply("Naam se call ke liye contacts ki permission chahiye. Dialog mein ALLOW dabao.")
            }
            return
        }

        val forceAi = low.startsWith("ai ")
        val offline = if (forceAi) null else engine.handle(text).takeIf { !it.startsWith(UNKNOWN_PREFIX) }
        if (offline != null) {
            reply(offline)
            return
        }
        if (!ai.isConfigured()) {
            reply("AI abhi set nahi hai. Upar AI SETUP dabake Worker URL aur token daalo.")
            return
        }
        val question = if (forceAi) text.substring(3).trim() else text
        ai.ask(question, memory.all()) { answer, ok ->
            reply(answer)
            if (ok && suggestOn && question.length in 8..300 && looksPersonal(question)) {
                ai.suggestFact(question) { fact ->
                    if (fact != null &&
                        pendingFact == null &&
                        !memory.isSensitive(fact) &&
                        memory.all().none { it.equals(fact, ignoreCase = true) }
                    ) {
                        pendingFromAi = true
                        pendingFact = fact
                    }
                }
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (state == JState.LISTENING) state = JState.SLEEPING
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                process(text)
            } else {
                messages.add("JARVIS" to "Kuch samajh nahi aaya. Dobara bolo.")
            }
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val name = pendingCallName
        pendingCallName = null
        if (granted && name != null) {
            reply(contacts.dial(name))
        } else {
            reply("Contacts permission nahi mili, isliye naam se call nahi laga sakta. Number ke saath bolo: call 9876543210")
        }
    }

    fun startListening() {
        speaker.stop()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (speaker.lang == "hi") "hi-IN" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS sun raha hai...")
        }
        try {
            state = JState.LISTENING
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            state = JState.SLEEPING
            messages.add("JARVIS" to "Voice input dialog nahi khul paaya. Google app install/update karo.")
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        process(text)
    }

    fun sample() {
        speaker.speak(
            if (speaker.lang == "hi") "नमस्ते, मैं जार्विस हूँ।" else "Hello, I am Jarvis."
        )
    }

    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }

    LaunchedEffect(messages.size) {
        chat.save(messages.toList())
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showContactsWhy) {
        AlertDialog(
            onDismissRequest = {
                showContactsWhy = false
                pendingCallName = null
            },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Contacts permission", color = Color.White) },
            text = {
                Text(
                    "Naam se call lagane ke liye JARVIS ko aapke contacts padhne ki permission chahiye. Yeh sirf tab chalta hai jab aap 'X ko call karo' bolte ho. Contacts phone se bahar nahi jaate aur AI ko nahi bheje jaate. Call ka green button aapko khud dabana hoga.",
                    color = Color(0xFFE3F2FD),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Text(
                    "ALLOW",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            showContactsWhy = false
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable {
                            showContactsWhy = false
                            pendingCallName = null
                        }
                        .padding(12.dp)
                )
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Settings", color = Color.White) },
            text = {
                Column {
                    Text("Awaaz ki speed", color = Color(0xFFE3F2FD), fontSize = 13.sp)
                    Row(
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallButton("SLOW", if (rate == 0.8f) ON_COLOR else OFF_COLOR) {
                            rate = 0.8f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                        SmallButton("NORMAL", if (rate == 1.0f) ON_COLOR else OFF_COLOR) {
                            rate = 1.0f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                        SmallButton("FAST", if (rate == 1.3f) ON_COLOR else OFF_COLOR) {
                            rate = 1.3f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                    }
                    Text("Awaaz ka pitch", color = Color(0xFFE3F2FD), fontSize = 13.sp)
                    Row(
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmallButton("LOW", if (pitch == 0.85f) ON_COLOR else OFF_COLOR) {
                            pitch = 0.85f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                        SmallButton("NORMAL", if (pitch == 1.0f) ON_COLOR else OFF_COLOR) {
                            pitch = 1.0f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                        SmallButton("HIGH", if (pitch == 1.2f) ON_COLOR else OFF_COLOR) {
                            pitch = 1.2f
                            speaker.setVoiceStyle(rate, pitch)
                            sample()
                        }
                    }
                    Text("AI se yaad rakhne ke suggestions", color = Color(0xFFE3F2FD), fontSize = 13.sp)
                    Row(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                        SmallButton(
                            if (suggestOn) "SUGGESTIONS: ON" else "SUGGESTIONS: OFF",
                            if (suggestOn) ON_COLOR else OFF_COLOR
                        ) {
                            suggestOn = !suggestOn
                            settings.suggestions = suggestOn
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton("CLEAR CHAT", Color(0xFFEF5350)) {
                            messages.clear()
                            chat.clear()
                            ai.clearHistory()
                            messages.add("JARVIS" to "Chat saaf kar di. Yaadein (memory) waise hi hain.")
                        }
                        SmallButton("CLEAR MEMORY", Color(0xFFEF5350)) {
                            memory.clear()
                            messages.add("JARVIS" to "Saari yaadein mita di.")
                        }
                    }
                }
            },
            confirmButton = {
                Text(
                    "CLOSE",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { showSettings = false }
                        .padding(12.dp)
                )
            }
        )
    }

    val pf = pendingFact
    if (pf != null) {
        AlertDialog(
            onDismissRequest = { pendingFact = null },
            containerColor = Color(0xFF0A1B33),
            title = {
                Text(
                    if (pendingFromAi) "AI ka suggestion: yaad rakhun?" else "Yaad rakhun?",
                    color = Color.White
                )
            },
            text = {
                Text(
                    "\"$pf\"\n\nYeh sirf is phone mein save hoga. Jab aap AI se sawaal poochoge, tab yeh baat AI (Google) ko bhi bheji jayegi. Password, OTP ya bank jaisi private cheezein save mat karo.",
                    color = Color(0xFFE3F2FD),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Text(
                    "YES, YAAD RAKHO",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val ok = memory.add(pf)
                            pendingFact = null
                            reply(
                                if (ok) {
                                    "Theek hai, yaad rakh liya: $pf"
                                } else {
                                    "Yeh pehle se yaad hai ya memory bhar gayi hai (30 yaadein)."
                                }
                            )
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "NO",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable {
                            pendingFact = null
                            messages.add("JARVIS" to "Theek hai, yaad nahi rakha.")
                        }
                        .padding(12.dp)
                )
            }
        )
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("AI Setup", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Cloudflare Worker ka URL aur APP_TOKEN daalo. Yeh sirf is phone mein save honge.",
                        color = Color(0xFFE3F2FD),
                        fontSize = 13.sp
                    )
                    SetupField("Worker URL (https://...)", urlInput, false) { urlInput = it }
                    SetupField("APP_TOKEN", tokenInput, true) { tokenInput = it }
                }
            },
            confirmButton = {
                Text(
                    "SAVE",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            ai.save(urlInput, tokenInput)
                            aiOn = ai.isConfigured()
                            showSetup = false
                            val msg = if (aiOn) {
                                "AI setup save ho gaya. Ab koi bhi sawaal pucho, ya 'ai ...' se shuru karo."
                            } else {
                                "URL ya token khaali hai, AI abhi off hai."
                            }
                            messages.add("JARVIS" to msg)
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable { showSetup = false }
                        .padding(12.dp)
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020814))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "J.A.R.V.I.S",
            color = Color(0xFF4FC3F7),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(state.label, color = state.color, fontSize = 13.sp)
            SmallButton(if (aiOn) "AI: ON" else "AI SETUP") {
                urlInput = ai.url
                tokenInput = ai.token
                showSetup = true
            }
            SmallButton("SETTINGS") { showSettings = true }
        }

        Orb(state) { startListening() }

        Wave(active = state != JState.SLEEPING, color = state.color)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallButton("■ STOP", Color(0xFFEF5350)) {
                speaker.stop()
                state = JState.SLEEPING
            }
            SmallButton(if (voiceOn) "VOICE ON" else "VOICE OFF") {
                voiceOn = !voiceOn
                speaker.turnVoice(voiceOn)
                if (!voiceOn && state == JState.SPEAKING) state = JState.SLEEPING
            }
            SmallButton("NEXT") {
                messages.add("JARVIS" to speaker.nextVoice())
                sample()
            }
            SmallButton(if (lang == "en") "LANG EN" else "LANG HI") {
                messages.add("JARVIS" to speaker.toggleLang())
                lang = speaker.lang
                sample()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(messages) { (who, text) ->
                val mine = who == "YOU"
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    Text(who, color = Color(0xFF4FC3F7), fontSize = 11.sp)
                    Text(
                        text,
                        color = Color(0xFFE3F2FD),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .background(
                                if (mine) Color(0xFF12365E) else Color(0xFF0A1B33),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF0A1B33), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (input.isEmpty()) {
                    Text("Command ya sawaal likho...", color = Color(0xFF607D8B), fontSize = 15.sp)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = Color(0xFFE3F2FD), fontSize = 15.sp),
                    cursorBrush = SolidColor(Color(0xFF4FC3F7)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "SEND",
                color = Color(0xFF4FC3F7),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable { send() }
                    .padding(8.dp)
            )
        }
    }
}
