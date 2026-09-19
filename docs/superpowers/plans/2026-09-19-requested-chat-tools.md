# Requested Chat Tools Implementation Plan

**Goal:** Add missing capabilities from the supplied iMe feature screenshots, preserve advertising, complete the preview-matched settings design and fix full-message Premium-emoji copying.

**Architecture:** Reuse existing native Telegram controls and Nebula AI credentials. Keep new local data account-scoped, opt-in, and out of ordinary settings export. Actual server-backed features must expose their configuration and errors; support must not promise an unstaffed service. Build working native entry points rather than a promotional feature list.

**Tech Stack:** Android Java and ordered Telegram patches; shared settings contract; existing Swift/UIKit iOS integration and Go services where appropriate.

User confirmed all features, with advertising explicitly excluded. Execute inline.

## Existing capabilities to retain

- [x] AI assistant: OpenAI, Claude, Gemini and compatible endpoints, user-supplied credentials, model selection.
- [x] Own icon packs and launcher icons.
- [x] Custom profile badge infrastructure. Final artwork is pending: supporter uses the NebulaGram logo; Mira is a star. The user rejected the first glossy generated variants and requested a cleaner modern design.
- [x] Story visibility control and native story settings.
- [x] Settings file import/export on Android and iOS (this is not automatic cloud sync).

## Implementation queue

- [ ] Finish bottom-folder styles, compact home background, and exact native settings palette/density; preserve all preview renderers.
- [ ] Restore Premium custom-emoji entities for full-message and caption copying; retain formatting and UTF-16 ranges. Verify quick copy and regular copy callers.
- [ ] Add a native tools/settings hub with genuine feature routes.
- [ ] Add system text-to-speech with stop/cancel and lifecycle cleanup.
- [ ] Add transcription for downloaded voice/video using an explicitly configured supported AI provider; expose limitations and request failures.
- [ ] Add translation actions and opt-in per-chat automatic translation with bounded requests/caching and cancellation.
- [ ] Add account-scoped local tasks with description, completion and reminders; no cross-account leakage or silent overwrites.
- [ ] Add per-chat password protection, lock-on-background, guarded opening, and preview/notification privacy handling.
- [ ] Add opt-in keyword filtering with editable rules and an obvious way to show hidden messages.
- [ ] Audit Java/native account-slot allocation and implement ten local accounts consistently.
- [ ] Implement explicit settings synchronization with a real storage/transport path, conflict handling and safe exclusion of credentials/private archives.
- [ ] Add a support entry using the actual project contact, without inventing paid priority or 24/7 support.
- [ ] Verify each feature with focused functional/security regression checks, native compilation and CI. Document platform availability and any external service prerequisites accurately.

Advertising is intentionally unchanged. Do not remove sponsored-message loading or presentation.

Both Android and iOS versions already exist and are available, as confirmed by the user. Promotional materials must not label iOS as merely in development. This does not imply that every newly requested feature is already implemented on both platforms.
