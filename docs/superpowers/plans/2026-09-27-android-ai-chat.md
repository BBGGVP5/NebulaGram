# Android AI Chat Implementation Plan

**Goal:** Replace the request form with a conversational AI surface, show live Nano downloads, offer an optional home AI shortcut, and repair launcher labels.

**Architecture:** One reusable NebulaAiChatView owns an ephemeral conversation, cancellable request and message rendering. Both the settings tab and NebulaAiSheet use it. Existing provider adapters and opt-in history remain the transport/storage boundary. Nano status remains tied to fragment lifecycle; AICore DOWNLOADABLE requires an explicit tap, DOWNLOADING schedules a foreground recheck.

**Tech Stack:** Java, Android Views, Telegram BottomSheet and FragmentFloatingButton, ML Kit GenAI, existing encrypted provider credentials.

- [x] Add NebulaAiConversation.java for bounded successful turns, NebulaAiMarkdown.java for safe native spans, and NebulaAiChatView.java for bubbles, composer, suggestions, animated waiting, cancellation and copy.
  Request lifecycle: `task = new NebulaAiClient(); worker = new Thread(...);` deliver only when `active == task && !disposed`; close calls `task.cancel(); worker.interrupt();`. Keep previous successful turns only, bounded by Nano's 10,000 and remote 50,000 character limits. Never send on open or on suggestion tap.
- [x] Replace the settings request page with the shared chat, retain connection/instructions and model list operations. Use one weighted page host so the composer stays visible above the keyboard.
- [x] Show a ProgressBar while checking/downloading, retain callback byte totals, schedule `checkNano(false)` after DOWNLOADING and cancel scheduled work on pause/destroy/configuration change. Update the existing status widgets without rebuilding input fields.
- [x] Create NebulaAiSheet.java using a rounded, keyboard-resizing BottomSheet and the shared chat. Add opt-in `home_shortcut` in AI preferences. Patch the existing camera floating button: retain its native glass background, replace only the glyph and click target when enabled. Settings remain reachable from the sheet; disabled/unconfigured AI opens setup without sending text.
- [x] Patch AppIconsSelectorCell.java to resolve bundled icon names with `getContext().getString(icon.title)` instead of Telegram's remote-string lookup. Preserve selection, rounded previews and launcher aliases.
- [x] Run executable conversation/Markdown/Nano-state checks, model/credential/transport checks and patch validation. Run Android compilation using the existing clean build workflow, preserving the dirty vendor checkout.

Validation covers: multiple requests and failures, stale callbacks after close, prompt bounds and context pruning, download progress without reentry, switching models, labels and keyboard-safe sheet layout. Physical-device animation and Nano download acceptance remain separate from JVM/CI checks.


Validation evidence: shared chat/settings/sheet classes compile against Android SDK and cached Telegram classes (resource identifiers supplied separately; full app compilation is CI). Executable checks passed for conversation/Markdown, Nano lifecycle/error handling, 600 tab transitions, 65 availability cases and four provider protocols. The ordered patch applies to pinned upstream and home visibility tests cover 32 combinations. No device is attached for visual, keyboard or real AICore acceptance.
