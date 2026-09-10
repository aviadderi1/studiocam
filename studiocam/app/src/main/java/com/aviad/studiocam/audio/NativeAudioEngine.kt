package com.aviad.studiocam.audio

object NativeAudioEngine {
    init {
        System.loadLibrary("studiocam_audio")
    }

    fun start(inputDeviceId: Int): Boolean = nativeStart(inputDeviceId)
    fun stop() = nativeStop()

    fun setChannelParams(
        channel: Int,
        physicalIndex: Int,
        volume: Float,
        pan: Float,
        reverbOn: Boolean,
        reverbMix: Float,
        reverbSize: Float,
        delayOn: Boolean,
        delayMix: Float,
        delayFeedback: Float
    ) = nativeSetChannelParams(
        channel, physicalIndex, volume, pan,
        reverbOn, reverbMix, reverbSize,
        delayOn, delayMix, delayFeedback
    )

    fun getLevel(channel: Int): Float = nativeGetLevel(channel)

    fun startFileRecording(path: String) = nativeStartFileRecording(path)
    fun stopFileRecording() = nativeStopFileRecording()

    private external fun nativeStart(inputDeviceId: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetChannelParams(
        channel: Int, physicalIndex: Int, volume: Float, pan: Float,
        reverbOn: Boolean, reverbMix: Float, reverbSize: Float,
        delayOn: Boolean, delayMix: Float, delayFeedback: Float
    )
    private external fun nativeGetLevel(channel: Int): Float
    private external fun nativeStartFileRecording(path: String)
    private external fun nativeStopFileRecording()
}
