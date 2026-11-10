package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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
fun JarvisScreen() {
    val context = LocalContext.current
    val engine = remember { CommandEngine(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            "JARVIS" to "Namaste! Main JARVIS hoon. Ab MIC dabake bol bhi sakte ho, ya neeche text likho. Jaise: time kya hai, 5 plus 3, battery. 'help' likho toh poori list milegi."
        )
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

    fun process(text: String) {
        messages.add("YOU" to text)
        state = JState.THINKING
        val reply = engine.handle(text)
        messages.add("JARVIS" to reply)
        speaker.speak(reply)
        if (!speaker.enabled || !speaker.isReady)package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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
fun JarvisScreen() {
    val context = LocalContext.current
    val engine = remember { CommandEngine(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }
    var showRationale by remember { mutableStateOf(false) }
    val messages = remember {
        mutableStateListOf(
            "JARVIS" to "Namaste! Main JARVIS hoon. Ab MIC dabake bol bhi sakte ho, ya neeche text likho. Jaise: time kya hai, 5 plus 3, battery. 'help' likho toh poori list milegi."
        )
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

    fun process(text: String) {
        messages.add("YOU" to text)
        state = JState.THINKING
        val reply = engine.handle(text)
        messages.add("JARVIS" to reply)
        speaker.speak(reply)
        if (!speaker.enabled || !speaker.isReady) state = JState.SLEEPING
    }

    val voice = remember {
        VoiceInput(
            context,
            { partial -> heard = partial },
            { text ->
                heard = ""
                process(text)
            },
            { err ->
                heard = ""
                messages.add("JARVIS" to err)
                if (state == JState.LISTENING) state = JState.SLEEPING
            },
            { listening ->
                if (listening) {
                    state = JState.LISTENING
                } else if (state == JState.LISTENING) {
                    state = JState.SLEEPING
                }
            }
        )
    }

    fun startListening() {
        speaker.stop()
        heard = ""
        voice.start(speaker.lang)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            messages.add(
                "JARVIS" to "Mic permission nahi mili. Text se kaam chalta rahega. Baad mein chahiye toh Settings, Apps, JARVIS, Permissions mein Microphone allow karo."
            )
        }
    }

    fun onMicTap() {
        if (state == JState.LISTENING) {
            voice.stop()
            state = JState.SLEEPING
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) startListening() else showRationale = true
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
        onDispose {
            voice.destroy()
            speaker.shutdown()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Mic permission", color = Color.White) },
            text = {
                Text(
                    "JARVIS ko aapki awaaz sunne ke liye microphone chahiye. Mic sirf tab chalu hota hai jab aap MIC dabate ho (status LISTENING dikhta hai aur upar green dot aata hai). JARVIS recording save nahi karta. Online hone par Google ki speech service awaaz ko text mein badalne ke liye use hoti hai.",
                    color = Color(0xFFE3F2FD)
                )
            },
            confirmButton = {
                Text(
                    "ALLOW",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            showRationale = false
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable { showRationale = false }
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
        Text(
            state.label,
            color = state.color,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Orb(state) { onMicTap() }

        Wave(active = state != JState.SLEEPING, color = state.color)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallButton("■ STOP", Color(0xFFEF5350)) {
                speaker.stop()
                voice.stop()
                heard = ""
                state = JState.SLEEPING
            }
            SmallButton(if (voiceOn) "VOICE ON" else "VOICE OFF") {
                voiceOn = !voiceOn
                speaker.turnVoice(voiceOn)
                if (!voiceOn && state == JState.SPEAKING) state = JState.SLEEPING
            }
            SmallButton("NEXT VOICE") {
                messages.add("JARVIS" to speaker.nextVoice())
                sample()
            }
            SmallButton(if (lang == "en") "LANG EN" else "LANG HI") {
                messages.add("JARVIS" to speaker.toggleLang())
                lang = speaker.lang
                sample()
            }
        }

        if (heard.isNotEmpty()) {
            Text(
                "Sun raha hoon: $heard",
                color = Color(0xFF81D4FA),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
                    Text("Command likho...", color = Color(0xFF607D8B), fontSize = 15.sp)
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
}￼Enter state = JState.SLEEPING
    }

    val voice = remember {
        VoiceInput(
            context,
            { partial -> heard = partial },
            { text ->
                heard = ""
                process(text)
            },
            { err ->
                heard = ""
                messages.add("JARVIS" to err)
                if (state == JState.LISTENING) state = JState.SLEEPING
            },
            { listening ->
                if (listening) {
                    state = JState.LISTENING
                } else if (state == JState.LISTENING) {
                    state = JState.SLEEPING
                }
      }
        )
    }

    fun startListening() {
        speaker.stop()
        heard = ""
        voice.start(speaker.lang)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        } else {
            messages.add(
                "JARVIS" to "Mic permission nahi mili. Text se kaam chalta rahega. Baad mein chahiye toh Settings, Apps, JARVIS, Permissions mein Microphone allow karo."
            )
        }
    }

    fun onMicTap() {
        if (state == JState.LISTENING) {
            voice.stop()
            state = JState.SLEEPING
            return
        }
        val ok = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) startListening() else showRationale = true
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
        onDispose {
            voice.destroy()
            speaker.shutdown()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            containerColor = Color(0xFF0A1B33),
            title = { Text("Mic permission", color = Color.White) },
            text = {
                Text(
                    "JARVIS ko aapki awaaz sunne ke liye microphone chahiye. Mic sirf tab chalu hota hai jab aap MIC dabate ho (status LISTENING dikhta hai aur upar green dot aata hai). JARVIS recording save nahi karta. Online hone par Google ki speech service awaaz ko text mein badalne ke liye use hoti hai.",
                    color = Color(0xFFE3F2FD)
                )
            },
            confirmButton = {
                Text(
                    "ALLOW",
                    color = Color(0xFF4FC3F7),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            showRationale = false
                            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    "CANCEL",
                    color = Color(0xFF90A4AE),
                    modifier = Modifier
                        .clickable { showRationale = false }
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
