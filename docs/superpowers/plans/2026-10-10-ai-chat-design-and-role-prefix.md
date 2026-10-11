# Nebula AI chat design and role-prefix repair

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Remove the unintended leading Assistant label and make the Android/iOS conversation screen behave and read like an AI chat, while keeping Nebula styling.

**Architecture:** Apply a narrowly scoped reply cleaner to regular chat display, copy, successful context and new saved turns, including streaming/restore. Do not change answer language or transform editor/translation output. Reuse existing request cancellation, provider selection, history opt-in and markdown rendering; replace bulky controls with an accessible compact toolbar and reply actions.

**Tech Stack:** Java Android views/vector drawables, Swift/UIKit, Foundation contract/XCTest, existing CI and ordered native overlays.

---

User clarified the reported language issue is **only `Assistant:`**, not the answer language. The writing-plans skill was already applied; execute inline because its optional execution plugins are unavailable. Previous iOS port runtime db19c74 passed native 38053788005 and IPA 38053790313; independently verified unsigned IPA 100 is the baseline, not evidence for this subsequent chat change.

## 1. Reply boundary

Files: new Android `NebulaAiChatReply.java`; canonical/mirrored Swift `NebulaAiChatReply.swift`; Java `scripts/check-ai-chat.py`; new Swift `NebulaAiChatReplyTests.swift`.

- [x] Test leading `Assistant:`, markdown-bold role labels, incremental partial labels, empty role-only result, old saved turns, Cyrillic/emoji and formatting after a role line. Preserve quoted/backticked/inner occurrences and all non-label text. Example: `clean("Assistant: 1 + 1 = 2! 😊", false)` equals `"1 + 1 = 2! 😊"`.
- [x] Keep the normal conversation format bounded and add a context-only/direct-answer hint where it fits. Do not prepend an unconditional language instruction or call another generation service.
- [x] Clean at the regular chat boundary before new history/context writes and when restoring old turns. Hide incomplete streaming role markers without losing legitimate final text such as `"A"`. Editor/translation/summarization text stays outside this cleaner.

## 2. Android conversation UI

Files: `NebulaAiChatView.java`, `NebulaAiFragment.java`, new `res/drawable/nebula_ai_chat_new.xml`, `nebula_ai_chat_send.xml`.

- [x] Replace the large model/history/new-chat cards with one compact toolbar: history/count, readable model/status, 44 dp new-chat icon. Draw a distinct square/pen icon matching our outline style; title the screen Nebula AI.
- [x] Give user bubbles a consistent readable width/alignment. Keep assistant markdown selectable on the plain surface. Put Copy in a small 44 dp icon action with tooltip/accessibility text and reversible copied feedback; keep response start anchored and streaming scrolling stable.
- [x] Improve the composer with rounded border, upward send arrow, clear Stop state and disabled-empty state. Account for multiline/IME/RTL/larger text; preserve draft, context isolation, cancellation and history opt-in.
- [x] Keep suggestion chips readable and restrained. No automatic sharing, requests, account/Premium changes or new paid generation behavior.

## 3. Native iOS counterpart

Files: `NebulaAiChatController.swift`, contract reply cleaner/mirror.

- [x] Use the same compact model/history/new-chat hierarchy with native symbols and >=44-point controls. Make the model row open connection settings. Keep action/language controls when used as a transformation tool; minimize them for ordinary chat.
- [x] Align user bubbles, markdown answer spacing, small horizontal Copy controls, clipboard feedback and composer placeholder/Stop/empty state. Retain keyboard layout guides, native sheets, Dynamic Type and explicit Apply when used in an editor.
- [x] Add real UIKit SDK checks for any isolated new controls and compile the actual SettingsUI graph. Do not infer native type correctness from parser-only checks.
- Native completion: `38112302179` succeeded at ff77388, including the real SettingsUI/TelegramUI graph. This supersedes the earlier in-progress native status; SDK checks and the full integration compiler both passed.

## 4. Validation and delivery

- [x] Run the Java context/reply/markdown fixtures, appropriate Android SDK compilation, shared settings checks and local iOS ordered checks/mirror generation. Run Swift reply tests on macOS.
- [x] Build the Android APK and actual iOS app; repair real compiler errors. Independently verify final artifacts and source revisions. Never offer a failed or superseded archive as the new chat build.
- [x] Record exact evidence and physical acceptance limits. Device QA is currently unavailable unless the user's previously unavailable phone becomes reachable; no physical appearance or live model-quality claim until tested.

Review checkpoints: provider/history remain reachable; no duplicate role label during streaming or restore; no answer-language override; quoted labels/code/indentation survive; copy uses displayed clean reply; empty send is disabled while Stop stays available; long text/keyboard/rotation/RTL keep controls reachable.

## Validation checkpoint — October 11

- Runtime `ff7738894645259a6bd542214ee3d7c2eb8a877a` implements both platform chat boundaries, compact controls and the UIKit user bubble. Existing editor/translation output and answer-language choice remain outside the role cleaner.
- Java context/reply/Markdown fixtures passed; SDK 36 `aapt2` compiled the four new vectors. A separate fixture compiled the actual `NebulaAiChatView`, reply, context and Markdown sources against SDK 36. Telegram shell/theme and generation providers were stubbed, with no account or network access.
- After renewed explicit permission, the fixture ran on the connected Pixel. Inspected screenshots/UI trees cover old replies without `Assistant:`, short/long user text, heading/bold/italic/code formatting, long model ellipsis, keyboard layout, disabled empty Send, enabled Stop during partial delivery, cancellation/drain completion and clipboard preview of `1 + 1 = 2! 😊` without the role label. This validates the actual view component, not the full Telegram wrapper or live Nano service. Screenshots/XML are local ignored evidence under `build/ai-chat-ui-fixture`. Removed the fixture and its temporary phone files afterward.
- macOS bootstrap `38112294057` succeeded: **151 XCTest cases**, zero failures, plus actual UIKit SDK typecheck of the new bubble and existing native hooks/audio fixtures. Local ordered patches, shared settings, build-safety (6) and IPA-validation (8) tests passed.
- APK `38112294062`, native integration `38112302179` and full IPA `38112304987` are still in progress at this checkpoint. Final artifacts and the complete app remain unverified until those jobs succeed. IPA 100 is the previous audio/port baseline and does not include this chat update.

