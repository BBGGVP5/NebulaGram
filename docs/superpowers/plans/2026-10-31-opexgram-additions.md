# Missing Android functions Implementation Plan

> Execute inline. User authorized all missing additions on 2026-10-31; priorities are no longer pending. Preserve unrelated iOS edits and patches 0249/0250.

**Goal:** Complete the missing Android capabilities from the supplied beta-10 feature list, expose usable controls and verify integrations.
**Architecture:** Extend existing Nebula overlay components with small ordered upstream hooks. Prefer native Telegram operations for sending, permissions, selecting, history and scheduling. Keep expensive file/network operations off UI; make operations explicit and cancellable; preserve account and topic boundaries.
**Tech Stack:** Java Android overlay, Telegram 13.1.1, native patches, Python/Java regression harnesses, GitHub Android CI.

## Stages

- [x] Finish function-by-function source audit in `docs/OPEXGRAM-BETA10-AUDIT-2026-10-31.md`; distinguish existing implementations from missing capabilities and upstream fixes requiring reproduction.
- [x] Fonts: extend `NebulaFont.java`, add `NebulaFontFragment.java`, route `NebulaShapeFragment.java` to it. Import bounded TTF/OTF using system document picker, validate before atomic installation, offer preview/reset, preserve monospace and recover from invalid files.
- [x] Selection: add preference and remove the 100-message UI selection limit in `ChatActivity` when enabled. Preserve report/forward/protocol limits; test boundary behavior.
- [x] History tools: add a per-chat screen for history volume selection, AI search/summary, local text/JSON export and confirmed deletion of own messages. Fetch paginated history with account/topic boundaries, cancellation and permission checks; avoid claiming media export if unsupported.
- [x] Slow mode queue: use native scheduled delivery with correctly spaced times, durable account/chat bookkeeping and accessible queue management. Never bypass server slow mode or silently resend failed messages.
- [x] Appearance: extend edge compression to messages without overwriting animation-owned transforms; implement optional media ambient light, theme palette variants and text animation controls where missing.
- [x] Remaining parity: registration date fallback, bot settings, compact settings, swipe/quote/folder changes, Material header/search; audit upstream QR/gift/forum/video fixes and fix demonstrable defects.
- [ ] Run focused behavior tests and ordered patch checks. Build Android from an isolated reviewed commit; fix CI failures. Record exact results and any feature limitations.

## Verification strategy

Reuse passing header regressions unless affected. Add behavioral tests for file limits/invalid font rollback, selected count boundary and server batches, queue scheduling, history pagination/privacy/account switching, AI empty output, palette and drawing opt-outs. Validate native Java syntax plus real APK dependency compilation. Device appearance remains separate from code-level checks.

## Decisions and verification so far

- Android scope from the supplied list. Existing unrelated iOS settings edits are preserved in the workspace and excluded from the release branch. Catalogs/generated resources/tests are updated independently in the workspace and clean release baseline (counts differ by an existing avatar setting).
- Release worktree: `build/opex-additions-20261031/release`, branch `codex/android-opex-additions-20261031`, based on `09f4953` (includes the previous recording fix). Targeted native files: `build/opex-additions-20261031/native`, reconstructed from pin `3de37c8cfb039452be7398909461da6288babf15` with patches through 0250; subsequent edits saved as 0251–0256.
- File manifest for owned paths: `build/opex-additions-20261031/owned-paths.json`. Shared catalog and contract test are maintained separately to avoid publishing unrelated iOS changes.
- Implemented limits: queue supports ordinary text, preserving server slow mode and native scheduled-message management; export contains text/captions/metadata, not media files; bot business lookup requires a connection ID; no new bot-token login flow; date fallback can report insufficient data instead of inventing a date.
- `check-font-import.py`: valid sfnt signatures, all truncated lengths, oversized input and invalid/unsigned table offsets pass.
- `check-chat-history-policy.py`: bounded and all-history pagination, 100000 messages, 6000 spaced queue slots, timestamp overflow and text fade lifecycle pass.
- `check-history-session.py`: executes production session with a deferred transport; topic isolation, deduplication, consumer backpressure, cancellation, account change and errors pass.
- `check-selection-actions.py`: native eligible selection still caps at 100 by default; opt-in selects 130, report mode retains cap; forward routing and native footer checks pass.
- `check-bot-api.py`: schema-level request/response field-order tests pass for profiles, admin rights and business connections. Not a live bot acceptance test.
- `check-theme-toggle.py`: 40038 checks pass, including alternate Monet resources and older Android opt-out.
- `check-settings-root.py`, `check-settings-routes.py`, `check-settings-contract.py`, `check-shape.py`, `check-feature-policy.py`: pass with new routes and registered keys.
- Header regression reuse on final targeted native tree: 117120 geometry cases, 36 load notifications, 9680 title frames, 716320 story trajectories and supporting motion checks pass.
- `check-upstream-series.py android`: all 251 patches through 0256 apply without changing vendor.
- Native Java syntax parsed; full dependency/type validation is pending APK build.
- First CI `41174082793` failed the isolated theme fixture because it omitted the new real palette class. Fixture updated and tests extended; second CI `41174633394` passed regressions and reached application compilation. Further code completion requires a final exact-commit build.

## Sources and remaining acceptance

Bot-only methods follow the public Telegram schema fetched from `https://raw.githubusercontent.com/telegramdesktop/tdesktop/dev/Telegram/SourceFiles/mtproto/scheme/api.tl` (local read-only reference in build directory). No private bot token or user messages were used in tests. Imported foreign code was not incorporated.

No phone visual test, real AI request, real deletion, real bot settings mutation or real slow-mode send was performed. Validate those product flows on test accounts/devices after a successful APK build. Native gift purchases and server flags were not modified without a reproduced defect.
