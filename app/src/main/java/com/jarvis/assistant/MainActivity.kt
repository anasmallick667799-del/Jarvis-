package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisScreen() }
    }
}

@Composable
fun JarvisScreen() {
    val context = LocalContext.current
    val engine = remember { CommandEngine(context.applicationContext) }
    var state by remember { mutableStateOf(JState.SLEEPING) }
    var input by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            "JARVIS" to "Namaste! Main JARVIS hoon. Neeche command likho, jaise: time kya hai, 5 plus 3, battery, whatsapp kholo. 'help' likho toh poori list milegi."
        )
    }
    val listState = rememberLazyListState()

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        input = ""
        messages.add("YOU" to text)
        messages.add("JARVIS" to engine.handle(text))
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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

        Orb(state) {
            state = JState.entries[(state.ordinal + 1) % JState.entries.size]
        }

        Wave(active = state != JState.SLEEPING, color = state.color)

        Text(
            "■ STOP",
            color = Color(0xFFEF5350),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .border(1.dp, Color(0xFFEF5350), RoundedCornerShape(20.dp))
                .clickable { state = JState.SLEEPING }
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )

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
}
