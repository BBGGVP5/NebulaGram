import copy
import datetime as dt
import importlib.util
from pathlib import Path
import unittest
import plistlib
import tempfile
import shutil
import sys

spec = importlib.util.spec_from_file_location('push_signing', Path(__file__).with_name('check-notification-signing.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class SigningTests(unittest.TestCase):
    def setUp(self):
        self.now = dt.datetime(2026, 9, 9, tzinfo=dt.timezone.utc)
        self.bundle = 'example.personal.Nebula'
        self.group = 'group.' + self.bundle
        self.main = self.component(self.bundle)
        self.main['entitlements']['aps-environment'] = 'production'
        self.main['profile']['Entitlements']['aps-environment'] = 'production'
        self.extension = self.component(self.bundle + '.NotificationService')

    def component(self, bundle):
        ent = {'application-identifier': 'LEGACYPREFIX.' + bundle,
               'com.apple.developer.team-identifier': 'USERTEAM',
               'com.apple.security.application-groups': [self.group]}
        return {'bundle_id': bundle, 'entitlements': ent, 'profile': {
            'ExpirationDate': self.now + dt.timedelta(days=10),
            'TeamIdentifier': ['USERTEAM'], 'ApplicationIdentifierPrefix': ['LEGACYPREFIX'],
            'Entitlements': copy.deepcopy(ent), 'ProvisionedDevices': ['PRIVATE_DEVICE_ID']}}

    def check_result(self):
        return m.validate(self.main, self.extension, 'production', self.now)

    def test_other_signing_team_and_legacy_prefix_supported(self):
        self.assertEqual(self.check_result()['errors'], [])
        self.assertFalse(self.check_result()['delivery_verified'])
        self.assertEqual(self.check_result()['provider_configuration'], 'unverified')

    def test_missing_push_and_environment_mismatch(self):
        del self.main['entitlements']['aps-environment']
        self.assertIn('app.aps_missing', self.check_result()['errors'])
        self.main['entitlements']['aps-environment'] = 'development'
        errors = self.check_result()['errors']
        self.assertIn('app.environment_mismatch', errors)
        self.assertIn('app.profile_aps_mismatch', errors)

    def test_group_required_in_both_signature_and_profile(self):
        self.extension['entitlements']['com.apple.security.application-groups'] = []
        self.main['profile']['Entitlements']['com.apple.security.application-groups'] = []
        self.assertIn('extension.group_missing', self.check_result()['errors'])
        self.assertIn('app.profile_group_missing', self.check_result()['errors'])

    def test_expired_wrong_team_and_app_id(self):
        self.main['profile']['ExpirationDate'] = self.now
        self.extension['profile']['TeamIdentifier'] = ['DIFFERENT']
        self.extension['entitlements']['application-identifier'] = 'OTHER.wrong.bundle'
        errors = self.check_result()['errors']
        self.assertIn('app.profile_expired', errors)
        self.assertIn('extension.team_mismatch', errors)
        self.assertIn('extension.app_id_mismatch', errors)

    def test_missing_extension_and_wrong_base(self):
        self.extension = None
        self.assertIn('extension.missing', self.check_result()['errors'])
        self.extension = self.component('different.Nebula.NotificationService')
        self.assertIn('extension.bundle_mismatch', self.check_result()['errors'])

    def test_wildcard_profile_and_filtering(self):
        self.main['profile']['Entitlements']['application-identifier'] = 'LEGACYPREFIX.example.personal.*'
        self.assertEqual(self.check_result()['errors'], [])
        key = 'com.apple.developer.usernotifications.filtering'
        self.extension['entitlements'][key] = True
        self.assertIn('extension.filtering_not_provisioned', self.check_result()['errors'])
        self.extension['profile']['Entitlements'][key] = True
        self.assertEqual(self.check_result()['errors'], [])

    def test_invalid_plist_values_fail_closed_and_do_not_leak(self):
        self.main['profile']['TeamIdentifier'] = 'USERTEAM'
        self.extension['entitlements'] = ['PRIVATE_DEVICE_ID']
        result = self.check_result()
        self.assertTrue(result['errors'])
        for private in ['USERTEAM', 'PRIVATE_DEVICE_ID', self.bundle]:
            self.assertNotIn(private, str(result))
        with self.assertRaises(ValueError): m.validate(self.main, self.extension, 'invalid', self.now)

class InspectionTests(unittest.TestCase):
    def test_bounded_plist_and_dictionary_only(self):
        self.assertEqual(m.read_plist(plistlib.dumps({'ok': True})), {'ok': True})
        with self.assertRaises(ValueError): m.read_plist(plistlib.dumps(['not a dict']))
        with self.assertRaises(ValueError): m.read_plist(b'x' * (m.MAX_PLIST + 1))
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory) / 'large.plist'
            p.write_bytes(b'x' * (m.MAX_PLIST + 1))
            with self.assertRaises(ValueError): m.file_bytes(p)

    @unittest.skipUnless(sys.platform == 'darwin', 'Real codesign tool requires macOS')
    def test_real_codesign_reader_on_disposable_adhoc_fixture(self):
        # No developer certificate/profile required; this is NOT an iOS delivery test.
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / 'fixture'
            shutil.copyfile('/usr/bin/true', executable)
            executable.chmod(0o755)
            entitlements = Path(directory) / 'fixture.plist'
            entitlements.write_bytes(plistlib.dumps({'example.nebula.fixture': True}))
            m.command('/usr/bin/codesign', '--force', '--sign', '-', '--entitlements', str(entitlements), str(executable))
            m.command('/usr/bin/codesign', '--verify', '--strict', str(executable))
            actual = m.read_plist(m.command('/usr/bin/codesign', '--display', '--entitlements', '-', '--xml', str(executable)))
            self.assertTrue(actual['example.nebula.fixture'])

if __name__ == '__main__': unittest.main()
