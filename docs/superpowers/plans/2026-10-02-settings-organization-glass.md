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
- [x] Shorten iOS glass section explanations, group material/detail/performance controls clearly, theme the preview from Telegram, and avoid rewriting unchanged integer values during slider events.

## 3. iOS settings organization and delivery

Files: `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsController.swift`, release notes, `platform/ios/PARITY.md`.

- [x] Move bottom-bar controls to a dedicated Navigation page; keep glass, icons and transitions in Appearance and chat controls in Chats. Preserve global search and stable IDs.
- [ ] Run Android regressions, iOS bootstrap/contracts and native compilation where the configured CI allows. Publish exact changed files and verify the resulting APK. Document outstanding iOS feature gaps and device verification honestly; this request does not turn pending native consumers into completed ports.

Local evidence: settings root geometry (36 cases), links including glass section 12, localization, presentation and 73-setting contracts pass. Actual glass setters preserve revisions on unchanged values and publish one revision per preset, while 40,000 warm getter calls avoid preferences. iOS bootstrap validates 59 patches and the overlay. Native application compilation is pending.
