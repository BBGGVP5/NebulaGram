# Local edit history implementation plan

**Goal:** Show previously received text and formatting versions without Telegram Premium or extra server requests.

**Architecture:** Capture a previous loaded message immediately before its native edit transaction replaces it. Android stores the original serialized message in its account SQLite database; iOS stores Codable text/entities in a reserved Postbox item-cache collection, also inside its account database. Keep at most 32 previous versions per message and 1,000 messages per account. Defaults are off. Secret, expiring, service, copy-protected and authentication-service messages are excluded. Disabling preserves existing versions; explicit clearing removes local history only.

**Tech Stack:** Java/Telegram SQLite and TL serialization, Swift/Postbox Codable, existing native settings and message tool controllers.

Execution is inline in the current workspace. No account credentials, native session data or message export is added to settings backups.

## Storage and transaction hooks

- [x] Add `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaEditHistory.java`: `capture(account, incoming)` runs on the existing storage queue, compares text plus serialized entities, saves the previous row before `REPLACE`, and prunes oldest versions/messages. UI reads/clear are queued; errors cannot abort Telegram's update.
- [x] Add `patches/android/0224-local-edit-history.patch` at the default-message branch of `MessagesStorage.putMessagesInternal`; no scheduled, quick-reply, welcome or encrypted-message storage changes.
- [x] Add `platform/ios/overlay/submodules/TelegramCore/Sources/State/NebulaEditHistory.swift`: use Postbox collection 126 and a 16-byte full peer/namespace/message key, reserve an index key for the bounded message list, and use the same transaction for the old-version capture and update.
- [x] Add `patches/ios/0112-local-edit-history.patch` to the `.EditMessage` operation before `transaction.updateMessage`; use the already loaded previous message and native protection data.

## User controls

- [x] Add `NebulaEditHistoryFragment.java` and `NebulaEditHistoryController.swift`: per-account switch, precise scope/limit explanation, newest-first previous versions, current text separately, explicit per-message/all clear. Render text/entities without opening links or sending messages.
- [x] Expose the screen in privacy settings and selected-message tools on both platforms. Keep account ownership fixed when the selected account changes.

## Verification

- [x] Execute production eligibility/comparison logic with identical edit/text/formatting/replay cases; include Unicode UTF-16 ranges and exact entity preservation.
- [x] Exercise actual storage operations with account isolation, deduplication, chronological versions, bounds, restart, clear, disabled/protected modes and failure paths. Check both native hooks before replacement and that update/send handlers remain native.
- [ ] Run all ordered patches, parser/bootstrap/SDK checks, then full APK/IPA builds. Distinguish source tests from physical-device acceptance.

Commands: `python scripts/check-edit-history.py`; `python scripts/check-edit-history.py --swift`; `python scripts/check-native-java-syntax.py build/android-1302-integrated-20261015`; `python platform/ios/tools/check-bootstrap.py --tree build/ios-13-upstream-20261013`.
