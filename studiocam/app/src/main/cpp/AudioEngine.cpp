#include "AudioEngine.h"
#include <android/log.h>
#include <cstring>
#include <cmath>
#include <algorithm>

#define LOG_TAG "StudioCamAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start(int32_t inputDeviceId) {
    stop();

    oboe::AudioStreamBuilder inputBuilder;
    inputBuilder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setDeviceId(inputDeviceId)
        ->setCallback(this);

    oboe::Result result = inputBuilder.openStream(inputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));
        // retry with shared mode as a fallback (exclusive can fail on some devices)
        inputBuilder.setSharingMode(oboe::SharingMode::Shared);
        result = inputBuilder.openStream(inputStream_);
        if (result != oboe::Result::OK) {
            LOGE("Retry failed too: %s", oboe::convertToText(result));
            return false;
        }
    }
    sampleRate_ = inputStream_->getSampleRate();

    for (auto &ch : channels_) {
        ch.delay = std::make_unique<DelayEffect>(sampleRate_);
        ch.reverb = std::make_unique<ReverbEffect>(sampleRate_);
    }

    oboe::AudioStreamBuilder outputBuilder;
    outputBuilder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(sampleRate_);

    result = outputBuilder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));
        inputStream_->close();
        inputStream_.reset();
        return false;
    }

    outputStream_->requestStart();
    result = inputStream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start input stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Audio engine started, sampleRate=%d", sampleRate_);
    return true;
}

void AudioEngine::stop() {
    stopFileRecording();
    if (inputStream_) {
        inputStream_->requestStop();
        inputStream_->close();
        inputStream_.reset();
    }
    if (outputStream_) {
        outputStream_->requestStop();
        outputStream_->close();
        outputStream_.reset();
    }
}

void AudioEngine::setChannelParams(
    int channel, int physicalIndex, float volume, float pan,
    bool reverbOn, float reverbMix, float reverbSize,
    bool delayOn, float delayMix, float delayFeedback
) {
    if (channel < 0 || channel > 1) return;
    auto &ch = channels_[channel];
    ch.physicalIndex.store(physicalIndex);
    ch.volume.store(std::max(0.0f, std::min(volume, 1.0f)));
    ch.pan.store(std::max(-1.0f, std::min(pan, 1.0f)));
    if (ch.reverb) ch.reverb->setParams(reverbSize, reverbMix, reverbOn);
    if (ch.delay) ch.delay->setParams(350.0f, delayFeedback, delayMix, delayOn);
}

float AudioEngine::getLevel(int channel) {
    if (channel < 0 || channel > 1) return 0.0f;
    return channels_[channel].level.load();
}

void AudioEngine::startFileRecording(const char *path) {
    stopFileRecording();
    recordingFile_ = fopen(path, "wb");
    if (!recordingFile_) {
        LOGE("Could not open recording file: %s", path);
        return;
    }
    recordedFrames_ = 0;
    writeWavHeaderPlaceholder();
    recording_.store(true);
}

void AudioEngine::stopFileRecording() {
    if (recording_.load()) {
        recording_.store(false);
    }
    if (recordingFile_) {
        finalizeWavHeader();
        fclose(recordingFile_);
        recordingFile_ = nullptr;
    }
}

void AudioEngine::writeWavHeaderPlaceholder() {
    // 44-byte placeholder; sizes get patched in on stop
    unsigned char header[44] = {0};
    fwrite(header, 1, 44, recordingFile_);
}

void AudioEngine::finalizeWavHeader() {
    int32_t byteRate = sampleRate_ * 2 /*channels*/ * 2 /*bytes per sample*/;
    int32_t blockAlign = 2 * 2;
    int32_t dataSize = static_cast<int32_t>(recordedFrames_ * 2 * 2);
    int32_t riffSize = 36 + dataSize;

    fseek(recordingFile_, 0, SEEK_SET);
    fwrite("RIFF", 1, 4, recordingFile_);
    fwrite(&riffSize, 4, 1, recordingFile_);
    fwrite("WAVE", 1, 4, recordingFile_);
    fwrite("fmt ", 1, 4, recordingFile_);
    int32_t fmtSize = 16;
    fwrite(&fmtSize, 4, 1, recordingFile_);
    int16_t audioFormat = 1; // PCM
    fwrite(&audioFormat, 2, 1, recordingFile_);
    int16_t numChannels = 2;
    fwrite(&numChannels, 2, 1, recordingFile_);
    fwrite(&sampleRate_, 4, 1, recordingFile_);
    fwrite(&byteRate, 4, 1, recordingFile_);
    int16_t blockAlign16 = static_cast<int16_t>(blockAlign);
    fwrite(&blockAlign16, 2, 1, recordingFile_);
    int16_t bitsPerSample = 16;
    fwrite(&bitsPerSample, 2, 1, recordingFile_);
    fwrite("data", 1, 4, recordingFile_);
    fwrite(&dataSize, 4, 1, recordingFile_);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *stream, void *audioData, int32_t numFrames
) {
    auto *input = static_cast<float *>(audioData);
    static std::vector<float> outBuffer;
    static std::vector<int16_t> pcmBuffer;
    outBuffer.resize(static_cast<size_t>(numFrames) * 2);
    if (recording_.load()) {
        pcmBuffer.resize(static_cast<size_t>(numFrames) * 2);
    }

    float peak[2] = {0.0f, 0.0f};

    for (int32_t i = 0; i < numFrames; i++) {
        float phys0 = input[i * 2 + 0];
        float phys1 = input[i * 2 + 1];
        float physical[2] = {phys0, phys1};

        float mixL = 0.0f;
        float mixR = 0.0f;

        for (int c = 0; c < 2; c++) {
            auto &ch = channels_[c];
            int32_t physIdx = ch.physicalIndex.load();
            float sample = physical[physIdx == 1 ? 1 : 0];

            if (ch.reverb) sample = ch.reverb->process(sample);
            if (ch.delay) sample = ch.delay->process(sample);

            float vol = ch.volume.load();
            sample *= vol;

            float absVal = std::fabs(sample);
            if (absVal > peak[c]) peak[c] = absVal;

            float pan = ch.pan.load();
            float leftGain = pan <= 0.0f ? 1.0f : 1.0f - pan;
            float rightGain = pan >= 0.0f ? 1.0f : 1.0f + pan;

            mixL += sample * leftGain;
            mixR += sample * rightGain;
        }

        mixL = std::max(-1.0f, std::min(mixL, 1.0f));
        mixR = std::max(-1.0f, std::min(mixR, 1.0f));

        outBuffer[i * 2 + 0] = mixL;
        outBuffer[i * 2 + 1] = mixR;

        if (recording_.load()) {
            pcmBuffer[i * 2 + 0] = static_cast<int16_t>(mixL * 32767.0f);
            pcmBuffer[i * 2 + 1] = static_cast<int16_t>(mixR * 32767.0f);
        }
    }

    channels_[0].level.store(peak[0]);
    channels_[1].level.store(peak[1]);

    if (outputStream_) {
        outputStream_->write(outBuffer.data(), numFrames, 0);
    }

    if (recording_.load() && recordingFile_) {
        fwrite(pcmBuffer.data(), sizeof(int16_t), pcmBuffer.size(), recordingFile_);
        recordedFrames_ += numFrames;
    }

    return oboe::DataCallbackResult::Continue;
}
