# iOS Composer and Header Implementation Plan

**Goal:** Port the latest Android composer and home header corrections to iOS, preserving native Telegram themes and interactions.

**Architecture:** Add patch 0068 after the 67 published native patches. Keep modal presentation and outline drawing in SettingsUI overlays; use Telegram's existing GlassBackgroundView for the complete home header.

**Tech Stack:** Swift, UIKit, Telegram component layout, Bazel, GitHub macOS CI.

- [x] Add a themed navigation sheet shared by composer tools and message actions. Preserve nested navigation, dimming, interactive dismissal and exact-once dismissal notification.
- [x] Suspend draft translation while the sheet is visible. Keep source revision checks when applying a result.
- [x] Draw a cached 24-point outline icon. Reserve a consistent 44-point composer slot and animate it with the native accessory layout.
- [x] Add full-width native glass behind the header, using active Telegram colors and existing glass accessibility/performance fallbacks. Keep title text at readable scale through transitions.
- [ ] Validate all patches, source contracts and build tooling, then run macOS SDK checks and the complete device IPA build.
- [ ] Verify the downloaded IPA, record source/build provenance and report device-testing limitations.

Implementation runs in this session under the user's existing request to port and build.

Local validation: 68 patches across 116 native paths applied cleanly; 6 native preparation and 8 IPA configuration tests passed; settings contract and design checks passed. Apple SDK compilation and IPA validation are pending CI.
