# Privacy and Native iOS Polish Implementation Plan

> Execute inline; execution/subagent skills are unavailable.

**Goal:** Fix reported Android/iOS presentation regressions, make subscription import independent of connection, and add a clearly scoped local deleted-message archive under Privacy.
**Architecture:** Ordered native hooks plus isolated platform adapters. Preserve Telegram data, authentication handlers, peer restrictions by default and native unread counts. Archive is opt-in, local, bounded, excludes secret/ephemeral/protected messages; no client detection in this first version (user confirmed).
**Tech Stack:** Java Android, Swift/UIKit, Telegram Postbox, Go core, shared settings contract, Python/JVM/Go/Swift CI tests.

## Work order
- [ ] Android: suppress background menu gaps during swipe-back foreground; hide animated counters and measured badge space; refresh visibility on preference changes.
- [ ] iOS: target actual ChatListUI HorizontalTabsComponent (old bootstrap only patched legacy peer-selection tabs); observe settings and refresh native layout.
- [ ] Country row: bound text and highlight inside rounded card, reserve chevron space; no changes to phone input validation.
- [ ] Own phone/code animations, stop when offscreen/background/Reduce Motion; no access to authentication input.
- [ ] Core onboarding.import: separate import from starting first server; iOS selection remains usable when imported endpoint cannot connect; tests with failing engine.
- [ ] Privacy/archive: inspect remote deletion paths on both platforms; capture only locally available eligible message text before deletion, bounded retention and explicit clear. No bypass of peer-level protection or guaranteed background capture claims.
- [ ] Settings parity: expose implemented native controls only; document remaining consumers and useful iOS-specific options instead of nonfunctional toggles.
- [ ] Verify patch series, Android regressions/compile, Go API tests, Swift bootstrap/native CI when dispatched. Black-screen report has no device/build/log; cannot claim resolved without reproduction.

## Clarifications: inline retention and bottom-tab identity
- Deleted messages must remain at their chronological position with native text/media renderers, a configurable marker and muted styling, not only in a separate text list. Current text-only capture is an unfinished intermediate, not the requested result. No network re-send. Preserve already-received media; exclude secret/expiring/protected messages. Per-chat and per-account clearing must remove retained copies only.
- Settings uses an account photo only while Profile is hidden; with both visible Settings uses its gear and Profile its avatar. A user without a photo keeps the gear fallback. Preview rows never display the account photo.

### Tab icon task
Files: `NebulaBottomBar.java`, new ordered Android patch for `GlassTabView.java` and `MainTabsActivity.java`, `scripts/check-navigation.py`, `scripts/check-settings-tab-icon.py`.
- [x] Add executable truth-table regression: `settingsUsesAvatar(hasPhoto)` equals `hasPhoto && !tabEnabled(TAB_PROFILE)` for all visibility combinations.
- [x] Replace Settings factory early-return with a tagged Settings view retaining both native gear and optional avatar child; switch visibility in `nebulaUpdateSettingsIcon(account)` on initial creation, tab-label refresh and account-photo updates.
- [x] Keep the same view, click handler and pager destination during changes; apply label layout to the currently visible icon.
- [x] Run navigation/state tests, ordered patch verification and Android Java compilation. Do not dispatch a build in this change.

## Handoff / incomplete scope
Native Android compilation passed for the current draft before the rendering optimization; the icon truth table and live transitions passed, including no-photo/account changes. iOS source bootstrap and Go core tests pass; UIKit/Swift native compilation and the black startup screen are not verified here.
The deleted-message feature remains a text-only draft. It does NOT yet satisfy the corrected requirement of inline retained text/media. Before dispatching a release, replace it with native in-history retention, implement per-chat/per-account clearing, wire iOS Privacy UI, and add archive persistence/privacy/media lifecycle tests. Do not present this draft as finished retention or full iOS settings parity.

## Superseding handoff (2026-09-09)
The text-only draft mentioned above has now been replaced in source by native in-history retention; see `2026-09-09-inline-retention.md` for current implementation and test limits. Android/iOS Privacy controls and icon editors are wired. Secret/expiring retention is explicitly opt-in via separate switches. This does not establish full iOS settings parity or device-tested retention.
