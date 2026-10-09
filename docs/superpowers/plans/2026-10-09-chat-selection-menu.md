# Chat selection and menu implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Match the second supplied video’s centered selection counter, add Go to beginning before Clear deleted messages, and repair the remaining intermittent update-emoji start.

**Architecture:** Append a narrow Telegram patch rather than edit the dirty vendor checkout. Keep native selection actions and history loading. Recheck animation visibility on the first actual drawing pass, after ancestor layout.

**Tech Stack:** Android Java, Telegram ActionBar/AnimatedTextView/ImageReceiver, Python regression fixtures.

## Changes and validation

- [x] Create `patches/android/0189-chat-selection-and-history-start.patch` from the reconstructed `build/android-upstream-12106` sources. Center `selectedMessagesCountTextView` using `setGravity(Gravity.CENTER)` and zero right-only padding. Set its left margin to `dp(52) - Math.round(actionMode.getX())` and right margin to 4dp; move the counter capsule’s final left edge from 60dp to 52dp. Preserve AnimatedTextView's native digit transition and weighted resizing when action icons change.
- [x] In the same patch, add menu ID `0x4e4753`, label `NebulaText.text("В начало", "Go to beginning")`, icon `R.drawable.msg_go_up`, immediately before existing clear-deleted action. Restrict to cloud default/saved histories; invoke native `jumpToDate(1)`, retaining native request lifecycle, progress and cancellation. Use the native section gap before these navigation/cache actions.
- [x] Extend `scripts/check-selection-capsule.py` for the new 52dp target across transition frames. Inspect and compile the changed native hooks; verify all patches apply from the pinned upstream commit.
- [x] Extend `scripts/check-settings-emoji.py`: decoder arrives while viewport=false, then viewport=true without another layout/focus callback, draw must start both TGS and video. Confirm failure first. In `NebulaAnimatedEmoji.onDraw`, call `updatePlayback()` before drawing so geometry from the complete ancestor layout can start the decoder. Keep hidden/focus/reduced-motion and detach checks.
- [x] Run emoji, selection and menu regression checks, Android SDK compilation and `git diff --check`; commit/push and start the full APK workflow. Report physical-device verification separately.

The requested implementation is authorized; execute inline. The optional execution subskills referenced by the planning template are not installed.

## User clarification: whole selection toolbar

- [x] Append `0190-selection-toolbar-actions.patch`: add a checklist action after the counter when multiple messages are selected; select only already loaded eligible messages up to the native 100-message limit using the existing selection accounting and protections. Use rounded existing Nebula line icons for selection controls. Animate controls with uniform 0.88-to-1 scale, 220ms; retain native permission-dependent visibility.
- [x] Add a no-author mode to `ChatActivityActionsButtonsLayout`: two equal native glass pills, left icon before “Без авторства”, right arrow after “Переслать”. In cloud default/saved chats the left action invokes the same destination picker with an explicit per-picker hide-author parameter. Keep native Reply in other modes. Carry the captured parameter to direct-send and both draft-preview branches; ordinary forwarding must always pass false. Keep native restrictions, destination rights, paid confirmation, scheduling and cancellation.
- [x] Exercise bulk selection eligibility/limit/idempotence and SDK-compile the actual footer component; check all three forwarding destinations carry the parameter, then build the full APK.

User correction: only the header icons should change by default. Keep the native bottom buttons, including their native icon positions and labels, until the opt-in `selection_without_author` switch in Chats is enabled. The switch defaults off through NebulaFeatureSettings; changing it back restores Reply and Forward.


Validation: all 185 patches reconstruct 134 native paths from the pinned upstream; 12,928 capsule transition cases, bulk-selection eligibility/100-item cap/idempotence/merged IDs, forwarding route and opt-in-default guards, emoji pre-layout decoding lifecycle, updater/settings-link checks pass. Actual footer and updated emoji/updater sources compile against the Android SDK and compiled Telegram dependencies. Full APK build is the remaining CI check. No device is attached, so visual/gesture acceptance is not claimed.
