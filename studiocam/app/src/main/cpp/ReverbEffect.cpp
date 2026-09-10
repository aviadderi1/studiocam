#include "ReverbEffect.h"
#include <algorithm>

void CombFilter::init(int32_t delaySamples) {
    buffer_.assign(static_cast<size_t>(delaySamples), 0.0f);
    index_ = 0;
}

float CombFilter::process(float input) {
    if (buffer_.empty()) return input;
    float out = buffer_[index_];
    buffer_[index_] = input + out * feedback_;
    index_ = (index_ + 1) % static_cast<int32_t>(buffer_.size());
    return out;
}

void AllpassFilter::init(int32_t delaySamples) {
    buffer_.assign(static_cast<size_t>(delaySamples), 0.0f);
    index_ = 0;
}

float AllpassFilter::process(float input) {
    if (buffer_.empty()) return input;
    float bufOut = buffer_[index_];
    float out = -input + bufOut;
    buffer_[index_] = input + bufOut * kFeedback;
    index_ = (index_ + 1) % static_cast<int32_t>(buffer_.size());
    return out;
}

ReverbEffect::ReverbEffect(int32_t sampleRate) {
    // Base delay lengths tuned for 44.1kHz (classic Freeverb), scaled to actual rate
    const int32_t combBase[4] = {1557, 1617, 1491, 1422};
    const int32_t allpassBase[2] = {225, 556};
    float scale = static_cast<float>(sampleRate) / 44100.0f;

    for (int i = 0; i < 4; i++) {
        combs_[i].init(std::max(8, static_cast<int32_t>(combBase[i] * scale)));
    }
    for (int i = 0; i < 2; i++) {
        allpass_[i].init(std::max(8, static_cast<int32_t>(allpassBase[i] * scale)));
    }
}

void ReverbEffect::setParams(float size, float mix, bool enabled) {
    size_.store(std::max(0.0f, std::min(size, 1.0f)));
    mix_.store(std::max(0.0f, std::min(mix, 1.0f)));
    enabled_.store(enabled);
}

float ReverbEffect::process(float input) {
    if (!enabled_.load()) {
        return input;
    }
    float feedback = 0.7f + size_.load() * 0.28f; // 0.70 .. 0.98
    float wet = 0.0f;
    for (auto &comb : combs_) {
        comb.setFeedback(feedback);
        wet += comb.process(input);
    }
    wet *= 0.25f;
    for (auto &ap : allpass_) {
        wet = ap.process(wet);
    }

    float mix = mix_.load();
    return input * (1.0f - mix) + wet * mix;
}
