package com.jarvis.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DIALOG_BG = Color(0xFF0A1B33)
private val TEXT_COLOR = Color(0xFFE3F2FD)
private val MUTED = Color(0xFF90A4AE)
private val ON_COLOR = Color(0xFF4FC3F7)

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .border(1.dp, ON_COLOR, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(label, color = ON_COLOR, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LockScreen(pinStore: PinStore, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    fun submit() {
        val wait = pinStore.remainingLockSeconds()
        if (wait > 0) {
            msg = "Bahut galat koshish. $wait second ruko."
            pin = ""
            return
        }
        if (pinStore.check(pin)) {
            pin = ""
            msg = ""
            onUnlocked()
            return
        }
        val w2 = pinStore.remainingLockSeconds()
        msg = if (w2 > 0) "Bahut galat koshish. $w2 second ruko." else "Galat PIN"
        pin = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020814))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("J.A.R.V.I.S", color = ON_COLOR, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("PIN daalo", color = MUTED, fontSize = 14.sp)
        Text(
            if (pin.isEmpty()) "- - - -" else "●".repeat(pin.length),
            color = TEXT_COLOR,
            fontSize = 26.sp,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Text(msg, color = Color(0xFFEF5350), fontSize = 13.sp)

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("<", "0", "OK")
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                row.forEach { k ->
                    KeyButton(k) {
                        if (k == "<") {
                            pin = pin.dropLast(1)
                        } else if (k == "OK") {
                            submit()
                        } else if (pin.length < 6) {
                            pin += k
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinFormDialog(
    title: String,
    hints: List<String>,
    onSubmit: (List<String>) -> String?,
    onClose: () -> Unit
) {
    val values = remember { mutableStateListOf<String>().apply { repeat(hints.size) { add("") } } }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DIALOG_BG,
        title = { Text(title, color = Color.White) },
        text = {
            Column {
                hints.forEachIndexed { i, h ->
                    SetupField(h, values[i], true) { values[i] = it }
                }
                if (error.isNotEmpty()) {
                    Text(error, color = Color(0xFFEF5350), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Text(
                "OK",
                color = ON_COLOR,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        val r = onSubmit(values.toList())
                        if (r == null) onClose() else error = r
                    }
                    .padding(12.dp)
            )
        },
        dismissButton = {
            Text("CANCEL", color = MUTED, modifier = Modifier.clickable { onClose() }.padding(12.dp))
        }
    )
}

@Composable
fun SecurityDialog(
    pinOn: Boolean,
    onSetPin: () -> Unit,
    onRemovePin: () -> Unit,
    onLockNow: () -> Unit,
    onWipe: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DIALOG_BG,
        title = { Text("Lock aur Privacy", color = Color.White) },
        text = {
            Column {
                Text("PIN lock: ${if (pinOn) "ON" else "OFF"}", color = TEXT_COLOR, fontSize = 14.sp)
                Text(
                    "AI ko kya jata hai: aapke sawaal, saved yaadein (jab AI se poochte ho) aur pichhle 12 messages. AI token ab encrypted hai. PIN sirf app ka lock hai, phone ke lock ka vikalp nahi.",
                    color = MUTED,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (pinOn) "CHANGE PIN" else "SET PIN") { onSetPin() }
                    if (pinOn) SmallButton("REMOVE PIN") { onRemovePin() }
                }
                if (pinOn) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        SmallButton("LOCK NOW") { onLockNow() }
                    }
                }
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    SmallButton("WIPE ALL DATA", Color(0xFFEF5350)) { onWipe() }
                }
            }
        },
        confirmButton = {
            Text(
                "CLOSE",
                color = ON_COLOR,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose() }.padding(12.dp)
            )
        }
    )
}

@Composable
fun WipeConfirmDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = DIALOG_BG,
        title = { Text("Saara data mitaun?", color = Color.White) },
        text = {
            Text(
                "Chat, yaadein, notes, reminders, tasks, settings, AI token aur PIN sab hamesha ke liye mit jayenge, aur app band ho jayegi. Yeh wapas nahi aayega.",
                color = TEXT_COLOR,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Text(
                "YES, SAB MITAO",
                color = Color(0xFFEF5350),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onYes() }.padding(12.dp)
            )
        },
        dismissButton = {
            Text("NO", color = MUTED, modifier = Modifier.clickable { onNo() }.padding(12.dp))
        }
    )
}
