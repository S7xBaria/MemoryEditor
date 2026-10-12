#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "MemEditNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int open_mem(int pid, int flags) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    return open(path, flags);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_memedit_app_MemoryEngine_nativeReadMemory(
        JNIEnv *env, jobject, jint pid, jlong address, jint size) {
    if (size <= 0 || size > 1048576) return nullptr;
    int fd = open_mem(pid, O_RDONLY);
    if (fd < 0) return nullptr;
    std::vector<uint8_t> buffer(size);
    ssize_t bytes = pread64(fd, buffer.data(), size, address);
    close(fd);
    if (bytes <= 0) return nullptr;
    jbyteArray result = env->NewByteArray(bytes);
    env->SetByteArrayRegion(result, 0, bytes, (jbyte *) buffer.data());
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_memedit_app_MemoryEngine_nativeWriteMemory(
        JNIEnv *env, jobject, jint pid, jlong address, jbyteArray data) {
    if (data == nullptr) return JNI_FALSE;
    jsize len = env->GetArrayLength(data);
    if (len <= 0 || len > 4096) return JNI_FALSE;
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    int fd = open_mem(pid, O_RDWR);
    if (fd < 0) {
        env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
        return JNI_FALSE;
    }
    ssize_t written = pwrite64(fd, bytes, len, address);
    close(fd);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return (written == len) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_memedit_app_MemoryEngine_nativeGetMaps(JNIEnv *env, jobject, jint pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *fp = fopen(path, "r");
    if (!fp) return nullptr;
    std::vector<std::string> maps;
    char line[512];
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "rw-p") || strstr(line, "r--p")) {
            maps.push_back(std::string(line));
        }
    }
    fclose(fp);
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray((jsize)maps.size(), stringClass, nullptr);
    for (size_t i = 0; i < maps.size(); i++) {
        env->SetObjectArrayElement(result, (jsize)i, env->NewStringUTF(maps[i].c_str()));
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_memedit_app_MemoryEngine_nativeGetPidFromPackage(
        JNIEnv *env, jobject, jstring packageName) {
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (!pkg) return -1;
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "pidof %s", pkg);
    FILE *fp = popen(cmd, "r");
    if (!fp) {
        env->ReleaseStringUTFChars(packageName, pkg);
        return -1;
    }
    int pid = -1;
    if (fscanf(fp, "%d", &pid) != 1) pid = -1;
    pclose(fp);
    env->ReleaseStringUTFChars(packageName, pkg);
    return pid;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_memedit_app_MemoryEngine_nativeScanInt(
        JNIEnv *env, jobject, jint pid, jlong start, jlong end, jint value) {
    int fd = open_mem(pid, O_RDONLY);
    if (fd < 0) return nullptr;
    std::vector<jlong> results;
    const size_t chunk = 65536;
    std::vector<uint8_t> buffer(chunk);
    for (jlong addr = start; addr < end; addr += (jlong)chunk) {
        size_t toRead = (end - addr) < (jlong)chunk ? (size_t)(end - addr) : chunk;
        ssize_t bytes = pread64(fd, buffer.data(), toRead, addr);
        if (bytes <= 0) continue;
        for (ssize_t i = 0; i <= bytes - 4; i += 4) {
            int32_t v;
            memcpy(&v, buffer.data() + i, 4);
            if (v == value) {
                results.push_back(addr + i);
                if (results.size() >= 10000) break;
            }
        }
        if (results.size() >= 10000) break;
    }
    close(fd);
    jlongArray arr = env->NewLongArray((jsize)results.size());
    if (results.size() > 0) {
        env->SetLongArrayRegion(arr, 0, (jsize)results.size(), (jlong *) results.data());
    }
    return arr;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_memedit_app_MemoryEngine_nativeScanFloat(
        JNIEnv *env, jobject, jint pid, jlong start, jlong end, jfloat value) {
    int fd = open_mem(pid, O_RDONLY);
    if (fd < 0) return nullptr;
    std::vector<jlong> results;
    const size_t chunk = 65536;
    std::vector<uint8_t> buffer(chunk);
    for (jlong addr = start; addr < end; addr += (jlong)chunk) {
        size_t toRead = (end - addr) < (jlong)chunk ? (size_t)(end - addr) : chunk;
        ssize_t bytes = pread64(fd, buffer.data(), toRead, addr);
        if (bytes <= 0) continue;
        for (ssize_t i = 0; i <= bytes - 4; i += 4) {
            float v;
            memcpy(&v, buffer.data() + i, 4);
            if (v == value) {
                results.push_back(addr + i);
                if (results.size() >= 10000) break;
            }
        }
        if (results.size() >= 10000) break;
    }
    close(fd);
    jlongArray arr = env->NewLongArray((jsize)results.size());
    if (results.size() > 0) {
        env->SetLongArrayRegion(arr, 0, (jsize)results.size(), (jlong *) results.data());
    }
    return arr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_memedit_app_MemoryEngine_nativeWriteInt(
        JNIEnv *env, jobject, jint pid, jlong address, jint value) {
    int fd = open_mem(pid, O_RDWR);
    if (fd < 0) return JNI_FALSE;
    ssize_t n = pwrite64(fd, &value, 4, address);
    close(fd);
    return (n == 4) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_memedit_app_MemoryEngine_nativeWriteFloat(
        JNIEnv *env, jobject, jint pid, jlong address, jfloat value) {
    int fd = open_mem(pid, O_RDWR);
    if (fd < 0) return JNI_FALSE;
    ssize_t n = pwrite64(fd, &value, 4, address);
    close(fd);
    return (n == 4) ? JNI_TRUE : JNI_FALSE;
}
