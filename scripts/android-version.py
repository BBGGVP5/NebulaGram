#!/usr/bin/env python3
"""Give APKs an independent app version and a monotonic rebuild number."""
from pathlib import Path
import argparse
import re
import subprocess

ROOT = Path(__file__).resolve().parent.parent


def build_code(run_number):
    code = 1_000_000 + int(run_number)
    if not 1_000_000 < code <= 2_147_483_647:
        raise ValueError('Invalid workflow run number')
    return code


def app_version(path):
    match = re.search(r'^NEBULA_VERSION=(\d+\.\d+\.\d+)$', Path(path).read_text(encoding='utf-8'), re.M)
    if not match:
        raise ValueError('NEBULA_VERSION must contain three numeric components')
    return match.group(1)


def verify_badging(badging, version, code, package):
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging, re.M)
    if not match or match.groups() != (package, str(code), version):
        raise ValueError('APK package/version differ from the update filename')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prepare = commands.add_parser('prepare')
    prepare.add_argument('--run-number', required=True)
    prepare.add_argument('--github-env', required=True)
    verify = commands.add_parser('verify')
    verify.add_argument('--aapt', required=True)
    verify.add_argument('--apk', required=True)
    verify.add_argument('--version', required=True)
    verify.add_argument('--code', type=int, required=True)
    verify.add_argument('--package', default='app.nebulagram.messenger')
    args = parser.parse_args()
    if args.command == 'prepare':
        version = app_version(ROOT / 'platform/android/version.properties')
        code = build_code(args.run_number)
        with open(args.github_env, 'a', encoding='utf-8', newline='\n') as env:
            env.write(f'NEBULA_VERSION={version}\nNEBULA_BUILD_CODE={code}\n')
        print(f'NebulaGram {version}, build {code}')
    else:
        badging = subprocess.check_output([args.aapt, 'dump', 'badging', args.apk], text=True, encoding='utf-8')
        verify_badging(badging, args.version, args.code, args.package)
        print('Verified APK package and both version fields')


if __name__ == '__main__':
    main()
