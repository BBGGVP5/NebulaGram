# iOS recent Android changes

**Goal:** Port remote Saved Messages retention, archive performance improvements, native glass preview/settings and consistent choice sheets. Repair the native zoom build error and dispatch an iOS IPA build.

**Architecture:** Keep Telegram Postbox deletion hooks and local tags. Cache immutable archive snapshots and coalesce encrypted writes off the transaction queue. Use supported UIKit glass/material effects inside the shared GlassBackgroundView, so the preview and actual surfaces share the same settings. Replace text-prefixed checkmarks with native table accessories in a reusable sheet.

**Files:** `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaDeletedArchive.swift`, `NebulaRetentionPolicy.swift`, `NebulaSettingsStore.swift`; matching XCTest coverage; SettingsUI `NebulaPrivacyController.swift`, `NebulaGlassController.swift`, `NebulaChoiceController.swift`, AI/settings choice callers; TelegramCore `NebulaDeletedCapture.swift`; ordered glass patch; generated mirrors and parity inventory.

- [x] Archive: separate Saved Messages scope; retain received or self-chat messages at remote hooks; cache membership, skip duplicates, coalesce disk writes; test account isolation and persistence.
- [x] Glass: native system/liquid/matte choices, tint amount, actual draggable glass preview, power/accessibility status; update existing surfaces through observations.
- [x] Sheets: reusable rounded UIKit presentation with Dynamic Type rows and trailing checkmark; migrate provider/action/navigation/transition/archive choices; explain local-model availability.
- [x] Fix the inaccessible camera zoom property reported by native CI.
- [x] Regenerate catalogs, validate ordered patches and bootstrap locally, start macOS XCTest and native/IPA builds; document outstanding device acceptance honestly.

Validation: 31 patches applied to the pinned source; local contract/design/build-preparation checks passed. First macOS run compiled the shared Swift code and passed all archive tests; one stale catalog-count assertion was corrected from 69 to 71. Native compile and IPA runs use commit `0c5c81f`; the follow-up changes tests/documentation only. Device acceptance and full Android visual-switch parity remain open.
