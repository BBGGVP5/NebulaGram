"""Scoped latency-format and live-probe contracts; no mobile SDK build."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
JAVA = HERE.parent / 'TMessagesProj/src/main/java/app/nebulagram'
UI = JAVA / 'ui'
IOS = ROOT / 'platform/ios/overlay/submodules/NebulaLinkUI/Sources'


def read(path):
    return path.read_text(encoding='utf-8')


servers = read(UI / 'NebulaServersFragment.java')
session = read(UI / 'NebulaProbeSession.java')
controller = read(IOS / 'NebulaLinkController.swift')
for name in ('NebulaServersFragment.java', 'NebulaConnectFragment.java', 'NebulaConnectionCard.java'):
    source = read(UI / name)
    assert 'NebulaLatency.format(' in source
    assert 'optString("latency_method")' in source and 'optLong("checked_at")' in source
    assert 'ping_type' not in source, 'cached measurements must retain their method'
assert 'NebulaProbeSession.start(ids)' in servers
assert 'NebulaProbeSession.removeListener(probeListener)' in servers
assert 'progress.optString("latency_method")' in servers
assert 'progress.optLong("checked_at")' in servers
assert 'request_id' in session and 'probe.cancel' in session
assert '≈' not in servers and '≈' not in read(UI / 'NebulaMenuFragment.java')
assert 'NebulaLatency.format(' in controller
assert '≈' not in controller and 'Nimbo Ping ·' not in controller
for locale in ('values', 'values-ru'):
    tree = ET.parse(HERE.parent / 'TMessagesProj/src/main/res' / locale / 'strings_nebula_menu.xml')
    values = {node.attrib['name']: node.text for node in tree.getroot()}
    assert values['nl_ping_nimbo'] == 'Nimbo Ping'

json_jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'))
if os.environ.get('JSON_JAR'):
    json_jars = [Path(os.environ['JSON_JAR'])]
assert json_jars, 'Set JSON_JAR to a local org.json jar'
with tempfile.TemporaryDirectory(prefix='.nimbo-test-', dir=HERE) as temporary:
    out = Path(temporary).resolve()
    assert out.is_relative_to(HERE)
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(json_jars[0]), '-d', str(out),
                    str(UI / 'NebulaLatency.java'), str(JAVA / 'nebulalink/NebulaCommandQueue.java'),
                    str(HERE / 'NebulaLatencyTest.java'), str(HERE / 'NebulaProbeQueueTest.java')], check=True)
    for name in ('NebulaLatencyTest', 'app.nebulagram.nebulalink.NebulaProbeQueueTest'):
        subprocess.run(['java', '-cp', os.pathsep.join((str(out), str(json_jars[0]))), name], check=True, timeout=15)
    if shutil.which('swiftc'):
        executable = out / ('swift-latency.exe' if os.name == 'nt' else 'swift-latency')
        subprocess.run(['swiftc', str(IOS / 'NebulaLatency.swift'),
                        str(ROOT / 'platform/ios/overlay/tests/NebulaLatencyTests.swift'), '-o', str(executable)], check=True)
        subprocess.run([str(executable)], check=True)
    else:
        print('SKIP: Swift compiler unavailable; iOS latency test runs in macOS CI')
print('PASS: concise Android/iOS ping labels, stored measurement provenance and probe lifecycle')
