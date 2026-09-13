# Inline forward drafts and protected-copy transport Implementation Plan

> **For agentic workers:** Execute the steps below in this task; preserve unrelated Nimbo work.

**Goal:** Replace the modal forward editor with Telegram's composer and prevent rejected protected-content forward RPCs.

**Architecture:** Isolated local copies use the native text/caption field and its validation, but never remote edit APIs. Opt-in protected forwarding uses ordinary messages (optional explicit source text) and cached attachment reuploads, after whole-batch permission and file validation.

**Tech Stack:** Android Java, upstream patch 0091, Python/JDK regression harness, Gradle Java compilation.

### Tasks
- [x] Replace `NebulaForwardEditing.edit` dialog with detached copies and native inline editing. Preserve the existing comment, entities and albums; cancel must leave originals intact; applying must not send.
- [x] Add `ChatActivityEnterView.editNebulaForwardCopy` with a local callback intercepted after native entity/length validation, before remote edit calls. Clear the callback on cancel. Never expose a local copy as a remote editing target. Use null grouped messages to avoid changing original album metadata.
- [x] Add a follow-up patch for ChatActivity menu, close/back handling and SendMessagesHelper copy routing. Keep normal forwarding unchanged.
- [x] Detect raw source protection independently of UI overrides. Validate the complete selection before queueing, retain paid/scheduled/topic parameters, send downloaded protected photos/documents as fresh uploads. Reject incomplete caches and secret/expiring media. Label attributed copies explicitly as source text.
- [x] Correct protection setting explanation. Extend `scripts/check-forward-editing.py` with opt-in, raw protection and no-partial-send cases; add structural guards for inline interception and cancellation.
- [x] Run `python scripts/check-forward-editing.py`, local `:TMessagesProj:compileStandaloneJavaWithJavac`, and patch applicability/diff checks. Inspect only task-owned changes; commit/push with `[skip ci]` without dispatching APK/IPA builds.

### Verification boundaries
No connected device; verify compilation and executable helper tests, but do not claim on-device visual or live Telegram delivery testing. Telegram authentic forwarded headers cannot be retained for protected sources: https://core.telegram.org/method/messages.forwardMessages .


## Result / manual checks still required
- Text is an ordinary editable composer draft (any pre-existing comment is retained), not a separate modal.
- Media captions and batches use the native composer checkmark, then the existing explicit Send action. Originals and album group objects are not passed to remote editing APIs.
- Protected text/photos/videos/documents use ordinary copies; source text is optional. Entire batches are preflighted; documents use isolated staging files, photos get fresh upload sizes. No background decoding on the UI thread.
- Automated verification: `python scripts/check-forward-editing.py`, `python scripts/check-protected-copies.py`, forward/reverse `git apply --check`, local Java compilation. No APK/IPA workflow dispatched.
- Device checklist: single text with an existing comment; cancel and apply a photo caption; multi-item album with mixed captions; source-message unchanged; protected text with/without source; fully downloaded protected photo/video/document; missing attachment; scheduled/paid/topic delivery. Real account/device delivery has not been tested in this environment.
