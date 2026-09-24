# Requested Chat Tools Implementation Plan

**Goal:** Add missing capabilities from the supplied iMe feature screenshots, preserve advertising, complete the preview-matched settings design and fix full-message Premium-emoji copying.

**Architecture:** Reuse existing native Telegram controls and Nebula AI credentials. Keep new local data account-scoped, opt-in, and out of ordinary settings export. Actual server-backed features must expose their configuration and errors; support must not promise an unstaffed service. Build working native entry points rather than a promotional feature list.

**Tech Stack:** Android Java and ordered Telegram patches; shared settings contract; existing Swift/UIKit iOS integration and Go services where appropriate.

User confirmed all features, with advertising explicitly excluded. Execute inline.

## Existing capabilities to retain

- [x] AI assistant: OpenAI, Claude, Gemini and compatible endpoints, user-supplied credentials, model selection.
- [x] Own icon packs and launcher icons.
- [x] Android profile badge artwork: supporter uses the flat generated NebulaGram logo; the existing `star` service key displays the user's latest Mira sparkle composition supplied on 2026-09-20. Bundled 128px transparent artwork is shared by profile name spans, settings and the grant selector. iOS has no Nebula profile badge integration yet.
- [x] Story visibility control and native story settings.
- [x] Settings file import/export on Android and iOS (this is not automatic cloud sync).

## Implementation queue

- [ ] Finish bottom-folder styles, compact home background, and exact native settings palette/density; preserve all preview renderers.
- [ ] Restore Premium custom-emoji entities for full-message and caption copying; retain formatting and UTF-16 ranges. Verify quick copy and regular copy callers.
- [x] Add a native tools/settings hub with genuine feature routes.
- [x] Add system text-to-speech with stop/cancel and lifecycle cleanup.
- [x] Add transcription for downloaded voice/video using an explicitly configured supported AI provider; expose limitations and request failures.
- [x] Add translation actions and opt-in per-chat automatic translation with bounded requests/caching and cancellation.
- [x] Add account-scoped local tasks with description, completion and reminders; no cross-account leakage or silent overwrites.
- [ ] Add per-chat password protection, lock-on-background, guarded opening, and preview/notification privacy handling.
- [x] Add opt-in keyword filtering with editable rules and an obvious way to show hidden messages.
- [x] Audit Java/native account-slot allocation and implement ten local accounts consistently.
- [ ] Implement explicit settings synchronization with a real storage/transport path, conflict handling and safe exclusion of credentials/private archives.
- [ ] Add a support entry using the actual project contact, without inventing paid priority or 24/7 support.
- [ ] Verify each feature with focused functional/security regression checks, native compilation and CI. Document platform availability and any external service prerequisites accurately.

Advertising is intentionally unchanged. Do not remove sponsored-message loading or presentation.

Both Android and iOS versions already exist and are available, as confirmed by the user. Promotional materials must not label iOS as merely in development. This does not imply that every newly requested feature is already implemented on both platforms.

2026-09-24 Android implementation and limitations: [chat tools](../../ANDROID-CHAT-TOOLS.md). Transcription currently uses Gemini and a 14 MB file limit. Per-chat passwords and cloud sync remain open.
