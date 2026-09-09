#!/usr/bin/env python3
"""Read-only APNs prerequisite audit of an extracted, signed iOS .app on macOS.

No signing, private keys, profile uploads or delivery claims. Fixed diagnostic
codes only: do not dump personal provisioning/profile or codesign output.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import plistlib
import subprocess
import sys
from xml.parsers.expat import ExpatError

MAX_PLIST = 4 * 1024 * 1024
GROUP = 'com.apple.security.application-groups'
TEAM = 'com.apple.developer.team-identifier'
FILTER = 'com.apple.developer.usernotifications.filtering'


def strings(value):
    return value if isinstance(value, list) and all(isinstance(v, str) for v in value) else []


def permitted(pattern, value):
    if not isinstance(pattern, str) or not isinstance(value, str):
        return False
    return pattern == value or (pattern.endswith('*') and '*' not in pattern[:-1] and value.startswith(pattern[:-1]))


def validate(app, extension, expected_environment, now=None):
    if expected_environment not in ('development', 'production'):
        raise ValueError('Invalid expected environment')
    now = now or dt.datetime.now(dt.timezone.utc)
    errors, warnings = [], []
    bundle = app.get('bundle_id') if isinstance(app, dict) else None
    if not isinstance(bundle, str) or not bundle or '*' in bundle:
        errors.append('app.bundle_invalid')
        bundle = ''
    group = 'group.' + bundle
    for role, item in [('app', app), ('extension', extension)]:
        if item is None:
            errors.append(role + '.missing')
            continue
        if not isinstance(item, dict) or not isinstance(item.get('entitlements'), dict) or not isinstance(item.get('profile'), dict):
            errors.append(role + '.malformed')
            continue
        ent, profile = item['entitlements'], item['profile']
        allowed = profile.get('Entitlements')
        if not isinstance(allowed, dict):
            errors.append(role + '.profile_malformed')
            continue
        expiration = profile.get('ExpirationDate')
        if not isinstance(expiration, dt.datetime):
            errors.append(role + '.profile_expiration_missing')
        elif expiration.replace(tzinfo=expiration.tzinfo or dt.timezone.utc) <= now:
            errors.append(role + '.profile_expired')
        if ent.get(TEAM) not in strings(profile.get('TeamIdentifier')) or not ent.get(TEAM):
            errors.append(role + '.team_mismatch')
        own_bundle = item.get('bundle_id')
        if not isinstance(own_bundle, str):
            errors.append(role + '.bundle_invalid')
            own_bundle = ''
        if role == 'extension' and own_bundle.rpartition('.')[0] != bundle:
            errors.append('extension.bundle_mismatch')
        identity = ent.get('application-identifier')
        expected_ids = [prefix + '.' + own_bundle for prefix in strings(profile.get('ApplicationIdentifierPrefix'))]
        if identity not in expected_ids or not permitted(allowed.get('application-identifier'), identity):
            errors.append(role + '.app_id_mismatch')
        if group not in strings(ent.get(GROUP)):
            errors.append(role + '.group_missing')
        if not any(permitted(p, group) for p in strings(allowed.get(GROUP))):
            errors.append(role + '.profile_group_missing')
        if role == 'app' or 'aps-environment' in ent:
            aps = ent.get('aps-environment')
            if aps not in ('development', 'production'):
                errors.append(role + '.aps_missing')
            elif aps != expected_environment:
                errors.append(role + '.environment_mismatch')
            if aps != allowed.get('aps-environment') or aps is None:
                errors.append(role + '.profile_aps_mismatch')
        if ent.get(FILTER) is True and allowed.get(FILTER) is not True:
            errors.append(role + '.filtering_not_provisioned')
        if role == 'extension' and ent.get(FILTER) is not True:
            warnings.append('extension.filtering_unavailable')
    if isinstance(app, dict) and isinstance(extension, dict):
        a, e = app.get('entitlements'), extension.get('entitlements')
        if isinstance(a, dict) and isinstance(e, dict) and a.get(TEAM) != e.get(TEAM):
            errors.append('extension.signing_team_differs')
    return {'status': 'issues-found' if errors else 'prerequisites-pass', 'errors': errors,
            'warnings': warnings, 'delivery_verified': False, 'provider_configuration': 'unverified'}


def read_plist(data):
    if len(data) > MAX_PLIST:
        raise ValueError('Oversized plist')
    value = plistlib.loads(data)
    if not isinstance(value, dict):
        raise ValueError('Expected plist dictionary')
    return value


def file_bytes(path):
    with path.open('rb') as stream:
        data = stream.read(MAX_PLIST + 1)
    if len(data) > MAX_PLIST:
        raise ValueError('Oversized input')
    return data


def command(*args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60, check=True)
    return result.stdout


def component(path):
    info = read_plist(file_bytes(path / 'Info.plist'))
    # Inspect signed entitlements, never a project/template .entitlements file.
    ent = read_plist(command('/usr/bin/codesign', '--display', '--entitlements', '-', '--xml', str(path)))
    profile_path = path / 'embedded.mobileprovision'
    file_bytes(profile_path)  # bounded input before invoking the CMS decoder
    profile = read_plist(command('/usr/bin/security', 'cms', '-D', '-i', str(profile_path)))
    return {'bundle_id': info.get('CFBundleIdentifier'), 'entitlements': ent, 'profile': profile}


def inspect(app, expected_environment):
    if sys.platform != 'darwin':
        raise ValueError('macOS required')
    app = Path(app).resolve(strict=True)
    if not app.is_dir() or app.suffix != '.app':
        raise ValueError('Expected extracted .app')
    # These commands verify/read only. Never invoke codesign --sign.
    command('/usr/bin/codesign', '--verify', '--deep', '--strict', str(app))
    extensions = []
    for path in (app / 'PlugIns').glob('*.appex'):
        if not path.resolve().is_relative_to(app):
            raise ValueError('Extension path escapes app')
        info = read_plist(file_bytes(path / 'Info.plist'))
        point = info.get('NSExtension', {})
        if isinstance(point, dict) and point.get('NSExtensionPointIdentifier') == 'com.apple.usernotifications.service':
            extensions.append(path)
    if len(extensions) > 1:
        raise ValueError('Ambiguous notification service')
    extension = None
    if extensions:
        command('/usr/bin/codesign', '--verify', '--strict', str(extensions[0]))
        extension = component(extensions[0])
    result = validate(component(app), extension, expected_environment)
    result['local_signature_verification'] = 'passed'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app', type=Path, required=True)
    parser.add_argument('--expected-environment', choices=['development', 'production'], required=True,
                        help='Environment this build registers with Telegram; upstream DEBUG uses development')
    args = parser.parse_args()
    try:
        result = inspect(args.app, args.expected_environment)
    except (OSError, ValueError, TypeError, ExpatError, plistlib.InvalidFileException, subprocess.SubprocessError):
        # Do not expose signed profile contents, paths, UDIDs or tool stderr.
        result = {'status': 'inspection-failed', 'errors': ['Use macOS with an extracted signed app, readable profiles and valid signatures.'],
                  'delivery_verified': False, 'provider_configuration': 'unverified'}
    print(json.dumps(result, indent=2))
    return 0 if result['status'] == 'prerequisites-pass' else 1

if __name__ == '__main__':
    raise SystemExit(main())
