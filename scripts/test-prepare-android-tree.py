"""Exercise actual fresh Android preparation with a dirty old source fixture."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prepare_android', Path(__file__).with_name('prepare-android-tree.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PreparationTests(unittest.TestCase):
    def test_destination_rejects_existing_vendor_parent_and_symlink(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / 'workspace'
            root.mkdir()
            for target in [root, root / 'vendor/android', Path(folder)]:
                with self.assertRaises(ValueError):
                    module.fresh_destination(target, root)
            self.assertEqual(module.fresh_destination(root / 'build/fresh', root), root / 'build/fresh')
            link = root / 'link'
            try:
                link.symlink_to(root / 'absent', target_is_directory=True)
            except OSError:
                return  # Windows without symlink privilege; other guards still execute.
            with self.assertRaises(ValueError):
                module.fresh_destination(link, root)

    def test_rejects_stale_or_missing_version(self):
        for contents in ['APP_VERSION_NAME=12.10.6', 'APP_VERSION_NAME=13.0.1', 'APP_VERSION_NAME=13.0.2', 'APP_VERSION_NAME=13.0.3', 'APP_VERSION_NAME=12.10.4', 'APP_VERSION_NAME=12.10.5', '# APP_VERSION_NAME=13.1.1', '']:
            with self.assertRaises(ValueError):
                module.version(contents)
        self.assertEqual(module.version('APP_VERSION_NAME=13.1.1\nAPP_VERSION_CODE=7237'), '13.1.1')

    def test_prepares_pinned_version_without_copying_or_resetting_dirty_source(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / 'workspace'
            source = root / 'vendor/android'
            source.mkdir(parents=True)
            subprocess.run(['git', 'init', '--quiet', str(source)], check=True)
            subprocess.run(['git', '-C', str(source), 'config', 'core.autocrlf', 'false'], check=True)
            (source / 'gradle.properties').write_bytes(b'APP_VERSION_NAME=13.1.1\nAPP_VERSION_CODE=7237\n')
            (source / 'native.java').write_bytes(b'old\n')
            subprocess.run(['git', '-C', str(source), 'add', '.'], check=True)
            subprocess.run(['git', '-C', str(source), '-c', 'user.name=Test', '-c', 'user.email=test@local', 'commit', '--quiet', '-m', 'Pinned version'], check=True)
            pin = module.output('git', '-C', source, 'rev-parse', 'HEAD')
            (source / 'gradle.properties').write_bytes(b'APP_VERSION_NAME=12.10.4\nlocal-change\n')
            (source / 'private-config').write_bytes(b'private-source-config\n')
            before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in source.iterdir() if p.is_file()}
            overlay = root / 'overlay'
            overlay.mkdir()
            (overlay / 'helper.java').write_bytes(b'helper\r\n')
            patches = root / 'patches'
            patches.mkdir()
            (patches / '0001.patch').write_bytes(b'diff --git a/native.java b/native.java\n--- a/native.java\n+++ b/native.java\n@@ -1 +1 @@\n-old\n+patched\n')
            destination = root / 'build/fresh'
            result = module.prepare(destination, source, pin, overlay, patches, root=root)
            self.assertEqual((destination / 'native.java').read_bytes(), b'patched\n')
            self.assertEqual((destination / 'helper.java').read_bytes(), b'helper\n')
            self.assertFalse((destination / 'private-config').exists())
            self.assertFalse(result['credentials_injected'])
            self.assertEqual(result['patch_count'], 1)
            self.assertEqual(before, {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in source.iterdir() if p.is_file()})
            with self.assertRaises(ValueError):
                module.prepare(destination, source, pin, overlay, patches, root=root)


if __name__ == '__main__':
    unittest.main()
