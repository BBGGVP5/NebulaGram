#!/usr/bin/env python3
"""Build a device IPA for user signing. No Apple keys, certificates or profiles.

API configuration is read only from environment and never included in evidence.
This builds the full Telegram application plus Nebula overlay, not a simulator.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import re
import shutil
import struct
import sys
import zipfile

spec = importlib.util.spec_from_file_location('native_build', Path(__file__).with_name('native-build.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)
TARGET = '//Telegram:Telegram'
RULES_PIN = '1791d916de4083388f22e20248d8b010d23f0d6b'
RULES_SOURCE_SHA256 = '900fe828aa2d5f9ac236aec3555cf4f01944a012273ea03a12f9b52e8b601540'


def prepare_unsigned_rules(tree):
    # Only a freshly prepared build tree, never the user's vendor checkout.
    rules = tree / 'build-system/bazel-rules/rules_apple'
    if native.output('git', '-C', str(rules), 'rev-parse', 'HEAD') != RULES_PIN:
        raise ValueError('Unexpected rules_apple revision')
    source = rules / 'apple/internal/ios_rules.bzl'
    if hashlib.sha256(source.read_bytes()).hexdigest() != RULES_SOURCE_SHA256:
        raise ValueError('rules_apple source is modified or already prepared; use a fresh tree')
    patch = native.ROOT / 'platform/ios/build-patches/0001-explicit-unsigned-profile-embedding.patch'
    native.run('git', '-C', rules, 'apply', '--check', patch)
    native.run('git', '-C', rules, 'apply', patch)


def configuration(env, bundle_id):
    api_id, api_hash = env.get('TELEGRAM_APP_ID', ''), env.get('TELEGRAM_APP_HASH', '')
    # The upstream writer interpolates into Starlark strings; validate before writing.
    if not re.fullmatch(r'[1-9][0-9]{0,9}', api_id) or int(api_id) > 2147483647:
        raise ValueError('A valid TELEGRAM_APP_ID repository secret is required')
    if not re.fullmatch(r'[0-9a-fA-F]{32}', api_hash) or api_hash == '0' * 32:
        raise ValueError('A valid TELEGRAM_APP_HASH repository secret is required')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9-]*(?:\.[A-Za-z][A-Za-z0-9-]*)+', bundle_id):
        raise ValueError('Invalid bundle identifier')
    return dict(bundle_id=bundle_id, api_id=api_id, api_hash=api_hash,
                team_id='0000000000', app_center_id='0', is_internal_build='false',
                is_appstore_build='false', appstore_id='0', app_specific_url_scheme='nebulagram',
                premium_iap_product_id='', enable_siri=False, enable_icloud=False)


def check_device_binary(data):
    """Require a thin arm64 executable linked for iOS, not an arm64 simulator."""
    if len(data) < 32:
        raise ValueError('Missing Mach-O executable')
    magic, cpu, _, filetype, ncmds, sizeofcmds, _, _ = struct.unpack_from('<8I', data)
    if magic != 0xfeedfacf or cpu != 0x0100000c or filetype != 2:
        raise ValueError('Expected an arm64 Mach-O executable')
    end = 32 + sizeofcmds
    if end > len(data) or ncmds > 10000:
        raise ValueError('Truncated Mach-O commands')
    offset, device = 32, False
    for _ in range(ncmds):
        if offset + 8 > end: raise ValueError('Truncated Mach-O command')
        cmd, size = struct.unpack_from('<2I', data, offset)
        if size < 8 or offset + size > end: raise ValueError('Invalid Mach-O command')
        if cmd == 0x32:  # LC_BUILD_VERSION: platform 2 is iOS; 7 is iOS simulator.
            if size < 24 or struct.unpack_from('<I', data, offset + 8)[0] != 2:
                raise ValueError('Executable is not linked for an iOS device')
            device = True
        elif cmd == 0x25:  # Legacy LC_VERSION_MIN_IPHONEOS, arm64 only above.
            if size < 16: raise ValueError('Invalid minimum iOS version command')
            device = True
        offset += size
    if offset != end or not device:
        raise ValueError('Missing iOS device platform load command')


def inspect_archive(path, bundle_id):
    """Validate without extracting or rewriting the Bazel-produced archive."""
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate archive entries')
        for name in names:
            p = PurePosixPath(name)
            if p.is_absolute() or '..' in p.parts or '\\' in name:
                raise ValueError('Unsafe archive path')
            if p.name == 'embedded.mobileprovision':
                raise ValueError('User-signing artifact must not embed a provisioning profile')
        roots = [n for n in names if re.fullmatch(r'Payload/[^/]+\.app/Info\.plist', n)]
        if len(roots) != 1: raise ValueError('Expected exactly one Payload application')
        root = roots[0][:-len('Info.plist')]

        def read_info(name):
            if z.getinfo(name).file_size > 4 * 1024 * 1024: raise ValueError('Oversized plist')
            return plistlib.loads(z.read(name))

        def check_executable(prefix, info):
            executable = info.get('CFBundleExecutable', '')
            if not executable or '/' in executable or '\\' in executable or executable in ('.', '..'):
                raise ValueError('Invalid bundle executable name')
            # Mach-O load commands fit in this prefix; do not load entire app binaries into RAM.
            with z.open(prefix + executable) as f: check_device_binary(f.read(1024 * 1024))

        info = read_info(roots[0])
        if info.get('CFBundleIdentifier') != bundle_id or info.get('CFBundleSupportedPlatforms') != ['iPhoneOS']:
            raise ValueError('Unexpected bundle identifier or simulator platform')
        check_executable(root, info)
        notification_services = 0
        for name in names:
            if not name.startswith(root + 'PlugIns/') or not re.search(r'/[^/]+\.appex/Info\.plist$', name):
                continue
            ext = read_info(name)
            if not ext.get('CFBundleIdentifier', '').startswith(bundle_id + '.'):
                raise ValueError('Extension bundle identifier mismatch')
            check_executable(name[:-len('Info.plist')], ext)
            if ext.get('NSExtension', {}).get('NSExtensionPointIdentifier') == 'com.apple.usernotifications.service':
                notification_services += 1
        if notification_services != 1: raise ValueError('Notification Service Extension is required')
    with open(path, 'rb') as f: digest = hashlib.file_digest(f, 'sha256').hexdigest()
    return dict(status='packaged-for-user-signing', target=TARGET, configuration='release_arm64',
                bundle_id=bundle_id, sha256=digest, notification_service_extensions=notification_services,
                requires_user_signing=True, device_tested=False, push_delivery_verified=False)


def build(tree, destination, jobs, bundle_id, build_number):
    config_values = configuration(os.environ, bundle_id)
    if not re.fullmatch(r'[1-9][0-9]{0,9}', build_number): raise ValueError('Invalid build number')
    destination = native.require_fresh(destination)
    tree, _ = native.validate_tree(tree)
    prepare_unsigned_rules(tree)
    versions = json.loads((tree / 'versions.json').read_text(encoding='utf-8'))
    native.select_xcode(versions['xcode'])
    os.chdir(tree)
    sys.path.insert(0, str(tree / 'build-system/Make'))
    from BazelLocation import locate_bazel
    from Make import BazelCommandLine
    from BuildConfiguration import BuildConfiguration
    bazel = locate_bazel(str(tree), None, None)
    command = BazelCommandLine(bazel=bazel, override_bazel_version=False,
                               override_xcode_version=False, bazel_user_root=str(tree.parent / 'bazel-user-root'))
    config = tree / 'build-input/configuration-repository'
    config.mkdir(parents=True, exist_ok=False)
    (config / 'MODULE.bazel').write_text('module(name = "build_configuration")\n', encoding='utf-8')
    (config / 'WORKSPACE').write_text('', encoding='utf-8')
    (config / 'BUILD').write_text('', encoding='utf-8')
    (config / 'provisioning').mkdir()
    (config / 'provisioning/BUILD').write_text('exports_files([])\n', encoding='utf-8')
    variables = config / 'variables.bzl'
    BuildConfiguration(**config_values).write_to_variables_file(
        bazel_path=bazel, use_xcode_managed_codesigning=False, aps_environment='production', path=str(variables))
    variables.chmod(0o600)
    # Verified in pinned rules_apple/codesigning_support.bzl: short-circuits signing
    # before device provisioning-profile validation. Do not invent apple.codesign flags.
    command.common_build_args += ['--features=disable_legacy_signing', '--features=nebula_unsigned_ipa', '--jobs=' + str(jobs),
                                  '--local_resources=memory=HOST_RAM*0.65', '--color=no']
    command.common_args.remove('--verbose_failures')  # Avoid generated configuration command dumps.
    command.set_configuration('release_arm64')
    command.set_disable_provisioning_profiles()
    command.set_build_number(build_number)
    command.set_custom_target(TARGET)
    print('Building full device application; no Apple signing identity is used', flush=True)
    try:
        command.invoke_build()
    finally:
        variables.unlink()  # Only our generated config; never upload it or a build cache.
    archive = tree / 'bazel-bin/Telegram/Telegram.ipa'
    report = inspect_archive(archive, bundle_id)
    report.update(upstream_revision=native.pin(), source_revision=native.output('git', '-C', str(native.ROOT), 'rev-parse', 'HEAD'),
                  versions=versions, build_number=build_number)
    destination.mkdir(parents=True)
    shutil.copyfile(archive, destination / 'NebulaGram-unsigned.ipa')
    (destination / 'build-result.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Validated IPA saved for user signing; installation and APNs are not verified', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preflight', action='store_true')
    parser.add_argument('--tree', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--bundle-id', default='app.nebulagram')
    parser.add_argument('--build-number', default='1')
    parser.add_argument('--jobs', type=int, choices=range(1, 9), default=2)
    args = parser.parse_args()
    if args.preflight:
        configuration(os.environ, args.bundle_id)
        native.select_xcode('26.2')
        print('Device build prerequisites available (no signing or push delivery check)')
    else:
        if not args.tree or not args.output: parser.error('--tree and --output are required')
        build(args.tree, args.output, args.jobs, args.bundle_id, args.build_number)


if __name__ == '__main__': main()
