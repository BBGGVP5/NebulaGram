# Android Search, Stories and Pager Implementation Plan

> Execute inline; subagent/executing-plans skills unavailable.

**Goal:** Add settings-search history clear/show controls, a chat-list story strip toggle, and frame-synchronous bottom tab selection; dispatch Android build after checks.
**Architecture:** Small Nebula preference helpers + native ProfileActivity/DialogsActivity hooks. Preserve history when hidden but stop collecting new entries; clearing requires confirmation and only removes settings history. Story toggle changes presentation, not server data. Pager lens consumes actual current/next/progress, including skipped hidden tabs, and snaps at completion/cancel.
**Tech Stack:** Java Android, patch overlay, shared JSON catalog, Python/Java regression harness, Gradle, CI.

- [x] Add regression checks for history read/write suppression, clear isolation/refresh, story visibility gating and continuous selector positions.
- [x] Add preferences in NebulaAppearance, history helper, General UI controls and appended search entries (preserve existing numeric IDs). Catalog entries planned iOS, not added to legacy v1 export.
- [x] Generate 0075 patch for ProfileActivity history, DialogsActivity story strip and MainTabsActivity/MainTabsLayout pager drawing. Record hooks.
- [x] Run regression/contract checks and compile Java; push owned changes once to trigger Android CI. Check iOS native result separately. Push signing verifier plan remains pending while these requested Android fixes take priority.

## Verification
- All 70 patches applied in order to pinned upstream sources.
- 29 Android workflow checks passed, including history suppression/clear, 8 story states and 303 forward/reverse/cancel pager samples.
- Full :TMessagesProj:compileStandaloneJavaWithJavac passed in 2m55s after fixing captured visibility to remain final.
- Logs: build/search-stories-pager-0909/checks.log and compile.log. No connected-device rendering/gesture claim.
- iOS native module build 34341129456 passed at ab32b65; the two new catalog preferences remain planned on iOS, not exposed as implemented.
