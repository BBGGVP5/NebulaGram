# AI and Privacy Design Implementation Plan

> **For agentic workers:** Execute this plan task-by-task in the current session; unavailable orchestration skills are not required to make progress.

**Goal:** Refresh Android/iOS AI and privacy settings, capture real Android views, then build both platforms.

**Architecture:** Native theme-aware components, no new rendering framework or animated blur. Android AI separates request, connection and instructions without recreating fields; privacy keeps important warnings in confirmations and moves detailed help behind a clearly labelled row. UIKit uses a reusable self-sizing header and accessible subtitle cells. Storage/network behavior stays unchanged.

**Tech Stack:** Java Android Views, Swift UIKit, Gradle, Python checks, adb, GitHub Actions.

---

### 1. Shared visual hierarchy
- [x] Create `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSettingsHero.java`: theme-tinted rounded header, icon, wrapping title and explanation, readable status.
- [x] Create `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsHero.swift`: Dynamic Type labels, Auto Layout, self-sizing table header, SF Symbol and theme-aware colors; no fixed text height.

### 2. Android screens
- [x] Modify `NebulaAiFragment.java` in the same Android UI directory: three tabs toggle existing containers with `View.VISIBLE / View.GONE`; keep field references and in-flight cancellation intact. Status describes local configuration, not connectivity. Prefilled requests select request tab. Responses stay with request tab.
- [x] Modify `NebulaPrivacyFragment.java`: reusable hero, compact summary and explicit “How it works” dialog retaining all limitations. Keep `confirmClear` signature and every destructive confirmation.
- [x] Add `scripts/check-settings-design.py`: assert tab visibility state is applied without rebuilding on tab changes, key is never read into UI, privacy help and confirmations remain, UIKit sizing handles width/Dynamic Type.
- [x] Run `python scripts/check-settings-design.py`, `python scripts/check-ai-protocol.py`, `python scripts/check-ai-availability.py` and Gradle `:TMessagesProj:compileStandaloneJavaWithJavac` against the disposable build tree after copying only changed overlay files.

### 3. iOS screens
- [x] Modify `NebulaAiController.swift` and `NebulaPrivacyController.swift`: common hero, adaptive subtitle cells, semantic symbols, selectable actions unchanged. Refresh hero on setting changes and table layout; respect accessibility text sizing.
- [x] Run `python platform/ios/tools/test_native_build.py` and `python platform/ios/tools/test_ipa_build.py`. Parse changed Swift with local parser if available; full UIKit typecheck occurs in macOS build.

### 4. Visual verification and delivery
- [ ] Boot authorized Android emulator with adb. Render actual new views using a local test host if no authenticated Telegram account is available; never invent AI answers or use real secrets.
- [ ] Capture PNGs into a new versioned design directory, inspect at phone size, verify text fitting and tabs. Keep existing posters untouched. Clearly distinguish test-host captures from release/account screenshots.
- [ ] Review `git diff --check`, commit only this task, push Android build and dispatch iOS workflow. Report actual statuses and any screenshot/build limitations.


## Verification results
- Android Java compilation passed against the disposable full Telegram tree (1m33s), then passed again after final polish (30s); no vendor reset.
- Production AI tab method: 600 transitions passed, selected tab and visible page stay synchronized, field containers remain the same objects.
- AI protocol suite and 53 availability combinations passed; no credentials/network requests to AI providers used.
- iOS build preparation: 6 tests; IPA validation: 8 tests; 13-patch bootstrap integration check passed.
- All three changed Swift files parsed with tree-sitter; this is not a UIKit typecheck. Full iOS compilation remains a CI verification.
- Local emulator blocked before guest boot: original Pixel 7 and fresh Nebula_Post_Clean AVD, hardware and software CPU/GPU options all remain adb offline with no kernel output. No authenticated session was used, and no screenshots were fabricated. Screenshot/visual QA task remains incomplete.
- Previous Android build 34758519763 succeeded, but predates this redesign. Previous iOS build 34758520720 was still running at this checkpoint.
