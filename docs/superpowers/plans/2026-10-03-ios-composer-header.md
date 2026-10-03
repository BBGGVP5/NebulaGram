# iOS Composer and Header Implementation Plan

**Goal:** Port the latest Android composer and home header corrections to iOS, preserving native Telegram themes and interactions.

**Architecture:** Add patch 0068 after the 67 published native patches. Keep modal presentation and outline drawing in SettingsUI overlays; use Telegram's existing GlassBackgroundView for the complete home header.

**Tech Stack:** Swift, UIKit, Telegram component layout, Bazel, GitHub macOS CI.

- [x] Add a themed navigation sheet shared by composer tools and message actions. Preserve nested navigation, dimming, interactive dismissal and exact-once dismissal notification.
- [x] Suspend draft translation while the sheet is visible. Keep source revision checks when applying a result.
- [x] Draw a cached 24-point outline icon. Reserve a consistent 44-point composer slot and animate it with the native accessory layout.
- [x] Add full-width native glass behind the header, using active Telegram colors and existing glass accessibility/performance fallbacks. Keep title text at readable scale through transitions.
- [x] Validate all patches, source contracts and build tooling, then run macOS SDK checks and the complete device IPA build.
- [x] Verify the downloaded IPA, record source/build provenance and report device-testing limitations.

Implementation runs in this session under the user's existing request to port and build.

Local validation: 68 patches across 116 native paths applied cleanly; 6 native preparation and 8 IPA configuration tests passed; settings contract and design checks passed. Apple SDK compilation and IPA validation are pending CI.

Apple verification: bootstrap workflow 37110557483 passed at source 532b1bb3b829779e91c9996369b554a4bd272db7: 86 Swift tests, 68 ordered native patches across 116 paths, native Swift parsing, embedded Foundation compilation, UIKit sheet typecheck, Objective-C and asset SDK checks. The superseded IPA workflow 37110576390 was cancelled after the initial-position correction. Bootstrap 37111194726 passed for final runtime e7e888a19f41a9bf79a34d186b859230482f166c; IPA workflow 37111219096 completed successfully. Downloaded build 61 was verified against GitHub artifact digest and manifest: arm64 device app, app.nebulagram, exactly one notification service extension; SHA-256 20ed48884d25d5572e8e6823e0e2839a6934e25a760e5adc4ed8d170adfcd244. Local checkpoint: build/qa-ipa-composer-e7e888a/NebulaGram-unsigned.ipa. User signing and device testing remain required. This checkpoint precedes the subsequent tools-refinement request.

Device acceptance still required:
- Empty, one-line and multiline drafts; emoji/gift/attachment/send changes; recording and editing states. Verify the 44-point AI slot does not overlap native controls.
- Open tools, nested editor/settings, language picker and keyboard. Close through Apply, close button and interactive swipe; cancelled swipes must keep draft translation suspended.
- Return after app backgrounding; enable/disable translation while the sheet is open; reject a result when its source draft has changed.
- Light/dark Telegram themes, narrow phones, large text and landscape; home stories expansion/collapse, search, folder navigation and active connection title changes.
- Glass presets and quality changes, Reduce Transparency and Reduce Motion; verify the full header remains readable and native controls respond.