Android follow-up `704995138916562d335707f7880ea9be51bb771c` raises the model-selector touch area to the full 48 dp toolbar height observed in device QA. Actual ChatView SDK compilation and Java regression fixtures passed again. APK `38113262486` / build 1000488 supersedes 38112294062. iOS/core/bind/runtime inputs are identical to ff77388, so native 38112302179 and IPA 38112304987 remain the correct iOS checks.

APK `38113262486` succeeded. Independently verified original CI APK **1000488**, app.nebulagram.messenger, 1.0.0, matching v1/v2 signing certificate, arm64 Telegram/NebulaLink libraries, local audio and new chat markers. **60,052,034 bytes**, SHA-256 `632fe455feae98741cda8cd8c8ea3867b177240afde7551ee0d9bf00ce7b10b7`. Native integration `38112302179` also succeeded (1873 actions). Device IPA remains in progress at this checkpoint; full APK on-phone acceptance awaits the user's renewed availability because another app was foregrounded.

## October 11: full-app follow-up

- APK 1000488 installed successfully with existing application data. Native deep-link opened Nebula AI; actual toolbar/history/new-chat, placeholder, enabled-after-typing Send and Nano request routing were observed. The user reported the composer hint/text vertical alignment, so adjust Android font padding/gravity/minimum height and align the button with the same row inset, then repeat SDK/device layout checks and build the APK. iOS is not implicated by the Android observation.
- The live Stable/Full request returned UNAVAILABLE, and the connection page showed the same real AICore status. Installed AICore release is `prod_aicore_20260820.00_RC10`; Android reports verified boot `green`, bootloader `locked`. These facts do not establish the underlying Google provisioning failure. Check remaining model configurations through the native settings page, restore the previous choice, and improve actionable in-app status without fabricating readiness, clearing model data or changing the bootloader.
- Primary references inspected: https://developers.google.com/ml-kit/genai/prompt/android/get-started and https://developers.google.com/ml-kit/genai/prompt/android/select-model . Google distinguishes unsupported/not-yet-provisioned configurations through checkStatus; device branding alone is not a readiness signal.

Full-app follow-up: APK 1000488 installed successfully; existing account/application data retained. Native settings entry, enabled-after-typing Send and live local request routing passed. All four real AICore Prompt combinations returned UNAVAILABLE; original Stable/Full was restored. No model data was cleared or hidden state changed. The exact Google-side reason is unknown. Current Google device support lists Pixel 9 Pro XL for Prompt API, but UNAVAILABLE can indicate an unprovisioned configuration. https://developers.google.com/ml-kit/genai .

Runtime `e9853ca203f5d130b6e2866dff640847be1e4cc4` fixes Android composer alignment (font padding, vertical gravity, shared 44 dp height/insets), separates provider errors from copyable answers, and adds Edit request / Connection actions. Java regression/actual SDK compilation passed. Phone fixture measured 0.12 dp alignment offset for both empty hint and three input lines; retry restored the request, enabled Send and resized the input above the keyboard. A fixture-only assertion incorrectly assumed Google's keyboard package; actual XML belongs to `app.nebulagram.chatfixture`, so validation was corrected to the visible restored field/enabled Send/IME resize evidence. No application code change was needed for that assertion. APK `38115968537` / 1000490 is building. iOS runtime inputs remain identical to ff77388.

## Verified chat delivery

Original CI **APK 1000490**, run **38115968537**, source e9853ca: signature and matching certificate, package/version, arm64 libraries, local audio and new chat markers passed independent inspection. **60,051,795 bytes**, SHA-256 `d081fd3f02ab1e4fa1e2df36851cd331958fe52119c82919c1d03df66082ddbf`. This includes the composer alignment/error-card follow-up; phone fixture verified the changed component, but the phone disconnected before this final APK could be installed. 1000488 was previously installed and its full native chat flow checked.

Original CI **IPA 101**, run **38112304987**, source ff77388: full arm64 device app, 12.9.2/app.nebulagram/NebulaGram, exactly one Notification Service Extension and Speech privacy purpose passed independent inspection against its manifest. **88,013,148 bytes**, SHA-256 `902f27958589c19f1a252b1659ddceabe31d757317d7ead8c42ffff9ba336837`. Full native chat integration 38112302179 passed; 151 contract tests/SDK checks passed in 38112294057. User signing/live iPhone rendering remain separate.

The subsequent user-reported iOS Profile visibility/gear issue is tracked in `2026-10-11-ios-profile-tab-live-visibility.md` and final IPA 103 is being built at 1c548a7. Android/core/bind/runtime inputs remain identical to e9853ca, so APK 1000490 remains the final Android chat artifact.

The subsequent Profile-tab fix is now fully built: bootstrap **38118223239 / 153 tests**, native **38118224196**, and independently verified full **IPA 104 / 38118226841** at 92468e6. The original archive is **88,019,585 bytes**, SHA-256 `6cdfdcb1f81a455a9d154d228810b2cc8fb1fda451cc921ce4c5625179055287`; includes the chat update and final tab repair. User signing and physical iPhone rendering remain separate acceptance. Final Android stays the independently verified APK 1000490 because Android/core/bind/runtime sources are unchanged.
