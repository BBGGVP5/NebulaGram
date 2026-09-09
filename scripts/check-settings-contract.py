#!/usr/bin/env python3
"""Contract drift/default/type/legacy-compatibility checks, without an Android SDK."""
import copy
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location('contract', ROOT / 'scripts/generate-settings-contract.py')
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)
doc = json.loads(contract.CATALOG.read_text(encoding='utf-8'))
contract.validate_catalog(doc)
subprocess.run([sys.executable, str(ROOT / 'scripts/generate-settings-contract.py'), '--check'], check=True)
base = contract.JAVA.parent
entries = {row['key']: row for row in doc['settings']}
java_types = {'boolean': 'Boolean', 'integer': 'Integer', 'string': 'String'}
legacy = json.loads((ROOT / 'shared/settings/legacy-v1-types.json').read_text(encoding='utf-8'))
transfer = {key: java_types[row['type']] for key, row in entries.items() if row['transfer_v1']}
assert all(transfer.get(key) == value for key, value in legacy.items())
assert set(transfer) - set(legacy) == {'glass_quality'}
assert transfer['glass_quality'] == 'Integer'
assert not any(row['ios_status'] == 'implemented' for row in entries.values()), 'Add native iOS acceptance tests before promoting status'
bindings = 0
for row in entries.values():
    source = (base / row['android_source']).read_text(encoding='utf-8')
    if row.get('literal_default_binding'):
        method = {'boolean': 'Boolean', 'integer': 'Int', 'string': 'String'}[row['type']]
        literal = json.dumps(row['default'])
        pattern = r'get' + method + r'\("' + re.escape(row['key']) + r'",\s*' + re.escape(literal) + r'\s*\)'
        assert re.search(pattern, source), 'Default drift: ' + row['key']
        bindings += 1


def rejects(fn):
    try:
        fn()
    except ValueError:
        return
    raise AssertionError('Invalid value/catalog accepted')


for value in (False, -1, 101, 1.5, '40', None):
    rejects(lambda value=value: contract.validate_value(entries['avatar_round'], value))
contract.validate_value(entries['avatar_round'], 0)
contract.validate_value(entries['avatar_round'], 100)
rejects(lambda: contract.validate_value(entries['centered_chat_header'], 1))
rejects(lambda: contract.validate_value(entries['bottom_bar_order'], 'рџ«§' * 513))
for mutate in (
    lambda d: d['settings'].append(copy.deepcopy(d['settings'][0])),
    lambda d: d.update(schema_version=2),
    lambda d: d['settings'][0].update(android_store='credentials'),
    lambda d: d['settings'][0].update(ios_status='maybe'),
    lambda d: d['settings'][0].update(default=9),
    lambda d: d['settings'][0].update(type='integer', min=100, max=1),
):
    damaged = copy.deepcopy(doc)
    mutate(damaged)
    rejects(lambda: contract.validate_catalog(damaged))

# Compile the actual generated Java class; assert the live transfer map stays immutable.
with tempfile.TemporaryDirectory(prefix='nebula-contract-') as tmp:
    tmp = Path(tmp)
    checks = '\n'.join(f'if (NebulaSettingsSchema.types.get("{key}") != {typ}.class) throw new AssertionError("{key}");' for key, typ in sorted(legacy.items()))
    source = 'import app.nebulagram.ui.NebulaSettingsSchema; class ContractCheck { public static void main(String[] args) {' + checks
    source += f'if (NebulaSettingsSchema.types.size() != {len(transfer)}) throw new AssertionError();'
    source += 'try { NebulaSettingsSchema.types.put("api_key", String.class); throw new AssertionError(); } catch (UnsupportedOperationException expected) {} }}'
    java = tmp / 'ContractCheck.java'
    java.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp), str(contract.JAVA), str(java)], check=True)
    subprocess.run(['java', '-cp', str(tmp), 'ContractCheck'], check=True)
print(f'{len(entries)} definitions, {bindings} direct default bindings, {len(legacy)} unchanged transfer keys; invalid data and immutable Java map passed')
