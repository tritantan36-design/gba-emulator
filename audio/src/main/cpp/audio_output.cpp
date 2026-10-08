#include "audio_output.h"
namespace gbalite {
bool AudioOutput::start() {
    if (stream_) return true;
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(2)->setSampleRate(32768)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setDataCallback(this);
    if (builder.openStream(stream_) != oboe::Result::OK) { stream_.reset(); return false; }
    if (stream_->requestStart() != oboe::Result::OK) { stop(); return false; }
    return true;
}
void AudioOutput::stop() {
    if (stream_) {
        stream_->requestStop();
        stream_->close(); // close waits for callback completion before ring reset
        stream_.reset();
    }
    ring_.clear();
}
}
