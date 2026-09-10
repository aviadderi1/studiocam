package com.aviad.studiocam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviad.studiocam.audio.InputOption
import com.aviad.studiocam.ui.theme.CardBg
import com.aviad.studiocam.ui.theme.TextPrimary
import com.aviad.studiocam.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelStrip(
    state: ChannelState,
    availableInputs: List<InputOption>,
    modifier: Modifier = Modifier
) {
    var editingName by remember { mutableStateOf(false) }
    var inputMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(Color(0xFF0D0D0D), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        // Name row - tap the pencil to rename
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editingName) {
                BasicTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(state.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
            IconButton(onClick = { editingName = !editingName }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "ערוך שם ערוץ", tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Input device picker
        Text("כניסת אודיו", color = TextSecondary, fontSize = 10.sp)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .clickable { inputMenuOpen = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.selectedInput?.label ?: "בחר כניסה",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = inputMenuOpen, onDismissRequest = { inputMenuOpen = false }) {
                availableInputs.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            state.selectedInput = option
                            inputMenuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LedMeter(level = state.level)

        Spacer(Modifier.height(8.dp))
        SliderRow(label = "Volume", value = state.volume, range = 0f..100f, onChange = { state.volume = it })
        SliderRow(label = "Pan", value = state.pan, range = -50f..50f, onChange = { state.pan = it })

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFF1C1C1C))
        Spacer(Modifier.height(10.dp))

        EffectBlock(title = "REVERB", state = state.reverb, param2Label = "Size")
        Spacer(Modifier.height(12.dp))
        EffectBlock(title = "DELAY", state = state.delay, param2Label = "Feedback")
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(48.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(value.toInt().toString(), color = TextSecondary, fontSize = 10.sp, modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun EffectBlock(title: String, state: EffectState, param2Label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = TextPrimary, fontSize = 11.sp, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        Switch(checked = state.enabled, onCheckedChange = { state.enabled = it })
    }
    Spacer(Modifier.height(4.dp))
    val effectAlpha = if (state.enabled) 1f else 0.35f
    Column(modifier = Modifier.alpha(effectAlpha)) {
        SliderRow(label = "Mix", value = state.mix, range = 0f..100f, onChange = { state.mix = it })
        SliderRow(label = param2Label.take(4), value = state.param2, range = 0f..100f, onChange = { state.param2 = it })
    }
}
