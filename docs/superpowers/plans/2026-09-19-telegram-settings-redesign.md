# Telegram-style settings redesign

**Goal:** Restyle every Android Nebula settings route and provide a local interactive browser preview.
**Architecture:** Keep native fragments, callbacks, search index, preference storage and existing previews. Update shared cards, rows and introductions; add inline search to the settings root. The browser prototype uses isolated demonstration state and never connects to accounts or servers.
**Tech stack:** Android Java views, static HTML/CSS/JavaScript, Python source checks.

- [x] Update `NebulaTheme.java`, `NebulaCard.java`, `NebulaRow.java`, `NebulaSettingsHero.java` for neutral grouped surfaces, consistent spacing and Telegram-style icon tiles.
- [x] Add root search backed by `NebulaSettingsSearch.match` in `NebulaSettingsFragment.java`, preserving the section routes and transfer menu.
- [x] Build `design/settings-preview/index.html`, `style.css`, `app.js` with every settings section, nested pages, working switches, selection sheets, search and light/dark presentation.
- [x] Run existing settings geometry, appearance, design and localization checks; inspect the preview in a browser at desktop and mobile widths.
- [x] Open the local preview in Codex and report native device/build verification limits.

Scope: Android Nebula settings. iOS and Telegram's upstream account settings retain their native implementation.

Verification: five relevant native source/behavior suites passed; all 23 browser routes inspected with no console errors. Search, toggles, selection sheet, range, draft retention and mobile width checked. The separate media-preview-surface suite requires patched upstream sources; the clean vendor tree lacks its pre-existing drawMenuPreview hook and cannot run that suite as-is. No upstream patches were modified in this task. APK and on-device visual QA remain outstanding.
