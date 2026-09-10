#pragma once
#include <vector>
#include <atomic>

// Comb filter used inside the reverb network
class CombFilter {
public:
    void init(int32_t delaySamples);
    void setFeedback(float feedback) { feedback_ = feedback; }
    float process(float input);

private:
    std::vector<float> buffer_;
    int32_t index_ = 0;
    float feedback_ = 0.5f;
};

// Allpass filter used inside the reverb network
class AllpassFilter {
public:
    void init(int32_t delaySamples);
    float process(float input);

private:
    std::vector<float> buffer_;
    int32_t index_ = 0;
    static constexpr float kFeedback = 0.5f;
};

// Simplified Freeverb-style mono reverb: 4 parallel combs + 2 series allpass
class ReverbEffect {
public:
    explicit ReverbEffect(int32_t sampleRate);

    // size: 0..1 (room size -> comb feedback), mix: 0..1
    void setParams(float size, float mix, bool enabled);

    float process(float input);

private:
    CombFilter combs_[4];
    AllpassFilter allpass_[2];

    std::atomic<float> size_{0.5f};
    std::atomic<float> mix_{0.3f};
    std::atomic<bool> enabled_{false};
};
