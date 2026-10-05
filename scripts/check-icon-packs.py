"""Execute the bounded .icons archive reader against valid, malformed and oversized fixtures."""
from pathlib import Path
import hashlib, json, os, subprocess, warnings, zipfile

root = Path(__file__).resolve().parents[1]
work = root / 'build/icon-pack-check'
work.mkdir(parents=True, exist_ok=True)
jar = root / 'build/ai-protocol-check/json-20240303.jar'
if not jar.exists():
    import urllib.request
    jar.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
assert hashlib.sha256(jar.read_bytes()).hexdigest() == '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
svg = b'<svg width="24" height="24" viewBox="0 0 24 24"><path fill="#fff" d="M2 2h20v20H2z"/></svg>'
meta = {'schemaVersion': 1, 'packId': 'example', 'packName': 'Example', 'author': 'Test', 'version': '1', 'icons': {'msg_search': 'icons/search.svg'}}
fixtures = []
def fixture(name, expected, entries=None, metadata=None):
    path = work / (name + '.icons')
    with warnings.catch_warnings(), zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        warnings.simplefilter('ignore', UserWarning)
        z.writestr('metadata.json', json.dumps(meta if metadata is None else metadata))
        for key, value in (entries if entries is not None else [('icons/search.svg', svg)]):
            z.writestr(key, value)
    fixtures.extend([str(path), str(expected).lower()])
fixture('valid', True)
fixture('traversal', False, [('icons/search.svg', svg), ('../outside', b'x')])
fixture('absolute', False, [('/icons/search.svg', svg)])
fixture('windows', False, [('C:\\icons\\search.svg', svg)])
fixture('duplicate', False, [('icons/search.svg', svg), ('icons/search.svg', svg)])
fixture('missing', False, [])
fixture('large-icon', False, [('icons/search.svg', b'x' * 262145)])
for name, bad in [('script', b'<script>run()</script>'), ('entity', b'<!DOCTYPE svg [<!ENTITY x SYSTEM "file:///private">]>'), ('external', b'<image href="https://example.com/a"/>'), ('style', b'<path fill="url(https://example.com/a)"/>'), ('event-handler', b'<path onclick="run()"/>')]:
    fixture(name, False, [('icons/search.svg', b'<svg>' + bad + b'</svg>')])
fixture('metadata-version', False, metadata={**meta, 'schemaVersion': 2})
fixture('resource-name', False, metadata={**meta, 'icons': {'../resource': 'icons/search.svg'}})
fixture('long-id', False, metadata={**meta, 'packId': 'x' * 81})
fixture('too-many-icons', False, metadata={**meta, 'icons': {f'msg_{i}': 'icons/search.svg' for i in range(513)}})
fixture('bad-png', False, [('icons/search.png', b'not png')], {**meta, 'icons': {'msg_search': 'icons/search.png'}})
png = b'\x89PNG\r\n\x1a\n' + b'\x00' * 8 + (2048).to_bytes(4, 'big') + (2048).to_bytes(4, 'big')
fixture('huge-png', False, [('icons/search.png', png)], {**meta, 'icons': {'msg_search': 'icons/search.png'}})
fixture('bad-webp', False, [('icons/search.webp', b'not webp')], {**meta, 'icons': {'msg_search': 'icons/search.webp'}})
fixture('too-many-entries', False, [('icons/search.svg', svg)] + [(f'ignored/{i}', b'x') for i in range(2048)])
fixture('zip-bomb', False, [('icons/search.svg', svg)] + [(f'ignored/{i}', b'x' * 262144) for i in range(123)])
bundled = root / 'platform/android/overlay/TMessagesProj/src/main/assets/nebulagram/remix-outline.icons'
assert bundled.is_file()
fixtures.extend([str(bundled), 'true'])
test = work / 'IconPackCheck.java'
test.write_text('''import app.nebulagram.ui.NebulaIconArchive;import java.io.*;
public class IconPackCheck {public static void main(String[] args)throws Exception{
int cases=0;for(int i=0;i<args.length;i+=2){boolean expected=Boolean.parseBoolean(args[i+1]),accepted=false;
try(FileInputStream in=new FileInputStream(args[i])){NebulaIconArchive pack=NebulaIconArchive.read(in);accepted=true;if(pack.icons.isEmpty())throw new AssertionError("empty pack");
try{pack.icons.clear();throw new AssertionError("mutable icon map");}catch(UnsupportedOperationException ok){}}
catch(Exception rejected){} if(expected!=accepted)throw new AssertionError(args[i]+" expected "+expected);cases++;}
System.out.println("Icon archive: "+cases+" fixtures passed, including the bundled Remix pack");}}
''', encoding='utf-8')
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(test), str(ui / 'NebulaIconArchive.java')], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'IconPackCheck', *fixtures], check=True)
