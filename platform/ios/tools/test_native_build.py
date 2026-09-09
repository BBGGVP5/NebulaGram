"""Local safety/reproducibility tests; these do not claim native compilation."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('native_build', Path(__file__).with_name('native-build.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativeBuildTests(unittest.TestCase):
    def test_existing_destination_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as work:
            tree = Path(work)
            sentinel = tree / 'my-work.txt'
            sentinel.write_text('keep', encoding='utf-8')
            with self.assertRaises(ValueError): native.require_fresh(tree)
            self.assertEqual(sentinel.read_text(), 'keep')
            with self.assertRaises(ValueError): native.require_fresh(sentinel)
            # macOS exposes /var via /private/var; compare canonical paths.
            self.assertEqual(native.require_fresh(tree / 'fresh'), (tree / 'fresh').resolve())

    def test_vendor_destination_refused(self):
        with self.assertRaises(ValueError):
            native.require_fresh(native.ROOT / 'vendor/never-write-a-build-here')

    def test_collision_is_atomic_and_new_tree_is_deterministic(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            source, dest = root / 'overlay', root / 'tree'
            source.mkdir(); dest.mkdir()
            (source / 'a.swift').write_text('first', encoding='utf-8')
            (source / 'z.swift').write_text('second', encoding='utf-8')
            (dest / 'z.swift').write_text('upstream', encoding='utf-8')
            with self.assertRaises(ValueError): native.copy_overlay(dest, source)
            self.assertFalse((dest / 'a.swift').exists())
            self.assertEqual((dest / 'z.swift').read_text(), 'upstream')
            fresh = root / 'fresh'
            fresh.mkdir()
            native.copy_overlay(fresh, source)
            for file in source.iterdir(): self.assertEqual(file.read_bytes(), (fresh / file.name).read_bytes())

    def test_parent_symlink_escape_refused(self):
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            overlay, dest, outside = root / 'overlay', root / 'tree', root / 'outside'
            (overlay / 'nested').mkdir(parents=True); dest.mkdir(); outside.mkdir()
            (overlay / 'nested/a.swift').write_text('data', encoding='utf-8')
            try: (dest / 'nested').symlink_to(outside, target_is_directory=True)
            except OSError: self.skipTest('Symlink creation is unavailable on this host')
            with self.assertRaises(ValueError): native.copy_overlay(dest, overlay)
            self.assertEqual(list(outside.iterdir()), [])

    def test_libraries_target_ios_simulator_not_host_macos(self):
        self.assertIn('--cpu=ios_sim_arm64', native.SIMULATOR_FLAGS)
        self.assertIn('--apple_platform_type=ios', native.SIMULATOR_FLAGS)
        self.assertIn('--ios_minimum_os=13.0', native.SIMULATOR_FLAGS)

    def test_input_inventory_and_compile_only_configuration(self):
        self.assertEqual(len(native.pin()), 40)
        first = native.inputs()
        self.assertEqual(first, native.inputs())
        self.assertIn('platform/ios/overlay/submodules/NebulaIntegrationChecks/BUILD', first)
        self.assertIn('patches/ios/0001-nebula-settings-bootstrap.patch', first)
        self.assertIn('platform/ios/tools/native-build.py', first)
        fixture = native.fixture_configuration()
        self.assertEqual(fixture['api_id'], '0')
        self.assertEqual(fixture['api_hash'], '0' * 32)
        self.assertFalse(fixture['enable_icloud'])
        self.assertFalse(fixture['enable_siri'])
        self.assertEqual(native.TARGET, '//submodules/NebulaIntegrationChecks:NebulaIntegrationChecks')


if __name__ == '__main__': unittest.main()
