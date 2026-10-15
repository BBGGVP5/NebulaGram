"""Exercise the actual native account getter and JNI thread-local storage."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
base = tree / 'TMessagesProj'
java = (base / 'src/main/java/org/telegram/messenger/UserConfig.java').read_text(encoding='utf-8')
cpp = (base / 'jni/tgnet/ConnectionsManager.cpp').read_text(encoding='utf-8')
wrapper = (base / 'jni/TgNetWrapper.cpp').read_text(encoding='utf-8')
header = (base / 'jni/tgnet/ConnectionsManager.h').read_text(encoding='utf-8')
defines = (base / 'jni/tgnet/Defines.h').read_text(encoding='utf-8')
# Keep the UI gate until persisted IDs and notification routing migrate.
assert re.search(r'MAX_ACCOUNT_COUNT\s*=\s*10;', java)
assert '#define MAX_ACCOUNT_COUNT' not in defines
assert 'extern thread_local JNIEnv *jniEnv;' in header
assert 'jniEnv[' not in cpp + wrapper + header
assert 'AttachCurrentThread(&jniEnv, nullptr)' in cpp
assert 'DetachCurrentThread();' in cpp
assert 'for (int a = 0; a < MAX_ACCOUNT_COUNT' not in wrapper
start = cpp.index('ConnectionsManager& ConnectionsManager::getInstance(')
brace = cpp.index('{', start)
depth, end = 1, brace + 1
while depth:
    depth += (cpp[end] == '{') - (cpp[end] == '}')
    end += 1
method = cpp[start:end]
assert 'instanceNum < 0' in method and 'std::abort()' in method
tls = re.search(r'^thread_local JNIEnv \*jniEnv = nullptr;', cpp, re.M).group()
source = r'''
#include <map>
#include <memory>
#include <mutex>
#include <cstdlib>
#include <cstdint>
#include <atomic>
#include <thread>
#include <vector>
#include <cassert>
#define ANDROID
struct JNIEnv { int owner; };
struct ConnectiosManagerDelegate {};
static std::atomic<int> creations(0), delegates(0);
ConnectiosManagerDelegate *createJavaConnectionsDelegate() {
    ++delegates;
    return new ConnectiosManagerDelegate();
}
class ConnectionsManager {
public:
    int id;
    ConnectiosManagerDelegate *delegate = nullptr;
    explicit ConnectionsManager(int value): id(value) { ++creations; }
    void setDelegate(ConnectiosManagerDelegate *value) { delegate = value; }
    static ConnectionsManager& getInstance(int32_t instanceNum);
};
''' + tls + '\n' + method + r'''
int main() {
    std::vector<std::thread> threads;
    std::atomic<bool> start(false);
    assert(creations == 0 && jniEnv == nullptr);
    for (int t = 0; t < 16; ++t) threads.emplace_back([t, &start] {
        JNIEnv local{t};
        assert(jniEnv == nullptr);
        jniEnv = &local;
        while (!start.load()) std::this_thread::yield();
        for (int round = 0; round < 8; ++round) for (int id = 0; id < 128; ++id) {
            auto &instance = ConnectionsManager::getInstance(id);
            assert(instance.id == id && instance.delegate != nullptr);
            assert(&instance == &ConnectionsManager::getInstance(id));
            assert(jniEnv == &local && jniEnv->owner == t);
        }
    });
    start = true;
    for (auto &thread : threads) thread.join();
    assert(jniEnv == nullptr && creations == 128 && delegates == 128);
    for (int i = 0; i < 128; ++i) for (int j = 0; j < i; ++j)
        assert(&ConnectionsManager::getInstance(i) != &ConnectionsManager::getInstance(j));
    assert(ConnectionsManager::getInstance(1000000).id == 1000000);
    assert(ConnectionsManager::getInstance(INT32_MAX).id == INT32_MAX);
    assert(creations == 130 && delegates == 130);
}
'''
compiler = os.environ.get('CXX') or shutil.which('g++') or shutil.which('clang++')
if not compiler:
    raise SystemExit('Set CXX to a C++ compiler')
with tempfile.TemporaryDirectory(prefix='nebula-native-accounts-') as directory:
    work = Path(directory)
    test = work / 'check.cpp'
    test.write_text(source, encoding='utf-8')
    if '--syntax-only' in sys.argv:
        target = ['--target=aarch64-linux-android23'] if '/ndk/' in compiler.replace('\\', '/') else []
        subprocess.run([compiler, *target, '-std=c++11', '-fsyntax-only', str(test)], check=True)
        print('Dynamic native registry/JNI contract and syntax passed; execution not performed')
    else:
        executable = work / ('check.exe' if os.name == 'nt' else 'check')
        subprocess.run([compiler, '-std=c++11', '-pthread', str(test), '-o', str(executable)], check=True)
        subprocess.run([str(executable)], check=True, timeout=45)
        print('Native registry: 128 isolated accounts, sparse IDs, concurrent identity/delegates and JNI thread ownership passed')
