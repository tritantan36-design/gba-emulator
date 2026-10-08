#include "mgba_bridge.h"
#include <jni.h>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <exception>

extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_control(JNIEnv*, jobject, jlong, jint, jint);

namespace {
void exception(JNIEnv* env, const char* message);
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<gbalite::Core>> registry;
jlong nextId = 1;
std::shared_ptr<gbalite::Core> lookup(jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto entry = registry.find(id);
    return entry == registry.end() ? nullptr : entry->second;
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_control(JNIEnv* env, jobject, jlong id, jint command, jint value) {
    try { auto c=lookup(id); return c && c->control(command,value); }
    catch(...) { exception(env,"Native player control failed"); return false; }
}
extern "C" JNIEXPORT jlongArray JNICALL Java_dev_gbalite_mgba_JniBridge_metrics(JNIEnv* env, jobject, jlong id) {
    try {
        auto c=lookup(id); if(!c) return nullptr; auto data=c->metrics();
        auto out=env->NewLongArray(static_cast<jsize>(data.size()));
        if(out) { std::vector<jlong> values(data.begin(),data.end()); env->SetLongArrayRegion(out,0,static_cast<jsize>(values.size()),values.data()); }
        return out;
    } catch(...) { exception(env,"Native metrics failed"); return nullptr; }
}
void exception(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) return;
    auto type = env->FindClass("java/lang/IllegalStateException");
    if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_gbalite_mgba_JniBridge_create(JNIEnv* env, jobject) {
    try {
        auto core = std::make_shared<gbalite::Core>();
        std::lock_guard<std::mutex> lock(registryMutex);
        auto id = nextId++; registry.emplace(id, core); return id;
    } catch (...) { exception(env, "Native core creation failed"); return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_gbalite_mgba_JniBridge_destroy(JNIEnv* env, jobject, jlong id) {
    try {
        std::shared_ptr<gbalite::Core> owned;
        {
            std::lock_guard<std::mutex> lock(registryMutex);
            auto it = registry.find(id);
            if (it == registry.end()) return;
            owned = std::move(it->second); registry.erase(it);
        }
        // Stop even if a concurrent frame copy temporarily retains shared ownership.
        owned->pause();
    } catch (...) { exception(env, "Native core destruction failed"); }
}
extern "C" JNIEXPORT jint JNICALL Java_dev_gbalite_mgba_JniBridge_loadRom(JNIEnv* env, jobject, jlong id, jint fd, jlong length) {
    try { auto c = lookup(id); return c ? c->load(fd, length) : 4; }
    catch (...) { exception(env, "Native ROM load failed"); return 3; }
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_start(JNIEnv* env, jobject, jlong id) {
    try { auto c = lookup(id); return c && c->start(); }
    catch (...) { exception(env, "Native start failed"); return false; }
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_pause(JNIEnv* env, jobject, jlong id) {
    try { auto c = lookup(id); return c && c->pause(); }
    catch (...) { exception(env, "Native pause failed"); return false; }
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_reset(JNIEnv* env, jobject, jlong id) {
    try { auto c = lookup(id); return c && c->reset(); }
    catch (...) { exception(env, "Native reset failed"); return false; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_gbalite_mgba_JniBridge_setButton(JNIEnv* env, jobject, jlong id, jint mask, jboolean down) {
    try { auto c = lookup(id); if (c) c->setButton(static_cast<uint32_t>(mask), down); }
    catch (...) { exception(env, "Native input failed"); }
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_copyFrame(JNIEnv* env, jobject, jlong id, jobject target) {
    try {
        auto c = lookup(id);
        if (!c || !target) return false;
        auto size = env->GetDirectBufferCapacity(target);
        if (size < 240 * 160 * 4) return false;
        return c->copyFrame(env->GetDirectBufferAddress(target), static_cast<size_t>(size));
    } catch (...) { exception(env, "Native frame copy failed"); return false; }
}
#ifdef GBA_TEST_HOOKS
// TEST ONLY: debug builds expose a PCM counter, never a pointer or extra EmulatorCore API.
extern "C" JNIEXPORT jint JNICALL Java_dev_gbalite_mgba_JniBridge_probeReadForTest(JNIEnv*,jobject,jlong id,jint index) {
    auto c=lookup(id);return c?static_cast<jint>(c->probeReadForTest(index)):0;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_gbalite_mgba_JniBridge_nonzeroSamplesForTest(JNIEnv*, jobject, jlong id) {
    auto c = lookup(id); return c ? static_cast<jlong>(c->nonzeroSamples()) : 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_gbalite_mgba_JniBridge_framesForTest(JNIEnv*, jobject, jlong id) {
    auto c = lookup(id); return c ? static_cast<jlong>(c->framesForTest()) : 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_dev_gbalite_mgba_JniBridge_playedForTest(JNIEnv*, jobject, jlong id) {
    auto c = lookup(id); return c ? static_cast<jlong>(c->playedForTest()) : 0;
}
extern "C" JNIEXPORT jint JNICALL Java_dev_gbalite_mgba_JniBridge_activeHandlesForTest(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(registryMutex); return static_cast<jint>(registry.size());
}
#endif
extern "C" JNIEXPORT void JNICALL Java_dev_gbalite_mgba_JniBridge_updatePeripherals(JNIEnv* env,jobject,jlong id,jint x,jint y,jint z,jint light) {
    try { auto c=lookup(id); if(c) c->updatePeripherals(x,y,z,light); }
    catch(...) { exception(env,"Peripheral update failed"); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_gbalite_mgba_JniBridge_configurePeripherals(JNIEnv* env,jobject,jlong id,jint mask) {
    try { auto c=lookup(id); if(c) c->configurePeripherals(mask); }
    catch(...) { exception(env,"Peripheral configuration failed"); }
}
extern "C" JNIEXPORT jlongArray JNICALL Java_dev_gbalite_mgba_JniBridge_peripheralStatus(JNIEnv* env,jobject,jlong id) {
    try { auto c=lookup(id); if(!c) return nullptr; auto s=c->peripheralStatus();
        jlong v[]={s[0],s[1],s[2]}; auto out=env->NewLongArray(3);
        if(out) env->SetLongArrayRegion(out,0,3,v); return out;
    } catch(...) { exception(env,"Peripheral status failed"); return nullptr; }
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_dev_gbalite_mgba_JniBridge_exportBytes(JNIEnv* env, jobject, jlong id, jboolean state) {
    try {
        auto c = lookup(id); if (!c) return nullptr;
        auto bytes = c->exportBytes(state);
        auto result = env->NewByteArray(static_cast<jsize>(bytes.size()));
        if (result && !bytes.empty()) env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<const jbyte*>(bytes.data()));
        return result;
    } catch (...) { exception(env, "Native persistence export failed"); return nullptr; }
}
extern "C" JNIEXPORT jboolean JNICALL Java_dev_gbalite_mgba_JniBridge_importBytes(JNIEnv* env, jobject, jlong id, jbyteArray input, jboolean state) {
    try {
        auto c = lookup(id); if (!c || !input) return false;
        auto count = env->GetArrayLength(input);
        if (count <= 0 || count > (state ? 2 * 1024 * 1024 : 128 * 1024)) return false;
        std::vector<uint8_t> bytes(static_cast<size_t>(count));
        env->GetByteArrayRegion(input, 0, count, reinterpret_cast<jbyte*>(bytes.data()));
        if (env->ExceptionCheck()) return false;
        return c->importBytes(bytes, state);
    } catch (...) { exception(env, "Native persistence import failed"); return false; }
}
