# Native Settings Design Implementation Plan

**Goal:** Apply the approved settings presentation on Android and iOS while retaining the existing native previews and all preference callbacks.

**Architecture:** Android continues using NebulaCard, NebulaRow, NebulaSettingsHero and the existing preview classes. iOS retains its ItemListUI root and UIKit detail controllers; shared presentation helpers provide compact headings and colored navigation icons. Only settings with existing iOS consumers are exposed.

**Tech Stack:** Android Java/XML; Swift UIKit and Telegram ItemListUI; Python integration checks.

The user has requested implementation in this task; execute inline. No replacement web views, preview renderers, preference migration, badge artwork changes or media changes.

### Task 1: Android details and preview preservation

- [x] Add the shared compact introduction to `NebulaDesignFragment.java`, before the existing icon-pack grid and avatar sample. Keep the grid's resource selection, slider callback and draw method intact.
- [x] Align selection corner radius with the existing 20dp cards.

```java
LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
params.bottomMargin = dp(12);
content.addView(new NebulaSettingsHero(c, avatars ? R.drawable.nebula_cupertino_person : R.drawable.msg_customize,
        title, summary), params);
```

Keep `NebulaPreview`, `NebulaWallpaperPreview`, `NebulaControlsPreview`, `NebulaComposerPreview`, `NebulaFoldersPreview` and their callers unchanged. Keep the native iOS `NebulaLinkOverviewView` instance and its action/state callbacks.

### Task 2: iOS shared presentation and root

- [x] Update `NebulaSettingsHero.swift`: 20pt card, 48pt icon tile beside the heading, 20pt content margins, Dynamic Type, solid neutral background, existing status and table-header sizing.
- [x] Add `NebulaSettingsStyle.swift` for reusable 32pt colored navigation tiles and table-cell finishing. Switch rows have no leading tile; destructive colors and controls remain intact.

```swift
cell.imageView?.image = NebulaSettingsStyle.icon(symbol: symbol, color: color)
cell.textLabel?.font = .preferredFont(forTextStyle: .body)
cell.detailTextLabel?.font = .preferredFont(forTextStyle: .subheadline)
```

- [x] Use native `ItemListDisclosureItem` rows for the five existing root destinations, preserving stable IDs and actions. Show current glass/tab-order values and RU/EN subtitles.
- [x] Add a stable native search-input entry and transient `ValuePromise<String>`; filter existing rows and their section headers without writing defaults/history or hiding a storage failure.

```swift
let query = ValuePromise("", ignoreRepeated: true)
arguments.searchUpdated = { query.set($0) }
```

- [x] Apply shared cell styling to AI and privacy tables. Preserve fields, switches, key handling, archive operations, hero status, and all existing callback targets.

### Task 3: Verification and delivery

- [x] Run existing Android settings-root, settings-design, chat-layout, appearance-controls and runtime checks; attempt `:TMessagesProj:compileStandaloneJavaWithJavac` in the prepared build tree (resource limitation below).
- [x] Run `python platform/ios/tools/check-bootstrap.py` and contract generation checks. Add the new UIKit-only presentation files to the existing SDK typecheck in `--swift` so macOS CI validates their real APIs.
- [x] Add focused integration guards for search stable IDs, retained callbacks, supported iOS rows, and native preview bindings. Verify that preview implementation hashes match the start of this task.
- [x] Document successful local checks and the iOS build limitation on this Windows host. Do not claim device verification.

## Validation result

- Android checks passed: 36 settings-layout cases, 600 AI-tab transitions, 1080 appearance cases, 72 chat-layout cases, 264 folder/inset cases, 1001 avatar transition frames and 20 badge metric/alpha cases.
- All six existing native preview files are byte-identical to the start of this task. The icon-pack/anonymous avatar samples keep their drawing logic and preference callbacks; only surrounding introduction/selection styling changed.
- iOS: 16 patches and 37 integration paths passed bootstrap checks; catalog and generated mirrors match. All 19 existing row IDs are preserved and new search IDs are unique.
- Full Android Java compilation was attempted with both the prior 3GB/two-worker configuration and a 2GB/one-worker SerialGC configuration. Both JVMs terminated with native out-of-memory allocation failures. This is not a successful full build.
- The actual new Android introduction method compiled separately against Android SDK 36.1 and previously generated native hero/resource classes. This does not replace full application compilation.
- No Swift/UIKit typecheck, IPA/APK assembly or device acceptance was performed on this Windows host. Actual Foundation search checks and UIKit helper typechecks are wired into the existing macOS bootstrap CI command.
