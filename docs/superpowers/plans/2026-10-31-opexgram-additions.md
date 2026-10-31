# Missing Android functions Implementation Plan

> Execute inline. User authorized all missing additions on 2026-10-31; priorities are no longer pending. Preserve unrelated iOS edits and patches 0249/0250.

**Goal:** Complete the missing Android capabilities from the supplied beta-10 feature list, expose usable controls and verify integrations.
**Architecture:** Extend existing Nebula overlay components with small ordered upstream hooks. Prefer native Telegram operations for sending, permissions, selecting, history and scheduling. Keep expensive file/network operations off UI; make operations explicit and cancellable; preserve account and topic boundaries.
**Tech Stack:** Java Android overlay, Telegram 13.1.1, native patches, Python/Java regression harnesses, GitHub Android CI.

## Stages

- [ ] Finish function-by-function source audit in `docs/OPEXGRAM-BETA10-AUDIT-2026-10-31.md`; distinguish existing implementations from missing capabilities and upstream fixes requiring reproduction.
- [ ] Fonts: extend `NebulaFont.java`, add `NebulaFontFragment.java`, route `NebulaShapeFragment.java` to it. Import bounded TTF/OTF using system document picker, validate before atomic installation, offer preview/reset, preserve monospace and recover from invalid files.
- [ ] Selection: add preference and remove the 100-message UI selection limit in `ChatActivity` when enabled. Preserve report/forward/protocol limits; test boundary behavior.
- [ ] History tools: add a per-chat screen for history volume selection, AI search/summary, local text/JSON export and confirmed deletion of own messages. Fetch paginated history with account/topic boundaries, cancellation and permission checks; avoid claiming media export if unsupported.
- [ ] Slow mode queue: use native scheduled delivery with correctly spaced times, durable account/chat bookkeeping and accessible queue management. Never bypass server slow mode or silently resend failed messages.
- [ ] Appearance: extend edge compression to messages without overwriting animation-owned transforms; implement optional media ambient light, theme palette variants and text animation controls where missing.
- [ ] Remaining parity: registration date fallback, bot settings, compact settings, swipe/quote/folder changes, Material header/search; audit upstream QR/gift/forum/video fixes and fix demonstrable defects.
- [ ] Run focused behavior tests and ordered patch checks. Build Android from an isolated reviewed commit; fix CI failures. Record exact results and any feature limitations.

## Verification strategy

Reuse passing header regressions unless affected. Add behavioral tests for file limits/invalid font rollback, selected count boundary and server batches, queue scheduling, history pagination/privacy/account switching, AI empty output, palette and drawing opt-outs. Validate native Java syntax plus real APK dependency compilation. Device appearance remains separate from code-level checks.
