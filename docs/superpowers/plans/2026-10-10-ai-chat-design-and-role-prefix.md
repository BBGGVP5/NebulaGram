# Nebula AI chat design and role-prefix repair

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the unintended leading Assistant label and make the Android/iOS conversation screen behave and read like an AI chat, while keeping Nebula styling.

**Architecture:** Apply a narrowly scoped reply cleaner to regular chat display, copy, successful context and new saved turns, including streaming/restore. Do not change answer language or transform editor/translation output. Reuse existing request cancellation, provider selection, history opt-in and markdown rendering; replace bulky controls with an accessible compact toolbar and reply actions.

**Tech Stack:** Java Android views/vector drawables, Swift/UIKit, Foundation contract/XCTest, existing CI and ordered native overlays.

---

User clarified the reported language issue is **only `Assistant:`**, not the answer language. The writing-plans skill was already applied; execute inline because its optional execution plugins are unavailable. Previous iOS port runtime db19c74 passed native 38053788005 and IPA 38053790313; independently verified unsigned IPA 100 is the baseline, not evidence for this subsequent chat change.

## 1. Reply boundary

Files: new Android `NebulaAiChatReply.java`; canonical/mirrored Swift `NebulaAiChatReply.swift`; Java `scripts/check-ai-chat.py`; new Swift `NebulaAiChatReplyTests.swift`.

- [ ] Test leading `Assistant:`, markdown-bold role labels, incremental partial labels, empty role-only result, old saved turns, Cyrillic/emoji and formatting after a role line. Preserve quoted/backticked/inner occurrences and all non-label text. Example: `clean("Assistant: 1 + 1 = 2! 😊", false)` equals `"1 + 1 = 2! 😊"`.
- [ ] Keep the normal conversation format bounded and add a context-only/direct-answer hint where it fits. Do not prepend an unconditional language instruction or call another generation service.
- [ ] Clean at the regular chat boundary before new history/context writes and when restoring old turns. Hide incomplete streaming role markers without losing legitimate final text such as `"A"`. Editor/translation/summarization text stays outside this cleaner.

## 2. Android conversation UI

Files: `NebulaAiChatView.java`, `NebulaAiFragment.java`, new `res/drawable/nebula_ai_chat_new.xml`, `nebula_ai_chat_send.xml`.

- [ ] Replace the large model/history/new-chat cards with one compact toolbar: history/count, readable model/status, 44 dp new-chat icon. Draw a distinct square/pen icon matching our outline style; title the screen Nebula AI.
- [ ] Give user bubbles a consistent readable width/alignment. Keep assistant markdown selectable on the plain surface. Put Copy in a small 44 dp icon action with tooltip/accessibility text and reversible copied feedback; keep response start anchored and streaming scrolling stable.
- [ ] Improve the composer with rounded border, upward send arrow, clear Stop state and disabled-empty state. Account for multiline/IME/RTL/larger text; preserve draft, context isolation, cancellation and history opt-in.
- [ ] Keep suggestion chips readable and restrained. No automatic sharing, requests, account/Premium changes or new paid generation behavior.

## 3. Native iOS counterpart

Files: `NebulaAiChatController.swift`, contract reply cleaner/mirror.

- [ ] Use the same compact model/history/new-chat hierarchy with native symbols and >=44-point controls. Make the model row open connection settings. Keep action/language controls when used as a transformation tool; minimize them for ordinary chat.
- [ ] Align user bubbles, markdown answer spacing, small horizontal Copy controls, clipboard feedback and composer placeholder/Stop/empty state. Retain keyboard layout guides, native sheets, Dynamic Type and explicit Apply when used in an editor.
- [ ] Add real UIKit SDK checks for any isolated new controls and compile the actual SettingsUI graph. Do not infer native type correctness from parser-only checks.

## 4. Validation and delivery

- [ ] Run the Java context/reply/markdown fixtures, appropriate Android SDK compilation, shared settings checks and local iOS ordered checks/mirror generation. Run Swift reply tests on macOS.
- [ ] Build the Android APK and actual iOS app; repair real compiler errors. Independently verify final artifacts and source revisions. Never offer a failed or superseded archive as the new chat build.
- [ ] Record exact evidence and physical acceptance limits. Device QA is currently unavailable unless the user's previously unavailable phone becomes reachable; no physical appearance or live model-quality claim until tested.

Review checkpoints: provider/history remain reachable; no duplicate role label during streaming or restore; no answer-language override; quoted labels/code/indentation survive; copy uses displayed clean reply; empty send is disabled while Stop stays available; long text/keyboard/rotation/RTL keep controls reachable.
