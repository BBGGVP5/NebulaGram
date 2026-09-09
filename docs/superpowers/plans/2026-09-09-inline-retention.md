# Inline deleted-message retention Implementation Plan

**Goal:** Keep received eligible deleted messages in native chronology on Android/iOS, with icon/muted styling and local-only scoped cleanup; complete native Privacy UI.
**Architecture:** Filter only remote deletion updates after durably marking locally present messages. Keep original IDs, grouping, text and media references. Never send/reinsert to Telegram server. Store bounded local deletion metadata, exclude copy-protected/service/auth messages; secret and expiring retention require separate default-off switches in addition to the master switch. Native cached media remains subject to normal cache eviction, not a guaranteed permanent download. Cleanup only targets IDs recorded as retained, never ordinary history. Preserve unrelated changes and no release dispatch.
**Tech Stack:** Android Java/SQLite/Keystore, iOS Swift/Postbox/UIKit, existing encrypted metadata archive.

- [ ] Android: capture-before-UI ordering on storage queue, load marker index when DB opens, filter both deletion UI and DB arrays, update visible retained MessageObjects; retain native attachments/albums.
- [ ] Android: use marker in ChatMessageCell time and muted render; three-dot per-chat cleanup; Privacy all-account-local cleanup and icon picker. Cleanup excludes shared native media files.
- [ ] iOS: local Postbox tag and native message update, filter remote deletion operations, preserve media; index/cleanup through TelegramCore; message status styling.
- [ ] iOS: grouped native settings/Privacy UI, icon editor, retained-cache cleanup, counter consumer checks, auth/NebulaLink compile guards. Unsupported Android-only settings must be identified, not dummy toggles.
- [ ] Regression tests for ordering, retention eligibility, scope, dedup, bounds, clearing and marker render; Android compilation and iOS source/native checks where available.

Do not claim full parity or fix for the unverified iOS 27 black startup report. Authentication and notification delivery still depend on valid provisioning/Telegram/APNs configuration.

## Current source verification (2026-09-09)
- Native retention, per-chat/current-account cleanup and icon controls are implemented in Android/iOS source; secret and expiring retention have separate default-off switches. Device behavior is not yet validated.
- All 33 Android regression scripts pass. The retention harness executes 512 eligibility combinations and eight production icon-setter cases with Telegram emoji-range fixtures (not font-rendering tests).
- Android custom markers now preserve complete Telegram emoji ranges, including ZWJ families/flags/skin tones; an empty input keeps the previous marker. The local list and message timestamp use the marker, not a Deleted label.
- iOS uses up to four Swift grapheme clusters. Bootstrap validates eight ordered patches, 26 upstream paths and six mirrored contract files. This is not a native UIKit build result.
- Current remote iOS runs at b081fe4 predate these uncommitted changes and must NOT be cited as validation of this implementation. Native Swift compilation, device rendering, unread-counter effects and crash consistency of the metadata/native-database pair still need validation before release.
- No new release job dispatched. Full iOS settings parity and the unverified startup black screen remain outside verified completion.
