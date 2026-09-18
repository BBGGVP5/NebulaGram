"""Compile changed overlays against an existing Android build, without rebuilding it.

Optional first argument: prepared Telegram tree with compiled standalone classes.
The only generated stub is R for the two new string resources; all Android,
Telegram and gomobile classes come from local SDK/build/dependency caches.
Temporary compiler output stays beneath this tests directory and is removed.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
TREE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'build/final-verify-0904/tree'
ANDROID = HERE.parent / 'TMessagesProj/src/main'
BASE = ANDROID / 'java/app/nebulagram'
names = ['NebulaLatency', 'NebulaConnectFragment', 'NebulaConnectionCard',
         'NebulaLinkShortcut', 'NebulaMenuFragment', 'NebulaServersFragment']
sources = [BASE / 'ui' / (name + '.java') for name in names] + [BASE / 'nebulalink/NebulaLink.java']
classes = TREE / 'TMessagesProj/build/intermediates/javac/standalone/compileStandaloneJavaWithJavac/classes'
symbols = TREE / 'TMessagesProj/build/intermediates/compile_symbol_list/standalone/generateStandaloneRFile/R.txt'
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'AppData/Local/Android/Sdk')))
android_jar = sdk / 'platforms/android-35/android.jar'
assert classes.is_dir() and symbols.exists() and android_jar.exists(), 'Existing standalone build and Android 35 SDK required'

resource_values = {}
for line in symbols.read_text().splitlines():
    parts = line.split()
    if len(parts) == 4 and parts[0] == 'int':
        resource_values[(parts[1], parts[2])] = parts[3]
new_strings = {node.attrib['name'] for node in ET.parse(ANDROID / 'res/values/strings_nebula_menu.xml').getroot()}
needed = set()
for source in sources:
    needed.update(re.findall(r'\bR\.(\w+)\.(\w+)', source.read_text(encoding='utf-8')))
groups = {}
for kind, name in sorted(needed):
    value = resource_values.get((kind, name))
    if value is None:
        assert kind == 'string' and name in new_strings, f'Unresolved resource {kind}/{name}'
        value = '0x7f7f0000' if name == 'nl_ping_nimbo' else '0x7f7f0001'
    groups.setdefault(kind, []).append(f'public static final int {name} = {value};')
r_source = 'package org.telegram.messenger; public final class R {\n' + '\n'.join(
    'public static final class ' + kind + ' {\n' + '\n'.join(fields) + '\n}' for kind, fields in groups.items()) + '\n}'

cache = Path.home() / '.gradle/caches'
dependencies = sorted(p for p in cache.rglob('*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
with tempfile.TemporaryDirectory(prefix='.nimbo-compile-', dir=HERE) as temporary:
    out = Path(temporary).resolve()
    assert out.is_relative_to(HERE)
    r_file = out / 'R.java'
    r_file.write_text(r_source, encoding='utf-8')
    args = ['-encoding', 'UTF-8', '-proc:none', '-implicit:none', '-d', out.as_posix(),
            '-sourcepath', out.as_posix(), '-cp', os.pathsep.join(p.as_posix() for p in [out, android_jar, classes, *dependencies]),
            r_file.as_posix(), *(p.as_posix() for p in sources)]
    arg_file = out / 'javac.args'
    arg_file.write_text('\n'.join('"' + value + '"' for value in args), encoding='utf-8')
    subprocess.run(['javac', '@' + str(arg_file)], check=True)
print('PASS: 7 changed Android Java classes compile against existing Telegram/Android/gomobile classes; resource names verified')
