package com.aviad.studiocam.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class InputOption(
    val deviceInfo: AudioDeviceInfo,
    val label: String,
    val channelIndex: Int
)

/**
 * Enumerates real input devices (USB audio interfaces, wired mics, built-in mic)
 * and exposes one selectable option per physical input channel, so a two-channel
 * interface shows up as two distinct choices ("Input 1", "Input 2").
 */
class AudioDeviceManager(private val context: Context) {

    fun listInputOptions(): List<InputOption> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        val options = mutableListOf<InputOption>()
        for (device in devices) {
            val friendly = friendlyName(device)
            val channelCount = device.channelCounts.maxOrNull()?.takeIf { it > 0 } ?: 1
            if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            ) {
                // USB interfaces: expose each physical input separately
                val exposedChannels = channelCount.coerceAtLeast(2)
                for (ch in 0 until exposedChannels) {
                    options.add(InputOption(device, "$friendly · Input ${ch + 1}", ch))
                }
            } else {
                options.add(InputOption(device, friendly, 0))
            }
        }
        return options
    }

    private fun friendlyName(device: AudioDeviceInfo): String {
        val product = device.productName?.toString()?.takeIf { it.isNotBlank() }
        return product ?: when (device.type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "כרטיס USB"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "אוזניות USB"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "מיקרופון מובנה"
            else -> "כניסת אודיו"
        }
    }
}
