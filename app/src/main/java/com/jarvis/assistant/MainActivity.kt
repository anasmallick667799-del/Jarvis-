package com.jarvis.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class JState(val label: String, val color: Color) {
    SLEEPING("SLEEPING", Color(0xFF546E7A)),
    LISTENING("LISTENING", Color(0xFF29B6F6)),
    THINKING("THINKING", Color(0xFFFFCA28)),
    SPEAKING("SPEAKING", Color(0xFF26C6DA))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JarvisScreen() }
    }
}

@Composable
fun JarvisScreen() {
    var state by remember { mutableStateOf(JState.SLEEPING) }
    val messages = remember {
        mutableStateListOf(
            "JARVIS" to "Systems online. Phase 1: sirf UI demo hai.",
            "JARVIS" to "Orb ko tap karo, status badlega. Voice abhi nahi hai."
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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            state.label,
            color = state.color,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        Orb(state) {
            state = JState.entries[(state.ordinal + 1) % JState.entries.size]
        }

        Wave(active = state != JState.SLEEPING, color = state.color)

        Text(
            "■ STOP",
            color = Color(0xFFEF5350),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .border(1.dp, Color(0xFFEF5350), RoundedCornerShape(20.dp))
                .clickable { state = JState.SLEEPING }
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
