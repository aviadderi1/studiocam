package com.aviad.studiocam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aviad.studiocam.ui.theme.MeterAmber
import com.aviad.studiocam.ui.theme.MeterGreen
import com.aviad.studiocam.ui.theme.MeterRed

private const val SEGMENTS = 12

@Composable
fun LedMeter(level: Float, modifier: Modifier = Modifier) {
    val lit = (level.coerceIn(0f, 1f) * SEGMENTS).toInt()
    Row(
        modifier = modifier.fillMaxWidth().height(10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0 until SEGMENTS) {
            val color = when {
                i >= lit -> Color(0xFF2A2A2A)
                i < 6 -> MeterGreen
                i < 9 -> MeterAmber
                else -> MeterRed
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
