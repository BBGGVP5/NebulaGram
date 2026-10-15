# Local chat pins implementation plan

**Goal:** Pin additional cloud chats on this device without modifying Telegram's remote pin list or limits.

**Architecture:** Keep stable ordered peer IDs by owning Telegram user and native root/archive/filter scope. Native server pins remain first, then local pins, then the native remainder. Android's existing comparators read cached ranks without changing `Dialog.pinned` or folder server data. iOS adds local peers to the view predicate only, retaining native archive/additional entries; standard reorder filters local peers out of the remote request. Local storage is independent of exported settings. Archived/deleted chats are not moved back or joined.

**Tech Stack:** Java/SharedPreferences/TL input-peer metadata, Swift/UserDefaults/Postbox view predicates, native chat context menus.

Execution is inline. No source files from forks are copied.

- [x] Implement immutable ordered storage with 1,000 pins per account, exact 64-bit IDs, independent scopes, idempotent pin/unpin, oldest-free ordering, failed-write behavior and clear.
- [x] Android: integrate after native pin comparison in `MessagesController`, add explicit local context action in `DialogsActivity`, restore old cached peers through bounded read-only `getPeerDialogs` batches, and retain server-pinned metadata unchanged.
- [x] iOS: integrate only chat-list view predicates, keep root archive/additional entries, observe local revisions, and preserve ordinary loading and pagination. Exclude native server pins from the local additions. Scope by group/filter; drop removed/archived peers from root projection.
- [x] iOS: separate local action from ordinary Pin; keep local reorder in local storage and filter it from `_internal_reorderPinnedItemIds` remote changes.
- [x] Add per-account controls to disable/clear local pins and explain their device-only behavior.
- [ ] Execute production storage, ordering and view/payload adapters: server/local merge, duplicates, sparse IDs, account/folder isolation, restart, archive/removal, reorder and bounds. Replay all native patches and compile full applications; report device acceptance separately.

Files: Android `NebulaLocalPins.java`, `NebulaLocalPinsFragment.java`, patch `0225-local-chat-pins.patch`; iOS `TelegramCore/Sources/State/NebulaLocalPins.swift`, `SettingsUI/Sources/NebulaLocalPinsController.swift`, patch `0113-local-chat-pins.patch`; tests `scripts/check-local-chat-pins.py`.
