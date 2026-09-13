# Preserve Telegram iOS tab-bar appearance Implementation Plan

**Goal:** Hiding/reordering tabs changes only the controller list, never the native Telegram bar's visual implementation.

**Architecture:** Mark the upstream tab bar's existing glass container as native-only. Descendant glass views bypass Nebula's adaptive substitute while retaining all original Telegram drawing, search, selection and gestures. Other app surfaces keep adaptive glass. Clip their fallback material explicitly.

**Files:** New `patches/ios/0015-native-tab-bar-appearance.patch`; extend `platform/ios/tools/check-bootstrap.py` and `patches/ios/HOOKS.md`.

- [x] Add an inherited native-appearance marker to the existing glass container and a scoped adaptive-policy guard.
- [x] Enable the marker in TabBarComponent without replacing/reparenting native controls.
- [x] Prove the rest of TabBarComponent is byte-identical to the pinned upstream; verify filtering/order/selection still use native controllers.
- [x] Apply the complete ordered patch series with bootstrap checks. No IPA build or claims of on-device verification on this Windows host.

Verified: all 15 ordered iOS patches / 35 upstream paths apply; TabBarComponent is identical to upstream apart from one marker assignment after super.init. No Swift compiler/Apple SDK on this Windows host; UIKit compilation and iPhone low-power/reorder/hide-tab/search visual checks remain unverified.
