package com.jarvis.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun ChatList(
    messages: List<Pair<String, String>>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(state = listState, modifier = modifier) {
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
}

@Composable
fun InputBar(input: String, onChange: (String) -> Unit, onSend: () -> Unit) {
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
                onValueChange = onChange,
                textStyle = TextStyle(color = Color(0xFFE3F2FD), fontSize = 15.sp),
                cursorBrush = SolidColor(Color(0xFF4FC3F7)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
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
                .clickable { onSend() }
                .padding(8.dp)
        )
    }
}
