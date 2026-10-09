"""Run production Java audio formats/transport against offline HTTPS fixtures."""
from pathlib import Path
import hashlib, os, subprocess, urllib.request

root = Path(__file__).resolve().parent.parent
work = root / 'build/audio-protocol-check'
work.mkdir(parents=True, exist_ok=True)
sha = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
candidates = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'))
jar = Path(os.environ['JSON_JAR']) if os.environ.get('JSON_JAR') else candidates[0] if candidates else root / 'build/ai-protocol-check/json-20240303.jar'
if not jar.exists():
    jar = work / 'json-20240303.jar'
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
assert hashlib.sha256(jar.read_bytes()).hexdigest() == sha, 'Unexpected org.json artifact'
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(root / 'tests/android/AudioProtocolCheck.java'), str(overlay / 'NebulaAudioProtocol.java'), str(overlay / 'NebulaAudioClient.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'AudioProtocolCheck'], check=True)

subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(root / 'tests/android/SavedTagsCheck.java'), str(overlay / 'NebulaSavedTagStore.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'SavedTagsCheck'], check=True)
