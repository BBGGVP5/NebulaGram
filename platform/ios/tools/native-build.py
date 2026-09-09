#!/usr/bin/env python3
"""Prepare a FRESH iOS tree and compile real Telegram integration modules.

No reset/clean, signing, credentials, app packaging or release upload. The
compile-only configuration is deliberately unusable for Telegram login.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
MARKER = '.nebula-ios-build.json'
TARGET = '//submodules/NebulaIntegrationChecks:NebulaIntegrationChecks'



def output(*args, **kwargs):
    return subprocess.check_output(args, text=True, **kwargs).strip()


def run(*args, **kwargs):
    print('+', ' '.join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), check=True, **kwargs)


def pin():
    entry = output('git', '-C', str(ROOT), 'ls-tree', 'HEAD', 'vendor/telegram-ios').split()
    if len(entry) < 3 or entry[0] != '160000':
        raise ValueError('Missing committed Telegram iOS gitlink')
    return entry[2]


def inputs():
    paths = sorted((ROOT / 'patches/ios').glob('*.patch'))
    paths += sorted(p for p in (ROOT / 'platform/ios/overlay').rglob('*') if p.is_file())
    paths.append(Path(__file__).resolve())
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}


def require_fresh(destination):
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise ValueError('Destination must not exist; existing trees are never reset or deleted')
    resolved = destination.resolve()
    if resolved == ROOT or resolved.is_relative_to(ROOT / 'vendor'):
        raise ValueError('Build destination must be separate from the vendor/source checkout')
    return resolved


def copy_overlay(destination, overlay):
    destination, overlay = Path(destination).resolve(), Path(overlay).resolve()
    pairs = []
    for source in sorted(overlay.rglob('*')):
        if source.is_symlink():
            raise ValueError('Overlay symlinks are not supported: ' + str(source))
        if not source.is_file():
            continue
        target = destination / source.relative_to(overlay)
        if not target.resolve().is_relative_to(destination) or target.exists() or target.is_symlink():
            raise ValueError('Overlay collision or path escape: ' + str(target))
        pairs.append((source, target))
    # Validate the entire overlay before writing any file.
    for source, target in pairs:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)


def prepare(destination):
    destination = require_fresh(destination)
    revision = pin()
    url = output('git', '-C', str(ROOT), 'config', '-f', '.gitmodules', '--get', 'submodule.vendor/telegram-ios.url')
    run(sys.executable, ROOT / 'platform/ios/tools/generate-overlay.py', '--check')
    run('git', 'clone', '--filter=blob:none', '--no-checkout', '--depth', '1', url, destination)
    run('git', '-C', destination, 'config', 'core.longpaths', 'true')
    run('git', '-C', destination, 'fetch', '--depth', '1', 'origin', revision)
    run('git', '-C', destination, 'checkout', '--detach', revision)
    run('git', '-C', destination, 'submodule', 'update', '--init', '--recursive', '--depth', '1', '--jobs', '4')
    for patch in sorted((ROOT / 'patches/ios').glob('*.patch')):
        run('git', '-C', destination, 'apply', '--check', patch)
        run('git', '-C', destination, 'apply', patch)
    copy_overlay(destination, ROOT / 'platform/ios/overlay')
    manifest = {'revision': revision, 'inputs': inputs(), 'target': TARGET,
                'configuration': 'debug_sim_arm64', 'purpose': 'compile-only; no login, app or signing'}
    changed = output('git', '-C', str(destination), 'diff', '--name-only').splitlines()
    manifest['native_files'] = {p: hashlib.sha256((destination / p).read_bytes()).hexdigest() for p in changed}
    (destination / MARKER).write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Prepared:', destination, flush=True)


def fixture_configuration():
    return dict(bundle_id='app.nebulagram.compilecheck', api_id='0', api_hash='0' * 32,
                team_id='0000000000', app_center_id='0', is_internal_build='true',
                is_appstore_build='false', appstore_id='0', app_specific_url_scheme='nebulagram-compilecheck',
                premium_iap_product_id='', enable_siri=False, enable_icloud=False)


def select_xcode(version):
    if sys.platform != 'darwin':
        raise ValueError('Native compilation requires macOS, not a syntax-only fallback')
    if platform.machine() != 'arm64':
        raise ValueError('This build targets an Apple Silicon simulator/toolchain')
    applications = sorted(Path('/Applications').glob('Xcode*.app'))
    for app in applications:
        developer = app / 'Contents/Developer'
        env = dict(os.environ, DEVELOPER_DIR=str(developer))
        try:
            actual = output('xcodebuild', '-version', env=env).splitlines()[0]
        except subprocess.CalledProcessError:
            continue
        if actual == 'Xcode ' + version:
            os.environ['DEVELOPER_DIR'] = str(developer)
            print('Using', actual, 'at', developer, flush=True)
            return
    raise ValueError('Pinned Xcode ' + version + ' is unavailable; found: ' + ', '.join(p.name for p in applications))


def build(tree, jobs):
    tree = Path(tree).resolve()
    manifest = json.loads((tree / MARKER).read_text(encoding='utf-8'))
    if manifest['revision'] != pin() or manifest['inputs'] != inputs() or manifest['target'] != TARGET:
        raise ValueError('Prepared tree does not match current pinned sources/overlay; prepare a fresh tree')
    if output('git', '-C', str(tree), 'rev-parse', 'HEAD') != pin():
        raise ValueError('Prepared checkout revision changed')
    expected = dict(manifest['native_files'])
    prefix = 'platform/ios/overlay/'
    expected.update({p[len(prefix):]: digest for p, digest in manifest['inputs'].items() if p.startswith(prefix)})
    for relative, digest in expected.items():
        if hashlib.sha256((tree / relative).read_bytes()).hexdigest() != digest:
            raise ValueError('Prepared source changed: ' + relative)
    versions = json.loads((tree / 'versions.json').read_text(encoding='utf-8'))
    select_xcode(versions['xcode'])
    os.chdir(tree)
    sys.path.insert(0, str(tree / 'build-system/Make'))
    from BazelLocation import locate_bazel
    from Make import BazelCommandLine
    from BuildConfiguration import BuildConfiguration
    bazel = locate_bazel(str(tree), None, None)
    command = BazelCommandLine(bazel=bazel, override_bazel_version=False,
                               override_xcode_version=False, bazel_user_root=str(tree.parent / 'bazel-user-root'))
    config = tree / 'build-input/configuration-repository'
    config.mkdir(parents=True, exist_ok=True)
    (config / 'MODULE.bazel').write_text('module(name = "build_configuration")\n', encoding='utf-8')
    (config / 'WORKSPACE').write_text('', encoding='utf-8')
    (config / 'BUILD').write_text('', encoding='utf-8')
    (config / 'provisioning').mkdir(exist_ok=True)
    (config / 'provisioning/BUILD').write_text('exports_files([])\n', encoding='utf-8')
    BuildConfiguration(**fixture_configuration()).write_to_variables_file(
        bazel_path=bazel, use_xcode_managed_codesigning=False, aps_environment='development',
        path=str(config / 'variables.bzl'))
    command.common_build_args += ['--jobs=' + str(jobs), '--local_resources=memory=HOST_RAM*0.65', '--color=no']
    command.set_configuration('debug_sim_arm64')
    command.set_disable_provisioning_profiles()
    command.set_build_number('1')
    command.set_custom_target(TARGET)
    print('Free disk GiB:', round(shutil.disk_usage(tree).free / 2**30, 1), flush=True)
    result_file = tree / 'nebula-native-result.json'
    manifest.update(status='running', versions=versions)
    result_file.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    try:
        command.invoke_build()
    except BaseException:
        manifest['status'] = 'failed'
        result_file.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        raise
    # No success artifact is written before the actual native compiler completes.
    manifest.update(status='compiled', versions=versions)
    result_file.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('prepare')
    p.add_argument('--destination', required=True, type=Path)
    b = sub.add_parser('build')
    b.add_argument('--tree', required=True, type=Path)
    b.add_argument('--jobs', type=int, choices=range(1, 9), default=2)
    preflight = sub.add_parser('preflight')
    preflight.add_argument('--xcode', default='26.2')
    args = parser.parse_args()
    if args.command == 'prepare': prepare(args.destination)
    elif args.command == 'build': build(args.tree, args.jobs)
    else: select_xcode(args.xcode)


if __name__ == '__main__':
    main()
