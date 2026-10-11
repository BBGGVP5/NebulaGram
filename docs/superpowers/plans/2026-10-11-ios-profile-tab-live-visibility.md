# iOS profile tab visibility and icon Implementation Plan

> **For agentic workers:** Execute inline with the existing writing-plans workflow; optional execution plugins are unavailable.

**Goal:** Hide/show Profile immediately from its setting in both native/default and customized navigation, keep a reachable Settings route, and render Profile as an account avatar/person rather than Settings' gear animation.

**Architecture:** Add an ordered native follow-up patch against the validated 101 input. Keep Telegram's native tab component, theme-selected colors, lens/search/badges and controller identity; make visibility independent of the presentation mode. Reuse the native account-avatar signal for one-account Profile and prevent Settings animation on the person fallback. Check live settings delivery and native controller updates, including hiding the selected Profile and rebuilding on return.

**Tech Stack:** Swift, UIKit, SwiftSignalKit, existing NebulaSettingsContract and ordered native patches.

## 1. Diagnose and specify

- [x] Inspect the actual 101 root/profile/tab source after applying all 97 patches. Found forced `showProfileTab || !showBottomBar` and Settings' animation assigned to the profile fallback. Account-avatar signal currently requires another account, so a single-account Profile falls back.
- [x] Clarify restart behavior: user confirms restarting helps **hide Profile**, not Gemini Nano or its color. Inspect settings notifications and controller refresh in addition to forced visibility.
- [x] Add regression cases for both presentation modes, all Profile/Settings visibility combinations, settings reachability and live false/true notifications. Tests should execute the same policy used by native root.

## 2. Implement native consumers

Files: canonical/mirrored `NebulaNavigationPolicy.swift`, its tests, `patches/ios/0098-profile-tab-live-visibility.patch`; native `TelegramRootController.swift` and `PeerInfoScreen.swift` consumers; `platform/ios/tools/check-bootstrap.py`.

- [x] Apply Profile visibility in all modes; retain the Settings recovery route. Hide the selected Profile safely by preserving remaining controller identity or selecting Chats. Inspect whether a deferred native update/on-return refresh is required.
- [x] Use account avatar for Profile even with one account. When no avatar is ready, use the person symbol without `TabSettings`; Settings remains a gear with its native warning/account behavior.
- [x] Compare the native tab component's theme color assignments with pinned upstream. Keep intended accent/glass rendering; only fix a proven Nebula regression. The screenshot alone does not establish a color mapping defect.

## 3. Validate and deliver

- [x] Run ordered patch/shared-contract checks and Swift policy tests; compile the actual native graph and device app. Record exact successful builds and verified archive hashes.
- [x] Add the new checkpoint to PARITY. A physical iPhone rendering/toggle check remains required unless a reachable signed device is available; do not claim that from compiler tests.

Baseline: native chat run 38112302179 and full IPA 38112304987 succeeded at ff77388. Independently verified original **IPA 101**, 12.9.2/app.nebulagram, arm64 app plus one notification extension, 88,013,148 bytes, SHA-256 `902f27958589c19f1a252b1659ddceabe31d757317d7ead8c42ffff9ba336837`. It predates this tab repair. Android-only composer/error-card revision e9853ca is building in 38115968537 / 1000490; its runtime remains independent of this iOS change.

## October 11 validation checkpoint

Final source `1c548a7527b792a7a66306af594298243cd28410` makes **all three** visibility preferences independent of appearance mode, with Settings retained when Profile is absent. Root rebuilds are coalesced to the next main-queue turn and checked again from the native tab controller's appearance callback. Existing selected-controller identity / Chats fallback is retained. Profile's native avatar pipeline accepts a single account; its person fallback never uses the Settings animation.

The actual `TabBarComponent.swift` still equals pinned upstream after only the established width/label/native-container hooks; color assignments and lens/search/badges were not changed. Ordered **98 patches / 160 native paths** and shared contracts passed locally. New policy tests cover visibility combinations, true/false live notifications and persistence in both bar modes. The actual native refresh method is executed in a deterministic Swift queue fixture (deferred/coalesced/latest-state/on-return/released-root). Prior 5aae118 bootstrap **38117144121** passed **153 XCTest cases** and that exact unchanged refresh method fixture. Final 1c548a7 bootstrap **38117464500**, native **38117465553**, and device IPA **38117468383 / 103** are in progress. Superseded native/IPA 102 were cancelled before delivery; no 102 artifact is presented as final.

Patch-format validation first caught added blank-line whitespace and Windows CRLF in the new patch; both were fixed, then strict ordered application passed. Git diff whitespace warnings for preserved native context/deletion lines in a patch payload are separate from introduced native-source whitespace, which the bootstrap rejects. No vendor source was edited.

Final bootstrap **38117464500** succeeded at 1c548a7 with **153 XCTest cases**, zero failures, the executed actual root-refresh method fixture and SDK/98 ordered patch checks. This supersedes the earlier in-progress bootstrap status and verifies the final three-independent-choice policy. Native and full device IPA 103 remain in progress.

Final fallback follow-up **92468e6cae8672415394bae154f2aa552ff707b3** also gives the person symbol an explicit 28-point configuration and pre-renders its regular/selected native theme colors with alwaysOriginal mode. The stock bar's static Image path uses tintColor nil, so leaving a plain default-size template symbol would inherit unrelated UIKit tint and differ from native avatars. Original avatar images remain untouched. Strict patch checks and final bootstrap **38118223239** passed **153 XCTest cases** plus the unchanged executed native refresh fixture. Native **38118224196** and full **IPA 104 / 38118226841** are now the required final builds. Superseded native/device jobs for 102 and 103 were cancelled; earlier source/bootstrap successes remain evidence for their unchanged policy/queue behavior, not final archives. Android inputs are still identical to e9853ca.

Final native integration **38118224196** succeeded at 92468e6. It compiled the actual Root/TabBarUI/PeerInfoScreen/SettingsUI graph with the new callback, visibility policy and sized/themed Profile fallback. Full device IPA 104 is the remaining build/verification checkpoint.

## Verified final delivery

Final runtime **92468e6cae8672415394bae154f2aa552ff707b3** passed bootstrap **38118223239 / 153 XCTest cases**, native integration **38118224196 / 1873 actions**, and full device IPA **38118226841 / 104**. Independently inspected the untouched original CI archive against its manifest: 12.9.2, app.nebulagram, NebulaGram display name, arm64 device app, exactly one Notification Service Extension, and the actual Speech privacy-purpose string. **88,019,585 bytes**, SHA-256 `6cdfdcb1f81a455a9d154d228810b2cc8fb1fda451cc921ce4c5625179055287`. Source revision matches CI and its receipt.

The unsigned IPA requires user signing. Physical iPhone toggle/appearance, installation and push delivery are not verified. Compiler/fixture results establish source and build correctness, not on-device visual acceptance. Android runtime is unchanged after e9853ca; verified APK 1000490 remains its final artifact. No further implementation/check work remains for this source checkpoint.
