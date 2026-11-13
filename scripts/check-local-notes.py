"""Run note sync serialisation on the JVM and pin the profile row and native-note split."""
from pathlib import Path
import hashlib, os, subprocess, sys, urllib.request

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work = root / 'build/local-notes-check'
work.mkdir(parents=True, exist_ok=True)
sha = '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
candidates = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'))
jar = Path(os.environ['JSON_JAR']) if os.environ.get('JSON_JAR') else candidates[0] if candidates else work / 'json-20240303.jar'
if not jar.exists():
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
assert hashlib.sha256(jar.read_bytes()).hexdigest() == sha, 'Unexpected org.json artifact'
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(overlay / 'NebulaNoteDocument.java'),
                str(root / 'tests/android/NoteSyncCheck.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'NoteSyncCheck'], check=True)

errors = []
document = (overlay / 'NebulaNoteDocument.java').read_text(encoding='utf-8')
if 'import android' in document or 'org.telegram' in document:
    errors.append('NebulaNoteDocument must stay pure Java')
if 'MARKER = "#NebulaGramNotesV1\\n"' not in document:
    errors.append('notes need their own Saved Messages tag')
settings_marker = (overlay / 'NebulaSyncDocument.java').read_text(encoding='utf-8')
if '#NebulaGramNotesV1' in settings_marker:
    errors.append('settings sync must not read note pages')
notes = (overlay / 'NebulaNotes.java').read_text(encoding='utf-8')
for needle, why in [
    ('"sync_" + id, false', 'note sync must be opt-in'),
    ('"notes_" + id', 'notes must be stored per Telegram account'),
    ('m.out', 'only own Saved Messages pages may be read'),
    ('m.fwd_from != null', 'forwarded pages must be ignored'),
    ('TL_inputPeerSelf', 'pages live in Saved Messages'),
    ('focus_notes', 'contacts must use the native Telegram note'),
    ('user.contact', 'contacts must use the native Telegram note'),
]:
    if needle not in notes:
        errors.append(why)
patch = (root / 'patches/android/0330-profile-local-notes.patch').read_text(encoding='utf-8')
for needle in ['NebulaNotes.row(currentAccount, userId, userInfo != null && userInfo.note != null)',
               'NebulaNotes.row(currentAccount, -chatId, false)', 'position == noteRow || position == nebulaNoteRow',
               'put(++pointer, nebulaNoteRow, sparseIntArray)', 'NebulaNotes.edit(this, currentAccount']:
    if needle not in patch:
        errors.append('0330 lost hook: ' + needle)
if len(sys.argv) > 1:
    profile = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java').read_text(encoding='utf-8')
    if profile.count('nebulaNoteRow') < 9:
        errors.append('prepared ProfileActivity is missing local note rows')
    if 'noteRow = rowCount++' not in profile:
        errors.append('native contact note row must stay')
if errors:
    raise SystemExit('\n'.join(errors))
print('local notes wiring: ok')
