"""Validate Java/native slot agreement and exercise the actual native singleton switch."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
java = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java').read_text(encoding='utf-8')
defines = (tree / 'TMessagesProj/jni/tgnet/Defines.h').read_text(encoding='utf-8')
cpp = (tree / 'TMessagesProj/jni/tgnet/ConnectionsManager.cpp').read_text(encoding='utf-8')
assert re.search(r'MAX_ACCOUNT_DEFAULT_COUNT\s*=\s*10;', java)
assert re.search(r'MAX_ACCOUNT_COUNT\s*=\s*10;', java)
assert re.search(r'#define MAX_ACCOUNT_COUNT 11\b', defines)
start = cpp.index('ConnectionsManager& ConnectionsManager::getInstance(')
brace = cpp.index('{', start)
depth = 1
end = brace + 1
while depth:
    depth += (cpp[end] == '{') - (cpp[end] == '}')
    end += 1
method = cpp[start:end]
for slot in range(11):
    assert re.search(rf'case {slot}:\s*(?:default:\s*)?static ConnectionsManager instance{slot}\({slot}\);\s*return instance{slot};', method)

source = '''typedef int int32_t;
class ConnectionsManager {
public:
    int id;
    explicit ConnectionsManager(int value): id(value) {}
    static ConnectionsManager& getInstance(int32_t instanceNum);
};
''' + method + '''
int main() {
    for (int i = 0; i < 11; ++i) {
        auto& current = ConnectionsManager::getInstance(i);
        if (current.id != i || &current != &ConnectionsManager::getInstance(i)) return 1;
        for (int j = 0; j < i; ++j)
            if (&current == &ConnectionsManager::getInstance(j)) return 2;
    }
    return 0;
}
'''
work = root / 'build/account-slots-check'
work.mkdir(parents=True, exist_ok=True)
test = work / 'check.cpp'
test.write_text(source, encoding='utf-8')
compiler = os.environ.get('CXX') or shutil.which('g++') or shutil.which('clang++')
if not compiler:
    raise SystemExit('Set CXX to a C++ compiler')
if '--syntax-only' in sys.argv:
    subprocess.run([compiler, '-std=c++11', '-fsyntax-only', str(test)], check=True)
    print('10 Java slots + 11 distinct native cases: contract and native syntax passed')
else:
    executable = work / ('check.exe' if os.name == 'nt' else 'check')
    subprocess.run([compiler, '-std=c++11', str(test), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)
    print('10 Java slots + reserved native slot: all singleton identities and isolation passed')
