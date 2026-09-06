"""Run actual Java REST serialization/transport against deterministic HTTPS fixtures."""
from pathlib import Path
import hashlib, os, subprocess, urllib.request

root = Path(__file__).resolve().parent.parent
work = root / 'build/ai-protocol-check'
work.mkdir(parents=True, exist_ok=True)
sha = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
candidates = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'))
jar = Path(os.environ['JSON_JAR']) if os.environ.get('JSON_JAR') else candidates[0] if candidates else work / 'json-20240303.jar'
if not jar.exists():
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
assert hashlib.sha256(jar.read_bytes()).hexdigest() == sha, 'Unexpected org.json artifact'
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(root / 'tests/android/AiProtocolCheck.java'), str(overlay / 'NebulaAiClient.java'), str(overlay / 'NebulaSettingsSchema.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'AiProtocolCheck'], check=True)
