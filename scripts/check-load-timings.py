"""Load timing diagnostics (0342) stay inert without the Telegram log."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'vendor/telegram-android'
src = tree / 'TMessagesProj/src/main/java/org/telegram'
ui = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


loader = (src / 'messenger/ApplicationLoader.java').read_text(encoding='utf-8')
assert loader.index('applicationLoaderInstance = this;') < loader.index('NebulaTimings.processStart();')
for step in ['NebulaLink.init', 'NebulaGlassRuntime.init', 'NebulaMotion.apply']:
    assert f'NebulaTimings.step("{step}")' in loader, step
launch = (src / 'ui/LaunchActivity.java').read_text(encoding='utf-8')
assert launch.index('NebulaStorySchedule.onWake();') < launch.index('NebulaTimings.onResume();')
chat = (src / 'ui/ChatActivity.java').read_text(encoding='utf-8')
create = method(chat, 'public boolean onFragmentCreate() {')
assert create.index('NebulaTimings.chatOpened(currentAccount, classGuid);') < 120

timings = (ui / 'NebulaTimings.java').read_text(encoding='utf-8')
# Без журнала: ни наблюдателей, ни кадрового сторожа, ни проб очереди.
for entry in ['public static void onResume(', 'public static void chatOpened(']:
    body = method(timings, entry)
    assert body.split('{', 1)[1].lstrip().startswith('if (!on()) return;'), entry
assert 'return BuildVars.LOGS_ENABLED;' in method(timings, 'private static boolean on(')
# processStart/step идут до готовности FileLog и не пишут в журнал сами.
assert 'FileLog' not in method(timings, 'public static void step(') + method(timings, 'public static void processStart(')
frames = method(timings, 'private static void watchFrames(')
assert 'mainInterfacePaused || !on()) { framesRunning = false; return; }' in frames
assert 'getStorageQueue().postRunnable' in method(timings, 'private static void probeStorage(')
for banned in ['messageOwner', 'getDialogId', 'dialogId']:
    assert banned not in timings, 'timings must not log chat content or ids: ' + banned

print('Load timings are wired at startup, resume and chat open, and stay inert without the Telegram log')
