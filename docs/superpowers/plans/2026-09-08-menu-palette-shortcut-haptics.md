# Menu palette, anchor motion and home shortcut Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Repair unreadable popup surfaces and reversed opening direction, deliver the missing home NebulaLink control, and cover tab/folder haptics.

**Architecture:** Keep menu material and foreground on one deterministic palette; validate contrast against both black and white underlays, not just an opaque nominal color. Resolve the opening pivot at pre-draw in real window coordinates and use one coherent anchored scale instead of independent row sliding/circular clipping. Add a lifecycle-bound home shortcut using existing asynchronous core calls, plus shared debounced haptics at user-action sites. Package native changes after 0062 as 0063.

**Tech Stack:** Android Java, existing blur3/ValueAnimator, JSON core API, Python/JVM regression checks, Git patches, Gradle.

## 1. Menu rendering and animation
Files: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/{NebulaMenuPalette,NebulaMenuStyle,NebulaMenuReveal}.java`, `scripts/check-menu-colors.py`, `scripts/check-menu-feedback.py`.
- [x] Test actual translucent contrast with black/white underlays and mismatched global/source theme; previous tests only checked a mocked opaque surface.
- [x] Use source-theme dark/light neutral surface, minimum readable tint alpha, and `foreground(original, surface)` for the same surface installed on the drawable before drawing.
- [x] Resolve anchor after attachment with `anchor.getLocationOnScreen() - host.getLocationOnScreen()`, clamped to popup bounds; retain it for closing.
- [x] Replace row translation and oscillating circular reveal with one anchored scale/alpha transition. Cancel without snapping to full size. Reset on detach.

## 2. Home NebulaLink
Files: new `NebulaLinkShortcut.java`, `NebulaLinkShortcutState.java`; modify `NebulaSectionFragment.java`, `NebulaSettingsSearch.java`; native `DialogsActivity.java` in 0063; `scripts/check-link-shortcut.py`.
- [x] Add persisted `home_nebulalink` display switch (default on), search entry and preview of disconnected/connecting/connected/error states.
- [x] Install a 46dp action item only on the ordinary home dialogs screen, never picker/search-only screens.
- [x] Tap calls `settings.get` then `tunnel.start` when selected, or opens server selection if absent; connected/connecting tap calls `tunnel.stop`. Serialize taps while the callback is outstanding and expose errors.
- [x] Long press opens an anchored menu with server selection and a selected-server `probe.servers` request (`ids: [selectedId]`, never all servers).
- [x] Register preferences/status/proxy listeners on attach and remove on detach; no render-time networking. Preview never calls the core.

## 3. Haptics and verification
Files: `NebulaHaptics.java`; native `MainTabsActivity.java`, `FilterTabsView.java` in 0063; `scripts/check-menu-feedback.py`.
- [x] Route ordinary lower-tab and folder taps, folder long-press, and scrub selection through the shared preference/strength policy; debounce duplicate calls within 40ms.
- [x] Test disabled setting, repeated events and strength bounds; assert native user-action hooks exist, not just drag hooks.
- [x] Reconstruct all patched native files from pinned upstream; sequentially apply patches and compare with the compile tree.
- [x] Run existing UI/JVM checks and the added tests, then `:TMessagesProj:compileStandaloneJavaWithJavac --offline`; distinguish source verification from device visual validation.
- [x] Review diff and preserve unrelated motion assets. Delivery is permitted only after successful checks under the existing instructions.

Execution is inline; the referenced superpowers execution skills are not installed. Device validation remains explicit: home overflow, folder long-press and lower-tab menus in light/dark themes, with blur on/off and rapid open/close.

## 4. Screenshot follow-up and verification
- [x] About: put Information, Links and Components section headings outside their cards.
- [x] Folder outline: paint from the lens bounds after pressure/stretch/clamping, not the undeformed selector.
- [x] Header: share expanded back width with title placement; keep normal back width during selection/search.
- [x] Stickers/GIF: use the existing chat frosted source, clip the panel to rounded bounds and pager above the lower type controls; retain grid bottom padding.

Verified locally: 14 existing Python/JVM checks and two new checks pass; 90 translucent palette cases plus 30 nested row cases; all 58 Android patches apply sequentially and match the native compile tree. Java/resource compilation `:TMessagesProj:compileStandaloneJavaWithJavac --offline` succeeded (2m55s). New tests distinguish runtime method tests from static integration assertions.

Not verified: APK packaging, actual tunnel/probe connectivity, and device rendering/touch perception. Device acceptance: light/dark home overflow, held folder and lower-tab menus, rapid open/close with top/bottom anchors; pressed folder outline; unread counter 0/1/99+ in Saved Messages and selection/search; emoji/GIF/sticker end-of-list clipping; shortcut visibility, tap, hold, and four states.
