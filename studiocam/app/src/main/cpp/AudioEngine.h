#pragma once
#include <oboe/Oboe.h>
#include <memory>
#include <atomic>
#include <cstdio>
#include "DelayEffect.h"
#include "ReverbEffect.h"

struct ChannelDsp {
    std::atomic<int32_t> physicalIndex{0}; // which physical input channel (0 or 1) feeds this logical channel
    std::atomic<float> volume{0.8f};       // 0..1
    std::atomic<float> pan{0.0f};          // -1..1
    std::atomic<float> level{0.0f};        // live peak level, read by UI
    std::unique_ptr<DelayEffect> delay;
    std::unique_ptr<ReverbEffect> reverb;
};

class AudioEngine : public oboe::AudioStreamCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool start(int32_t inputDeviceId);
    void stop();

    void setChannelParams(
        int channel, int physicalIndex, float volume, float pan,
        bool reverbOn, float reverbMix, float reverbSize,
        bool delayOn, float delayMix, float delayFeedback
    );

    float getLevel(int channel);

    void startFileRecording(const char *path);
    void stopFileRecording();

    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *stream, void *audioData, int32_t numFrames
    ) override;

private:
    std::shared_ptr<oboe::AudioStream> inputStream_;
    std::shared_ptr<oboe::AudioStream> outputStream_;

    ChannelDsp channels_[2];
    int32_t sampleRate_ = 48000;

    std::atomic<bool> recording_{false};
    FILE *recordingFile_ = nullptr;
    int64_t recordedFrames_ = 0;

    void writeWavHeaderPlaceholder();
    void finalizeWavHeader();
};
