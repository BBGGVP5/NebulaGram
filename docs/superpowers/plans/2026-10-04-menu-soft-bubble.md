# Soft Menu Bubble Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Open ordinary Android menus from a blurred round surface without copying the trigger icon; retain Telegram motion for message menus.

**Architecture:** Keep native PopupWindow placement and drawing outsets. Remove trigger snapshots, use continuous spring growth and close from the actual displayed extent. Apply a per-layout motion policy so message/reaction layouts keep native transitions and glass material.

**Tech Stack:** Java, Android Canvas/RenderEffect, native Telegram patch series, Python/JVM and real Android SDK checks, GitHub Actions.

---

### Task 1: Reproduce the recording failures

Files: `scripts/check-menu-feedback.py`, `scripts/check-menu-viewport.py`, `scripts/check-liquid-popup.py`, `scripts/check-menu-state.py`.

- [x] Compare the user's two recordings around 4.18–4.66 s (NebulaGram) and 4.495–4.72 s (FlClash). The trigger glyph remains in the expanding panel; early dismiss duration uses animation time instead of visible panel size.
- [x] Add production-method regressions: trigger alpha/draw count remain unchanged; message layouts reserve no outsets; a nearly expanded panel dismissed early receives a full close; spring velocity does not flatten abruptly at an artificial cap.

```java
check(anchor.alpha == .7f && anchor.draws == 0, "trigger is never captured or hidden");
menu.nebulaReveal.setMorphEnabled(false);
viewport.prepare(window, root, app, Gravity.TOP, 0, 0, false);
check(!menu.nebulaReveal.ready && window.elevation == 4, "message menu retains native window");
```

Run `python scripts/check-menu-viewport.py build/android-upstream-12106` before implementation; expect failure on missing motion policy.

### Task 2: Implement motion and native message exclusion

Files: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMenuReveal.java`, `NebulaMenuBubble.java`, `NebulaMenuFocus.java`, `NebulaMenuStyle.java`, `NebulaMenuViewport.java`; delete `NebulaMenuSource.java`; create `patches/android/0176-soft-menu-bubble.patch`.

- [x] Delete snapshot ownership/capture/drawing from reveal and its native draw hook. Trigger position remains a weak anchor; no bitmap handoff remains.
- [x] Add persistent per-layout motion policy and use it in native opening/closing and viewport guards.

```java
public boolean isMorphEnabled() { return morphEnabled; }
public void setMorphEnabled(boolean enabled) { morphEnabled = enabled; }
public static boolean animated(ActionBarPopupWindowLayout content) {
    return content != null && content.nebulaReveal.isMorphEnabled() && animated();
}
```

- [x] Replace the capped growth spring with an uncapped damped response under the existing 4.5% geometry envelope; correct endpoint velocity. Use smooth shrink and extent-based close duration. Preserve exact native endpoints and inverse touch mapping.
- [x] Compensate text blur for its uniform canvas scale with bounded cached RenderEffects; do not allocate effects repeatedly.
- [x] Mark ChatActivity message popup layouts as native motion, remove the forced bubble start/animation style, and keep their native reaction/message lift transition. Keep glass styling.

```java
popupLayout.nebulaReveal.setMorphEnabled(false);
// Native PopupContextAnimation and reaction progress own this message menu.
```

- [x] Reconstruct the complete pinned Telegram tree and run feedback, viewport, geometry, touch, state, colours and real SDK checks. Update fixture SDK stubs only to match new production methods.

### Task 3: Publish and build

Files: `docs/USER-CHANGES.md`, this plan.

- [x] Describe no moving trigger glyph, smooth bubble opening and native message menu motion in the user changelog.
- [ ] Run `git diff --check`; stage explicit changed files without the user's Telegram gitlink; commit and push main plus `codex/camera-controls`.
- [ ] Download and verify the signed Android APK for that exact revision. Complete the already running iOS rebuild separately; Android PopupWindow changes do not alter iOS native menu source.
- [ ] Deliver artifact links and distinguish automated verification from unperformed physical-device visual QA.

Verification before publication: all 171 patches apply to 122 pinned native files. Production focus passes 14,400 warmed frames with no effect allocations after cache warmup. Grouped actions pass 16,968 cases; inverse touches pass 3,636 cases; profile passes 312 drawing/state cases. SDK 37 typechecks menu and profile hooks. The iOS 74 IPA from unchanged iOS source at 87ea4fd completed successfully and its artifact digest/package were verified; it requires user signing. No physical Android device is attached.
