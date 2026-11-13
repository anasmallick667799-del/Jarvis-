package com.jarvis.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ON_COLOR = Color(0xFF4FC3F7)
private val OFF_COLOR = Color(0xFF546E7A)
private val DIALOG_BG = Color(0xFF0A1B33)
private val TEXT_COLOR = Color(0xFFE3F2FD)
private val MUTED = Color(0xFF90A4AE)

@Composable
fun MemoryConfirmDialog(fact: String, fromAi: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = DIALOG_BG,
        title = {
            Text(
                if (fromAi) "AI ka suggestion: yaad rakhun?" else "Yaad rakhun?",
                color = Color.White
            )
        },
        text = {
            Text(
                "\"$fact\"\n\nYeh sirf is phone mein save hoga. Jab aap AI se sawaal poochoge, tab yeh baat AI (Google) ko bhi bheji jayegi. Password, OTP ya bank jaisi private cheezein save mat karo.",
                color = TEXT_COLOR,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Text(
                "YES, YAAD RAKHO",
                color = ON_COLOR,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onYes() }.padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                "NO",
                color = MUTED,
                modifier = Modifier.clickable { onNo() }.padding(12.dp)
            )
        }
    )
}

@Composable
fun ContactsWhyDialog(onAllow: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DIALOG_BG,
        title = { Text("Contacts permission", color = Color.White) },
        text = {
            Text(
                "Naam se call lagane ke liye JARVIS ko aapke contacts padhne ki permission chahiye. Yeh sirf tab chalta hai jab aap 'X ko call karo' bolte ho. Contacts phone se bahar nahi jaate aur AI ko nahi bheje jaate. Call ka green button aapko khud dabana hoga.",
                color = TEXT_COLOR,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Text(
                "ALLOW",
                color = ON_COLOR,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAllow() }.padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                color = MUTED,
                modifier = Modifier.clickable { onCancel() }.padding(12.dp)
            )
        }
    )
}

@Composable
fun AiSetupDialog(
    url: String,
    token: String,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DIALOG_BG,
        title = { Text("AI Setup", color = Color.White) },
        text = {
            Column {
                Text(
                    "Cloudflare Worker ka URL aur APP_TOKEN daalo. Yeh sirf is phone mein save honge.",
                    color = TEXT_COLOR,
                    fontSize = 13.sp
                )
                SetupField("Worker URL (https://...)", url, false, onUrl)
                SetupField("APP_TOKEN", token, true, onToken)
            }
        },
        confirmButton = {
            Text(
                "SAVE",
                color = ON_COLOR,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSave() }.padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                color = MUTED,
                modifier = Modifier.clickable { onCancel() }.padding(12.dp)
            )
        }
    )
}

@Composable
fun SettingsDialog(
    rate: Float,
    pitch: Float,
    suggestOn: Boolean,
    onRate: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onToggleSuggest: () -> Unit,
    onClearChat: () -> Unit,
    onClearMemory: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DIALOG_BG,
        title = { Text("Settings", color = Color.White) },
        text = {
            Column {
                Text("Awaaz ki speed", color = TEXT_COLOR, fontSize = 13.sp)
                Row(
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallButton("SLOW", if (rate == 0.8f) ON_COLOR else OFF_COLOR) { onRate(0.8f) }
                    SmallButton("NORMAL", if (rate == 1.0f) ON_COLOR else OFF_COLOR) { onRate(1.0f) }
                    SmallButton("FAST", if (rate == 1.3f) ON_COLOR else OFF_COLOR) { onRate(1.3f) }
                }
                Text("Awaaz ka pitch", color = TEXT_COLOR, fontSize = 13.sp)
                Row(
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallButton("LOW", if (pitch == 0.85f) ON_COLOR else OFF_COLOR) { onPitch(0.85f) }
                    SmallButton("NORMAL", if (pitch == 1.0f) ON_COLOR else OFF_COLOR) { onPitch(1.0f) }
                    SmallButton("HIGH", if (pitch == 1.2f) ON_COLOR else OFF_COLOR) { onPitch(1.2f) }
                }
                Text("AI se yaad rakhne ke suggestions", color = TEXT_COLOR, fontSize = 13.sp)
                Row(modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) {
                    SmallButton(
                        if (suggestOn) "SUGGESTIONS: ON" else "SUGGESTIONS: OFF",
                        if (suggestOn) ON_COLOR else OFF_COLOR
                    ) { onToggleSuggest() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("CLEAR CHAT", Color(0xFFEF5350)) { onClearChat() }
                    SmallButton("CLEAR MEMORY", Color(0xFFEF5350)) { onClearMemory() }
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
