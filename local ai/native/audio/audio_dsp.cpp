#include <vector>
#include <cmath>
#include <cstdint>

/**
 * High-performance audio DSP routines: Voice Activity Detection (VAD) energy calculation
 * and sliding window RMS for real-time visualization.
 */
float computeRmsAmplitude(const int16_t *samples, size_t count) {
    if (count == 0) return 0.0f;
    double sum = 0.0;
    for (size_t i = 0; i < count; ++i) {
        sum += static_cast<double>(samples[i]) * samples[i];
    }
    double mean = sum / count;
    return static_cast<float>(std::sqrt(mean) / 32768.0);
}

