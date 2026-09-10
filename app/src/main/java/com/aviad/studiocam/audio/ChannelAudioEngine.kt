package com.aviad.studiocam.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Opens one AudioRecord session on the selected interface (stereo capture covers
 * a standard 2-in audio interface) and publishes a running RMS level (0f..1f) for
 * each of the two physical input channels. Channel strips read whichever index
 * (0 = left/Input 1, 1 = right/Input 2) the user assigned to them.
 *
 * DSP (reverb/delay) is intentionally not applied here yet - this stage only
 * proves the routing and metering. The Oboe-based low-latency effects engine
 * plugs into this same capture point in the next build.
 */
@SuppressLint("MissingPermission")
class ChannelAudioEngine {

    private val _levels = MutableStateFlow(0f to 0f)
    val levels: StateFlow<Pair<Float, Float>> = _levels

    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(device: AudioDeviceInfo) {
        stop()

        val sampleRate = 48000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val record = AudioRecord(
            MediaRecorder.AudioSource.UNPROCESSED,
            sampleRate,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        record.preferredDevice = device
        audioRecord = record

        job = scope.launch {
            val buffer = ShortArray(minBuf)
            record.startRecording()
            while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sumL = 0.0
                    var sumR = 0.0
                    var count = 0
                    var i = 0
                    while (i + 1 < read) {
                        sumL += abs(buffer[i].toInt())
                        sumR += abs(buffer[i + 1].toInt())
                        count++
                        i += 2
                    }
                    if (count > 0) {
                        val peakL = (sumL / count / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                        val peakR = (sumR / count / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                        _levels.value = normalize(peakL) to normalize(peakR)
                    }
                }
            }
        }
    }

    private fun normalize(v: Float): Float = sqrt(v.coerceIn(0f, 1f))

    fun stop() {
        job?.cancel()
        job = null
        audioRecord?.let {
            runCatching { it.stop() }
            it.release()
        }
        audioRecord = null
        _levels.value = 0f to 0f
    }
}
