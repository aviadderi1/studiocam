#include "DelayEffect.h"
#include <algorithm>
#include <cmath>

DelayEffect::DelayEffect(int32_t sampleRate) : sampleRate_(sampleRate) {
    // 1 second max delay buffer
    buffer_.assign(static_cast<size_t>(sampleRate_) + 1, 0.0f);
}

void DelayEffect::setParams(float timeMs, float feedback, float mix, bool enabled) {
    float samples = (timeMs / 1000.0f) * static_cast<float>(sampleRate_);
    samples = std::max(1.0f, std::min(samples, static_cast<float>(buffer_.size() - 1)));
    delaySamples_.store(samples);
    feedback_.store(std::max(0.0f, std::min(feedback, 0.9f)));
    mix_.store(std::max(0.0f, std::min(mix, 1.0f)));
    enabled_.store(enabled);
}

float DelayEffect::process(float input) {
    if (!enabled_.load()) {
        return input;
    }
    int32_t size = static_cast<int32_t>(buffer_.size());
    float delaySamples = delaySamples_.load();
    int32_t readIndex = writeIndex_ - static_cast<int32_t>(delaySamples);
    while (readIndex < 0) readIndex += size;
    readIndex %= size;

    float delayed = buffer_[readIndex];
    float fb = feedback_.load();
    float toStore = input + delayed * fb;
    buffer_[writeIndex_] = toStore;

    writeIndex_ = (writeIndex_ + 1) % size;

    float mix = mix_.load();
    return input * (1.0f - mix) + delayed * mix;
}
