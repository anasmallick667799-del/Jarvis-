package com.jarvis.assistant

import android.Manifest
import android.app.Activity
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

    private fun wantsListen(i: Intent?): Boolean =
        i?.extras?.get("auto_listen")?.toString() == "true"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && wantsListen(intent)) {
            listenSignal.value = listenSignal.value + 1
        }
        setContent { JarvisScreen(listenSignal) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (wantsListen(intent)) {
            listenSignal.value = listenSignal.value + 1
        }
    }
}

@Composable
fun JarvisScreen(listenSignal: androidx.compose.runtime.MutableState<Int>) {
    val context = LocalContext.current
    val chat = remember { ChatStore(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showContactsWhy by remember { mutableStateOf(false) }
    var pendingCallName by remember { mutableStateOf<String?>(null) }
    var pendingFact by remember { mutableStateOf<String?>(null) }
    var pendingFromAi by remember { mutableStateOf(false) }
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

    fun showReply(r: String) {
        messages.add("JARVIS" to r)
        speaker.speak(r)
        if (!speaker.enabled || !speaker.isReady) state = JState.SLEEPING
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

    DisposableEffect(Unit) {
        onDispose { speaker.shutdown() }
    }

    LaunchedEffect(messages.size) {
        chat.save(messages.toList())
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LaunchedEffect(listenSignal.value) {
        if (listenSignal.value > 0) startListening()
    }
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
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(state.label, color = state.color, fontSize = 13.sp)
            SmallButton(if (aiOn) "AI: ON" else "AI SETUP") {
                urlInput = assistant.ai.url
                tokenInput = assistant.ai.token
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

        ChatList(messages, listState, Modifier.weight(1f).fillMaxWidth())

        InputBar(input, { input = it }, { send() })
    }
}
