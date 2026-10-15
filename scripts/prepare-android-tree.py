#!/usr/bin/env python3
"""Prepare a fresh pinned Android checkout; existing/vendor trees are never reset."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def output(*args):
    return subprocess.check_output(list(map(str, args)), text=True, encoding='utf-8').strip()


def fresh_destination(destination, root=ROOT):
    path = Path(destination)
    if path.exists() or path.is_symlink():
        raise ValueError('Destination must not exist; existing sources are never reset or deleted')
    resolved = path.resolve()
    root = Path(root).resolve()
    if resolved == root or resolved.is_relative_to(root / 'vendor') or root.is_relative_to(resolved):
        raise ValueError('Destination must be separate from the workspace/vendor checkout')
    return resolved


def version(contents):
    values = dict(line.split('=', 1) for line in contents.splitlines() if '=' in line and not line.lstrip().startswith('#'))
    value = values.get('APP_VERSION_NAME', '').strip()
    if value != '13.0.2':
        raise ValueError('Expected Telegram 13.0.2, found ' + repr(value))
    return value


def prepare(destination, source, pin, overlay, patches, aar=None, dependencies=False, root=ROOT, upstream_url=None):
    destination = fresh_destination(destination, root)
    source = Path(source).resolve()
    overlay = Path(overlay).resolve()
    patches = sorted(Path(patches).glob('*.patch'))
    # Inspect immutable objects, not modified working files or injected credentials.
    upstream = subprocess.check_output(['git', '-C', str(source), 'show', pin + ':gradle.properties'])
    upstream_version = version(upstream.decode('utf-8'))
    source_head = output('git', '-C', source, 'rev-parse', 'HEAD')
    source_status = output('git', '-C', source, 'status', '--porcelain')
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['git', 'clone', '--no-hardlinks', '--no-checkout', str(source), str(destination)], check=True)
    for key, value in [('core.autocrlf', 'false'), ('core.eol', 'lf'), ('core.longpaths', 'true')]:
        subprocess.run(['git', '-C', str(destination), 'config', key, value], check=True)
    if upstream_url is not None:
        # A local partial clone may expose cached newer objects without advertising
        # their commit as a ref. Fetch the exact public pin into this new clone.
        subprocess.run(['git', '-C', str(destination), 'remote', 'set-url', 'origin', upstream_url], check=True)
        subprocess.run(['git', '-C', str(destination), '-c', 'http.version=HTTP/1.1', 'fetch', '--depth', '1', 'origin', pin], check=True)
    subprocess.run(['git', '-C', str(destination), 'checkout', '--detach', pin], check=True)
    if dependencies:
        subprocess.run(['git', '-C', str(destination), 'submodule', 'update', '--init', '--recursive', '--depth', '1', '--jobs', '4'], check=True)
    inputs = {}
    for file in sorted(overlay.rglob('*')):
        if file.is_symlink():
            raise ValueError('Overlay symlinks are unsupported: ' + str(file))
        if not file.is_file():
            continue
        relative = file.relative_to(overlay)
        target = destination / relative
        if not target.resolve().is_relative_to(destination):
            raise ValueError('Overlay path escapes destination')
        target.parent.mkdir(parents=True, exist_ok=True)
        data = file.read_bytes()
        # Match the canonical Git checkout rather than Windows editor newlines.
        if b'\0' not in data and file.suffix in {'.java', '.kt', '.xml', '.gradle', '.properties', '.txt', '.sh'}:
            data = data.replace(b'\r\n', b'\n')
        target.write_bytes(data)
        inputs['overlay/' + relative.as_posix()] = hashlib.sha256(data).hexdigest()
    for patch in patches:
        data = patch.read_text(encoding='utf-8').encode('utf-8')
        for check in [True, False]:
            command = ['git', '-C', str(destination), 'apply', '--whitespace=nowarn']
            if check:
                command.append('--check')
            subprocess.run(command + ['-'], input=data, check=True)
        inputs['patches/' + patch.name] = hashlib.sha256(data).hexdigest()
    if aar is not None and Path(aar).is_file():
        target = destination / 'TMessagesProj/libs/nebulalink.aar'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(aar, target)
    assert output('git', '-C', source, 'rev-parse', 'HEAD') == source_head
    assert output('git', '-C', source, 'status', '--porcelain') == source_status
    assert output('git', '-C', destination, 'rev-parse', 'HEAD') == pin
    assert version((destination / 'gradle.properties').read_text(encoding='utf-8')) == upstream_version
    manifest = dict(upstream_revision=pin, telegram_version=upstream_version,
                    patch_count=len(patches), inputs=inputs, dependencies_initialized=dependencies,
                    credentials_injected=False, source_checkout_preserved=True)
    (destination / '.nebula-android-source.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Prepared Telegram', upstream_version, 'with', len(patches), 'patches at', destination)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', required=True, type=Path)
    parser.add_argument('--dependencies', action='store_true', help='Initialize pinned native submodules as well')
    args = parser.parse_args()
    pin = output('git', '-C', ROOT, 'rev-parse', 'HEAD:vendor/telegram-android')
    upstream_url = output('git', '-C', ROOT, 'config', '-f', '.gitmodules', '--get', 'submodule.vendor/telegram-android.url')
    prepare(args.destination, ROOT / 'vendor/telegram-android', pin,
            ROOT / 'platform/android/overlay', ROOT / 'patches/android', ROOT / 'build/nebulalink.aar', args.dependencies,
            upstream_url=upstream_url)


if __name__ == '__main__':
    main()
