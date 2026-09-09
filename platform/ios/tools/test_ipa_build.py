"""Portable IPA preflight tests; synthetic archives are not device tests."""
import importlib.util
import json
import os
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import zipfile

spec = importlib.util.spec_from_file_location('ipa_build', Path(__file__).with_name('ipa-build.py'))
ipa = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ipa)


def binary(platform=2, cpu=0x0100000c):
    return struct.pack('<8I', 0xfeedfacf, cpu, 0, 2, 1, 24, 0, 0) + struct.pack('<6I', 0x32, 24, platform, 0, 0, 0)


def archive(path, *, platform=2, extension=True, provisioned=False, traversal=False):
    with zipfile.ZipFile(path, 'w') as z:
        root = 'Payload/Telegram.app/'
        info = {'CFBundleIdentifier': 'app.nebulagram', 'CFBundleExecutable': 'Telegram',
                'CFBundleSupportedPlatforms': ['iPhoneOS'], 'CFBundlePackageType': 'APPL'}
        z.writestr(root + 'Info.plist', plistlib.dumps(info))
        z.writestr(root + 'Telegram', binary(platform))
        if extension:
            ext = root + 'PlugIns/NotificationService.appex/'
            z.writestr(ext + 'Info.plist', plistlib.dumps({
                'CFBundleIdentifier': 'app.nebulagram.NotificationService', 'CFBundleExecutable': 'NSE',
                'NSExtension': {'NSExtensionPointIdentifier': 'com.apple.usernotifications.service'}}))
            z.writestr(ext + 'NSE', binary(platform))
        if provisioned: z.writestr(root + 'embedded.mobileprovision', b'not-a-profile')
        if traversal: z.writestr('../outside', b'no')


class IpaBuildTests(unittest.TestCase):
    def test_full_build_uses_release_device_target_and_cleans_configuration(self):
        for fail in [False, True]:
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as work:
                tree, dest = Path(work) / 'tree', Path(work) / 'artifact'
                tree.mkdir()
                (tree / 'versions.json').write_text('{"xcode":"26.2","bazel":"8.4.2"}', encoding='utf-8')
                command = SimpleNamespace(common_args=['--verbose_failures'], common_build_args=[])
                command.set_configuration = lambda value: setattr(command, 'configuration', value)
                command.set_disable_provisioning_profiles = lambda: setattr(command, 'profiles_disabled', True)
                command.set_build_number = lambda value: setattr(command, 'build_number', value)
                command.set_custom_target = lambda value: setattr(command, 'target', value)
                def invoke():
                    self.assertTrue((tree / 'build-input/configuration-repository/variables.bzl').exists())
                    if fail: raise RuntimeError('synthetic compiler failure')
                    out = tree / 'bazel-bin/Telegram/Telegram.ipa'
                    out.parent.mkdir(parents=True)
                    archive(out)
                command.invoke_build = invoke
                class Config:
                    def __init__(self, **kwargs): pass
                    def write_to_variables_file(self, **kwargs):
                        Path(kwargs['path']).write_text('private config', encoding='utf-8')
                modules = {'BazelLocation': SimpleNamespace(locate_bazel=lambda *a: '/bazel'),
                           'Make': SimpleNamespace(BazelCommandLine=lambda **kw: command),
                           'BuildConfiguration': SimpleNamespace(BuildConfiguration=Config)}
                env = {'TELEGRAM_APP_ID': '123', 'TELEGRAM_APP_HASH': 'ab' * 16}
                cwd = Path.cwd()
                try:
                    with patch.dict(os.environ, env), patch.dict('sys.modules', modules), \
                         patch.object(ipa.native, 'validate_tree', return_value=(tree, {})), \
                         patch.object(ipa.native, 'select_xcode'), patch.object(ipa.native, 'pin', return_value='pinned'), \
                         patch.object(ipa.native, 'output', return_value='source'):
                        if fail:
                            with self.assertRaises(RuntimeError): ipa.build(tree, dest, 2, 'app.nebulagram', '1')
                        else: ipa.build(tree, dest, 2, 'app.nebulagram', '1')
                finally: os.chdir(cwd)
                self.assertEqual(command.target, '//Telegram:Telegram')
                self.assertEqual(command.configuration, 'release_arm64')
                self.assertTrue(command.profiles_disabled)
                self.assertIn('--features=disable_legacy_signing', command.common_build_args)
                self.assertNotIn('--verbose_failures', command.common_args)
                self.assertFalse((tree / 'build-input/configuration-repository/variables.bzl').exists())
                self.assertEqual(dest.exists(), not fail)

    def test_credentials_are_required_and_never_fixture_login(self):
        for env in [{}, {'TELEGRAM_APP_ID': '0', 'TELEGRAM_APP_HASH': '0' * 32},
                    {'TELEGRAM_APP_ID': '123', 'TELEGRAM_APP_HASH': 'bad"value'}]:
            with self.assertRaises(ValueError): ipa.configuration(env, 'app.nebulagram')
        config = ipa.configuration({'TELEGRAM_APP_ID': '123', 'TELEGRAM_APP_HASH': 'ab' * 16}, 'app.nebulagram')
        self.assertEqual(config['api_id'], '123')
        self.assertEqual(config['bundle_id'], 'app.nebulagram')
        self.assertFalse(config['enable_icloud'])
        self.assertEqual(config['is_internal_build'], 'false')

    def test_configuration_rejects_code_injection(self):
        env = {'TELEGRAM_APP_ID': '123', 'TELEGRAM_APP_HASH': 'ab' * 16}
        for bundle in ['app."bad', 'app/bad', 'single', 'app..bad']:
            with self.assertRaises(ValueError): ipa.configuration(env, bundle)

    def test_device_macho_not_simulator_or_other_architecture(self):
        ipa.check_device_binary(binary())
        for data in [binary(7), binary(cpu=0x01000007), b'not MachO', binary()[:40]]:
            with self.assertRaises(ValueError): ipa.check_device_binary(data)

    def test_valid_archive_has_nse_and_no_delivery_claim(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / 'app.ipa'
            archive(path)
            report = ipa.inspect_archive(path, 'app.nebulagram')
            self.assertEqual(report['notification_service_extensions'], 1)
            self.assertFalse(report['device_tested'])
            self.assertFalse(report['push_delivery_verified'])
            self.assertTrue(report['requires_user_signing'])
            self.assertEqual(len(report['sha256']), 64)
            self.assertNotIn('api_hash', json.dumps(report))

    def test_invalid_archives_never_publish(self):
        for options in [dict(platform=7), dict(extension=False), dict(provisioned=True), dict(traversal=True)]:
            with self.subTest(options=options), tempfile.TemporaryDirectory() as work:
                path = Path(work) / 'app.ipa'
                archive(path, **options)
                with self.assertRaises(ValueError): ipa.inspect_archive(path, 'app.nebulagram')

    def test_bundle_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as work:
            path = Path(work) / 'app.ipa'
            archive(path)
            with self.assertRaises(ValueError): ipa.inspect_archive(path, 'app.other')


if __name__ == '__main__': unittest.main()
