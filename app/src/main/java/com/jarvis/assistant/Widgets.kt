package com.jarvis.assistant

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun Orb(state: JState, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        1f, 1.12f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale = if (state == JState.SLEEPING) 1f else pulse

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(170.dp)
    ) {
        Box(
            Modifier
                .size(160.dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(listOf(state.color.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF81D4FA), Color(0xFF0D47A1))))
                .border(2.dp, state.color, CircleShape)
                .clickable(onClick = onClick)
        ) {
            Text("MIC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Wave(active: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "wave")
    Row(
        modifier = Modifier.height(48.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 20) {
            val h by t.animateFloat(
                0.15f, 1f,
                infiniteRepeatable(tween(350 + i * 41, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight(if (active) h else 0.1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
