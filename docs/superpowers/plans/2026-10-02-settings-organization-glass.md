# Settings organization and glass implementation plan

> **For agentic workers:** Execute the checked tasks inline; preserve unrelated vendor and artwork changes.

**Goal:** Give each settings page one purpose, shorten explanatory copy, and make glass customization cheaper and easier on Android and iOS.

**Architecture:** Preserve stored keys and existing section IDs. Replace Android's concatenated chat/navigation screens with focused destinations, give glass its own section, and keep previews adjacent to their controls. Separate iOS navigation from appearance using the existing native settings controller. Batch Android material updates and avoid unchanged persistence/rebuild work.

**Tech Stack:** Android Java/Telegram views, UIKit/Swift, existing Python/Java fixtures and GitHub Android/iOS CI.

## 1. Focused Android settings

Files: `NebulaSectionFragment.java`, `NebulaSettingsFragment.java`, `NebulaSettingsHubFragment.java`, `NebulaSettingsSearch.java`, `NebulaSettingsLinks.java`, `NebulaFeatureControls.java` under the Android UI overlay.

- [x] Replace `SECTION_CHAT_SETTINGS` concatenation with links to chat appearance, messages and profile; keep existing direct section IDs available for search and links. Split navigation through its existing hub.
- [x] Add `SECTION_GLASS = 12`, route glass search entries there and expose it from appearance. Group profile controls into appearance and visible content; move unrelated conversation feature controls out of the profile's trailing area.
- [x] Remove redundant subtitles from self-explanatory controls, retain consequential explanations, and shorten glass/general prose. Check `python scripts/check-settings-links.py`, `python scripts/check-settings-root.py` and `python scripts/check-settings-design.py`.

## 2. Glass presets and efficient updates

Files: Android `NebulaGlass.java`, `NebulaGlassSettings.java`; iOS `NebulaGlassController.swift`.

- [x] Replace the single strong-refraction preset with a themed selection of balanced, clear and frosted materials. Apply each preset in one preferences transaction, and advance the render revision only when the effective snapshot changes. Retain manual controls and power/thermal adaptation.
- [x] Test actual snapshot revision behavior: identical setter and duplicate listener callback must not increment revision; changed values and a full preset must do so exactly once.
- [x] Shorten iOS glass section explanations, group material/detail/performance controls clearly, theme the preview from Telegram, and skip store validation and JSON encoding for unchanged integer slider events; disk writes were already deduplicated in the store.

## 3. iOS settings organization and delivery

Files: `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsController.swift`, release notes, `platform/ios/PARITY.md`.

- [x] Move bottom-bar controls to a dedicated Navigation page; keep glass, icons and transitions in Appearance and chat controls in Chats. Preserve global search and stable IDs.
- [x] Run Android regressions, iOS bootstrap/contracts and native compilation where the configured CI allows. Publish exact changed files and verify the resulting APK. Document outstanding iOS feature gaps and device verification honestly; this request does not turn pending native consumers into completed ports.

Local evidence: settings root geometry (36 cases), links including glass section 12, localization, presentation and 73-setting contracts pass. Actual glass setters preserve revisions on unchanged values and publish one revision per preset, while 40,000 warm getter calls avoid preferences. iOS bootstrap validates 59 patches and the overlay. Both native compilation runs subsequently passed; delivery evidence follows.

Android delivery: runtime `f52b857d5bdf0873df356c73d9b544264c17cea1`, run `37007327426`, passed the full regression suite and application build. Build `1000291`: `build/qa-apk-settings-f52b857/NebulaGram-1.0.0-TG-12.10.5-b1000291-arm64-v8a.apk`, 52,490,601 bytes, SHA-256 `5AC78264B86AF0236D1E783F04185F8731F8010CC85C85AACF1DF587809CFE39`. Package/version verified (`app.nebulagram.messenger`, 1.0.0/code 1000291); arm64 Telegram/NebulaLink libraries present. v1/v2 signatures pass with one signer, certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`, matching prior deliveries. Duplicate Android run `37007327742` was cancelled.

iOS bootstrap `37007327569` passed all 65 Swift tests, patch/overlay validation and parsing. Duplicate bootstrap `37007327822` was cancelled. Native compilation `37007329671` passed for SettingsUI, PeerInfoScreen and folder modules with Xcode 26.2. Downloaded `nebula-native-result.json` reports `compiled`; the hashes of both changed Swift sources match the committed Git blobs, and local Windows files match after CRLF normalization. Settings contract `37007327431` also passed. No signed IPA or physical-device/FPS verification is claimed.
