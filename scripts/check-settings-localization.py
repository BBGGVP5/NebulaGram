#!/usr/bin/env python3
"""Validate the real Gradle include patterns against every overlay string resource."""
import fnmatch
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / 'patches/android/0098-settings-localization-inputs.patch'
FILES = ['buildSrc/src/main/kotlin/org/telegram/plugin/TelegramBuildAppPlugin.kt',
         'buildSrc/src/main/kotlin/org/telegram/plugin/TelegramBuildPlugin.kt']
with tempfile.TemporaryDirectory(prefix='nebula-localization-') as folder:
    tree = Path(folder)
    subprocess.run(['git', 'init', '--quiet', str(tree)], check=True)
    for path in FILES:
        target = tree / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(subprocess.check_output(['git', '-C', str(ROOT / 'vendor/telegram-android'), 'show', 'HEAD:' + path]))
    if PATCH.exists():
        subprocess.run(['git', '-C', str(tree), 'apply', str(PATCH)], check=True)
    app = (tree / FILES[0]).read_text(encoding='utf-8')
    library = (tree / FILES[1]).read_text(encoding='utf-8')
    patterns = re.findall(r'include\("([^"]+)"\)', app)
    locale_patterns = re.findall(r'include\("([^"]+)"\)', library)
    res = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res'
    files = list(res.glob('values*/strings*.xml'))
    assert files
    keys = {}
    for file in files:
        relative = file.relative_to(res).as_posix()
        candidate = file.name if file.parent.name == 'values' else relative
        assert any(fnmatch.fnmatchcase(candidate, p) for p in patterns), 'Excluded from binary localization: ' + relative
        if file.parent.name != 'values':
            assert any(fnmatch.fnmatchcase(relative, p) for p in locale_patterns), 'Missing locale discovery: ' + relative
        keys.setdefault(file.parent.name, set()).update(item.attrib['name'] for item in ET.parse(file).getroot() if item.tag == 'string')
    for key in ['NebulaSettings', 'NebulaSectionGeneral', 'NebulaSectionFolders', 'NebulaAppearanceTitle', 'NebulaConnected']:
        assert key in keys['values'] and key in keys['values-ru'], key
    print(f'OK: {len(files)} overlay string files covered by binary localization and locale discovery; RU/EN settings keys present')
