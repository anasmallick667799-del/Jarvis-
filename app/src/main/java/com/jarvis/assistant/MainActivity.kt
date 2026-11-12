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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

private const val UNKNOWN_PREFIX = "Yeh command abhi samajh nahi aaya"

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
    val activity = context as ComponentActivity
    val engine = remember { CommandEngine(context.applicationContext) }
    val ai = remember { AiClient(context.applicationContext) }
    val memory = remember { MemoryStore(context.applicationContext) }
    val chat = remember { ChatStore(context.applicationContext) }
    val wake = remember { WakeWord(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var aiOn by remember { mutableStateOf(ai.isConfigured()) }
    var showSetup by remember { mutableStateOf(false) }
    var pendingFact by remember { mutableStateOf<String?>(null) }
    var pendingFromAi by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(ai.url) }
    var tokenInput by remember { mutableStateOf(ai.token) }
    var wakeOn by remember { mutableStateOf(false) }
    var appVisible by remember { mutableStateOf(true) }
    var speakingNow by remember { mutableStateOf(false) }
    var wakeSignal by remember { mutableStateOf(0) }
    var showWakeSetup by remember { mutableStateOf(false) }
    var showMicWhy by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    val messages = remember {
        val saved = chat.load()
        mutableStateListOf<Pair<String, String>>().apply {
            if (saved.isEmpty()) {
                add("JARVIS" to "Namaste! Main JARVIS hoon. MIC dabake bolo ya text likho. Time, battery, calculator, notes, app kholna offline chalta hai. 'yaad rakho ki ...' se meri baatein yaad rakh sakta hoon. Baaki sawaalon ke liye AI SETUP karo. 'help' likho toh list milegi.")
            } else {
                addAll(saved)
            }
        }
    }
    val listState = rememberLazyListState()

    val speaker = remember {
        Speaker(context) { speaking ->
            speakingNow = speaking
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

    fun reply(r: String) {
        messages.add("JARVIS" to r)
        speaker.speak(r)
        if (!speaker.enabled || !speaker.isReady) state = JState.SLEEPING
    }

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
        if (low.contains("wake key")) {
            keyInput = wake.accessKey
            showWakeSetup = true
            reply("Wake word ki AccessKey badalne ke liye dialog khol diya.")
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
            if (ok && question.length in 8..300 && looksPersonal(question)) {
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

    fun startListening() {
        wake.stop()
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

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            wakeOn = true
            messages.add("JARVIS" to "Wake word chalu. Ab 'Jarvis' bolo.")
        } else {
            messages.add("JARVIS" to "Mic permission nahi mili, isliye wake word chalu nahi hua. MIC button abhi bhi kaam karega.")
        }
    }

    fun onWakeTap() {
        if (wakeOn) {
            wakeOn = false
            messages.add("JARVIS" to "Wake word band kar diya.")
            return
        }
        if (!wake.hasKey()) {
            keyInput = ""
            showWakeSetup = true
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) {
            wakeOn = true
            messages.add("JARVIS" to "Wake word chalu. Ab 'Jarvis' bolo.")
        } else {
            showMicWhy = true
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
        val obs = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appVisible = true
            }

            override fun onStop(owner: LifecycleOwner) {
                appVisible = false
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose {
            activity.lifecycle.removeObserver(obs)
            wake.stop()
            speaker.shutdown()
        }
    }

    LaunchedEffect(wakeOn, appVisible, speakingNow, state) {
        val should = wakeOn && appVisible && !speakingNow &&
            state != JState.LISTENING && state != JState.THINKING
        if (should) {
            val err = wake.start { wakeSignal += 1 }
            if (err != null) {
                wakeOn = false
                messages.add("JARVIS" to err)
            }
        } else {
            wake.stop()
        }
    }

    LaunchedEffect(wakeSignal) {
        if (wakeSignal > 0 && state == JState.SLEEPING) startListening()
    }

    LaunchedEffect(messages.size) {
        chat.save(messages.toList())
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showMicWhy) {
        AlertDialog(
            onDismissRequest = { showMicWhy = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Mic permission", color = Color.White) },
            text = {
                Text(
                    "Wake word ke liye JARVIS ko microphone chahiye. Mic sirf tab chalu rehta hai jab WAKE: ON ho aur app khuli ho. Us waqt upar green dot aur status mein SAY 'JARVIS' dikhega. Wake word phone ke andar hi pehchana jata hai, recording save nahi hoti.",
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
                            showMicWhy = false
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable { showMicWhy = false }
                        .padding(12.dp)
                )
            }
        )
    }

    if (showWakeSetup) {
        AlertDialog(
            onDismissRequest = { showWakeSetup = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Wake word setup", color = Color.White) },
            text = {
                Column {
                    Text(
                        "console.picovoice.ai se free AccessKey lekar yahan paste karo. Yeh sirf is phone mein save hogi. Pehchaan ke liye internet chahiye ho sakta hai.",
                        color = Color(0xFFE3F2FD),
                        fontSize = 13.sp
                    )
                    SetupField("Picovoice AccessKey", keyInput, true) { keyInput = it }
                }
            },
            confirmButton = {
                Text(
                    "SAVE",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            wake.saveKey(keyInput)
                            showWakeSetup = false
                            wakeOn = false
                            if (wake.hasKey()) {
                                onWakeTap()
                            } else {
                                messages.add("JARVIS" to "AccessKey khaali hai, wake word off hai.")
                            }
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable { showWakeSetup = false }
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

    val shownLabel = if (state == JState.SLEEPING && wakeOn) "SAY 'JARVIS'" else state.label

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
            Text(shownLabel, color = state.color, fontSize = 13.sp)
            SmallButton(if (aiOn) "AI: ON" else "AI SETUP") {
                urlInput = ai.url
                tokenInput = ai.token
                showSetup = true
            }
            SmallButton(if (wakeOn) "WAKE: ON" else "WAKE: OFF") { onWakeTap() }
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
