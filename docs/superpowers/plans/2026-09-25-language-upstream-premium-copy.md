# Language picker, Telegram source check, and Premium copy

**Goal:** Correct the selected-language row alignment, advance the Android Telegram source when its ordered Nebula patches remain compatible, verify the iOS source baseline, and finish copying Premium custom emoji in messages.

**Architecture:** Keep Telegram source pristine and update only the submodule revision plus narrowly scoped ordered patches. Keep the language-picker correction in the Android overlay. Preserve Telegram's native message-copy behavior and add only the missing rich entities at the copy boundary.

## Tasks

- [x] Move explicit language-row padding after background assignment and add a focused source/layout regression check for selected and unselected rows.
- [x] Rebase the managed-proxy hook against Android 12.10.4, apply the complete ordered patch series in a disposable tree, then update the Android submodule pointer and upstream version note only after the full series passes.
- [x] Confirm the pinned iOS revision matches the current public `master`; do not switch to an older release tag.
- [x] Trace ordinary and quick-copy paths for messages and captions; preserve formatting and UTF-16 entity offsets while restoring Premium custom-emoji entities.
- [x] Add focused copy regression checks and run the Android Java compile plus relevant patch-series checks.
- [x] Update the iMe feature plan and Android capability notes to reflect implemented functionality and any genuine platform/service limits.

Validation: all 109 Android patches apply to Telegram Android 12.10.4, all 18 iOS patches and 41 upstream paths pass the iOS bootstrap check, Android standalone Java compilation passes with Firebase processing skipped (the local Google Services JSON has no `org.telegram.messenger` client), and settings, copy, private-feature, task, sync and upstream-version checks pass. Device rendering and a full APK/signing build were not part of this verification.
