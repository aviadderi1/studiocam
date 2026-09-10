#pragma once
#include <vector>
#include <atomic>

class DelayEffect {
public:
    explicit DelayEffect(int32_t sampleRate);

    // timeMs: 0..800, feedback: 0..0.9, mix: 0..1
    void setParams(float timeMs, float feedback, float mix, bool enabled);

    float process(float input);

private:
    std::vector<float> buffer_;
    int32_t writeIndex_ = 0;
    int32_t sampleRate_;

    std::atomic<float> delaySamples_{0.0f};
    std::atomic<float> feedback_{0.0f};
    std::atomic<float> mix_{0.0f};
    std::atomic<bool> enabled_{false};
};
