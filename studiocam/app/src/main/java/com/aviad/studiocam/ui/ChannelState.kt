package com.aviad.studiocam.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aviad.studiocam.audio.InputOption

class EffectState(mixDefault: Float, enabledDefault: Boolean = false) {
    var enabled by mutableStateOf(enabledDefault)
    var mix by mutableStateOf(mixDefault)
    var param2 by mutableStateOf(50f) // reverb: size · delay: feedback
}

class ChannelState(
    initialName: String,
    icon: String
) {
    var name by mutableStateOf(initialName)
    val icon = icon
    var selectedInput by mutableStateOf<InputOption?>(null)
    var volume by mutableStateOf(80f)
    var pan by mutableStateOf(0f) // -50..50
    var level by mutableStateOf(0f) // live RMS, 0f..1f
    val reverb = EffectState(mixDefault = 35f)
    val delay = EffectState(mixDefault = 0f)
}
