#pragma once
#include <deque>
#include <vector>
#include <cstdint>
#include <cstddef>
namespace gbalite {
struct RewindSnapshot {
    std::vector<uint8_t> state;
    std::vector<uint32_t> frame;
    size_t bytes() const { return state.size() + frame.size()*sizeof(uint32_t); }
};
class RewindRing {
    std::deque<RewindSnapshot> entries_;
    size_t bytes_ = 0;
    size_t limit_ = cap;
public:
    static constexpr size_t cap = 64*1024*1024;
    void clear() { entries_.clear(); bytes_=0; }
    void trim() { clear(); limit_=cap/4; }
    void push(RewindSnapshot snapshot) {
        auto n=snapshot.bytes(); if(n>limit_) return;
        while(!entries_.empty() && (bytes_+n>limit_ || entries_.size()>=150)) {
            bytes_-=entries_.front().bytes(); entries_.pop_front();
        }
        bytes_+=n; entries_.push_back(std::move(snapshot));
    }
    bool pop(RewindSnapshot& out) {
        if(entries_.empty()) return false;
        bytes_-=entries_.back().bytes(); out=std::move(entries_.back()); entries_.pop_back(); return true;
    }
    size_t size() const { return entries_.size(); }
    size_t bytes() const { return bytes_; }
};
}
