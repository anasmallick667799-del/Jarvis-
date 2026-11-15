package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val listenSignal = mutableStateOf(0)
    private val relockSignal = mutableStateOf(0)
    private var stoppedAt = 0L

    private fun wantsListen(i: Intent?): Boolean =
        i?.extras?.get("auto_listen")?.toString() == "true"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && wantsListen(intent)) {
            listenSignal.value = listenSignal.value + 1
        }
        setContent { JarvisScreen(listenSignal, relockSignal) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (wantsListen(intent)) {
            listenSignal.value = listenSignal.value + 1
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 &&
            System.currentTimeMillis() - stoppedAt > 30_000 &&
            PinStore(this).hasPin()
        ) {
            relockSignal.value = relockSignal.value + 1
        }
    }
}

@Composable
fun JarvisScreen(
    listenSignal: androidx.compose.runtime.MutableState<Int>,
    relockSignal: androidx.compose.runtime.MutableState<Int>
) {
    val context = LocalContext.current
    val chat = remember { ChatStore(context.applicationContext) }
    val pinStore = remember { PinStore(context.applicationContext) }
    var locked by remember { mutableStateOf(pinStore.hasPin()) }
    var pinOn by remember { mutableStateOf(pinStore.hasPin()) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var showWipe by remember { mutableStateOf(false) }
    var pinForm by remember { mutableStateOf(0) }
    var showContactsWhy by remember { mutableStateOf(false) }
    var pendingCallName by remember { mutableStateOf<String?>(null) }
    var pendingFact by remember { mutableStateOf<String?>(null) }
    var pendingFromAi by remember { mutableStateOf(false) }
    var handledSignal by remember { mutableStateOf(0) }
    val messages = remember {
        val saved = chat.load()
        mutableStateListOf<Pair<String, String>>().apply {
            if (saved.isEmpty()) {
                add("JARVIS" to "Namaste! Main JARVIS hoon. MIC dabake bolo ya text likho. Time, battery, calculator, notes, alarm, reminders, tasks, mausam, joke, naam se call, sab try karo. 'help' likho toh list milegi.")
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

    fun showReply(r: String) {
        messages.add("JARVIS" to r)
        if (!locked) speaker.speak(r)
        if (locked || !speaker.enabled || !speaker.isReady) state = JState.SLEEPING
    }

    val assistant = remember {
        Assistant(
            context.applicationContext,
            object : AssistantHost {
                override fun say(text: String) {
                    showReply(text)
                }

                override fun setBusy() {
                    state = JState.THINKING
                }

                override fun suggestMemory(fact: String, fromAi: Boolean) {
                    if (pendingFact == null) {
                        pendingFromAi = fromAi
                        pendingFact = fact
                    }
                }

                override fun askContactsPermission(name: String) {
                    pendingCallName = name
                    showContactsWhy = true
                }

                override fun clearChat() {
                    messages.clear()
                    chat.clear()
                }
            }
        )
    }

    var aiOn by remember { mutableStateOf(assistant.ai.isConfigured()) }
    var urlInput by remember { mutableStateOf(assistant.ai.url) }
    var tokenInput by remember { mutableStateOf(assistant.ai.token) }
    var suggestOn by remember { mutableStateOf(assistant.settings.suggestions) }

    fun process(text: String) {
        messages.add("YOU" to text)
        assistant.handle(text)
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
            assistant.dialContact(name)
        } else {
            showReply("Contacts permission nahi mili, isliye naam se call nahi laga sakta. Number ke saath bolo: call 9876543210")
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

    fun wipeAll() {
        ReminderScheduler.cancelAll(context)
        Secure.deleteKey()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.clearApplicationUserData()
    }

    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }

    LaunchedEffect(messages.size) {
        chat.save(messages.toList())
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(relockSignal.value) {
        if (relockSignal.value > 0 && pinStore.hasPin()) {
            locked = true
            showSetup = false
            showSettings = false
            showSecurity = false
            showWipe = false
            pinForm = 0
        }
    }

    LaunchedEffect(listenSignal.value, locked) {
        if (!locked && listenSignal.value > handledSignal) {
            handledSignal = listenSignal.value
            startListening()
        }
    }

    if (locked) {
        LockScreen(pinStore) { locked = false }
    } else {
        if (showContactsWhy) {
            ContactsWhyDialog(
                onAllow = {
                    showContactsWhy = false
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                onCancel = {
                    showContactsWhy = false
                    pendingCallName = null
                }
            )
        }

        if (showSettings) {
            SettingsDialog(
                rate = rate,
                pitch = pitch,
                suggestOn = suggestOn,
                onRate = {
                    rate = it
                    speaker.setVoiceStyle(rate, pitch)
                    sample()
                },
                onPitch = {
                    pitch = it
                    speaker.setVoiceStyle(rate, pitch)
                    sample()
                },
                onToggleSuggest = {
                    suggestOn = !suggestOn
                    assistant.settings.suggestions = suggestOn
                },
                onClearChat = {
                    messages.clear()
                    chat.clear()
                    assistant.ai.clearHistory()
                    messages.add("JARVIS" to "Chat saaf kar di. Yaadein (memory) waise hi hain.")
                },
                onClearMemory = {
                    assistant.memory.clear()
                    messages.add("JARVIS" to "Saari yaadein mita di.")
                },
                onClose = { showSettings = false }
            )
        }

        if (showSecurity) {
            SecurityDialog(
                pinOn = pinOn,
                onSetPin = {
                    showSecurity = false
                    pinForm = 1
                },
                onRemovePin = {
                    showSecurity = false
                    pinForm = 2
                },
                onLockNow = {
                    showSecurity = false
                    locked = true
                },
                onWipe = {
                    showSecurity = false
                    showWipe = true
                },
                onClose = { showSecurity = false }
            )
        }

        if (pinForm == 1) {
            val has = pinOn
            PinFormDialog(
                title = if (has) "PIN badlo" else "PIN set karo",
                hints = if (has) {
                    listOf("Purana PIN", "Naya PIN (4 se 6 ank)", "Naya PIN dobara")
                } else {
                    listOf("Naya PIN (4 se 6 ank)", "Naya PIN dobara")
                },
                onSubmit = { v ->
                    val old = if (has) v[0] else ""
                    val n1 = if (has) v[1] else v[0]
                    val n2 = if (has) v[2] else v[1]
                    if (has && !pinStore.check(old)) {
                        "Purana PIN galat hai."
                    } else if (!Regex("\\d{4,6}").matches(n1)) {
                        "PIN sirf 4 se 6 ank ka hona chahiye."
                    } else if (n1 != n2) {
                        "Dono PIN alag hain."
                    } else {
                        pinStore.setPin(n1)
                        pinOn = true
                        messages.add("JARVIS" to "PIN set ho gaya. App 30 second se zyada background mein rahi toh lock ho jayegi.")
                        null
                    }
                },
                onClose = { pinForm = 0 }
            )
        }

        if (pinForm == 2) {
            PinFormDialog(
                title = "PIN hatao",
                hints = listOf("Abhi ka PIN"),
                onSubmit = { v ->
                    if (pinStore.check(v[0])) {
                        pinStore.remove()
                        pinOn = false
                        messages.add("JARVIS" to "PIN lock hata diya.")
                        null
                    } else {
                        "PIN galat hai."
                    }
                },
                onClose = { pinForm = 0 }
            )
        }

        if (showWipe) {
            WipeConfirmDialog(
                onYes = {
                    showWipe = false
                    wipeAll()
                },
                onNo = { showWipe = false }
            )
        }

        val pf = pendingFact
        if (pf != null) {
            MemoryConfirmDialog(
                fact = pf,
                fromAi = pendingFromAi,
                onYes = {
                    val ok = assistant.memory.add(pf)
                    pendingFact = null
                    showReply(
                        if (ok) {
                            "Theek hai, yaad rakh liya: $pf"
                        } else {
                            "Yeh pehle se yaad hai ya memory bhar gayi hai (30 yaadein)."
                        }
                    )
                },
                onNo = {
                    pendingFact = null
                    messages.add("JARVIS" to "Theek hai, yaad nahi rakha.")
                }
            )
        }

        if (showSetup) {
            AiSetupDialog(
                url = urlInput,
                token = tokenInput,
                onUrl = { urlInput = it },
                onToken = { tokenInput = it },
                onSave = {
                    assistant.ai.save(urlInput, tokenInput)
                    aiOn = assistant.ai.isConfigured()
                    showSetup = false
                    val msg = if (aiOn) {
                        "AI setup save ho gaya. Ab koi bhi sawaal pucho, ya 'ai ...' se shuru karo."
                    } else {
                        "URL ya token khaali hai, AI abhi off hai."
                    }
                    messages.add("JARVIS" to msg)
                },
                onCancel = { showSetup = false }
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(state.label, color = state.color, fontSize = 13.sp)
                SmallButton(if (aiOn) "AI: ON" else "AI SETUP") {
                    urlInput = assistant.ai.url
                    tokenInput = assistant.ai.token
                    showSetup = true
                }
                SmallButton("SETTINGS") { showSettings = true }
                SmallButton("LOCK") { showSecurity = true }
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

            ChatList(messages, listState, Modifier.weight(1f).fillMaxWidth())

            InputBar(input, { input = it }, { send() })
        }
    }
}
