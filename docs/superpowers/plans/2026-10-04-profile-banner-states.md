# Profile banner states Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the music-row seam and keep borderless photographic glass consistent while the Android profile photo expands or collapses.

**Architecture:** Use Telegram's actual header bottom rather than the action row as the decorative boundary. Paint the page-colour fade after native photograph/blur children and before controls/text. Keep native layout, hit targets, gallery paging and music interactions; let a drawing-only action hook replace the native flat/blur backdrop when photographic glass is available.

**Tech Stack:** Android Canvas/View/RenderNode, Java, pinned Telegram patch series, production-class JVM drawing regressions, Android SDK compilation, GitHub Actions.

---

Implementation runs inline under the current developer restriction on delegation. This is an authorized bug fix; no additional approval is needed.

Evidence: the collapsed screenshot has a solid peer-colour strip behind the music row. `Hero.draw` ends at `actions.getY() + 74dp`, although native `updateActionsPosition` reserves another 25dp for music. Its `(1 - expanded)` alpha also removes the fade and glass capture in the expanded state. Native `ProfileActionsView.drawRenderNode` draws the gallery blur after the custom action surface, covering the material.

### Task 1: Make the decorative boundary and drawing order native

Files: modify `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProfileArt.java`; create `patches/android/0175-profile-banner-states.patch`.

- [x] Pass the current native header bottom, including the music row and search/media transition, into the hero:

```java
float bottom = (ActionBar.getCurrentActionBarHeight()
        + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0)
        + extraHeight + searchTransitionOffset) * (1f - mediaHeaderAnimationProgress);
```

- [x] Separate decorative-photo opacity from foreground fade opacity. The photograph still fades as the native gallery appears; the page-colour fade remains present through expansion.
- [x] Draw the fade immediately after the native `overlaysView`, which follows the gallery and its blur but precedes actions, music and title nodes:

```java
boolean drawn = super.drawChild(canvas, child, drawingTime);
if (child == overlaysView && !isInLandscapeMode) {
    nebulaHero.drawForeground(canvas);
}
return drawn;
```

- [x] Use the currently displayed native gallery `BackupImageView` during expansion instead of freezing the first avatar as the glass source.
- [x] Restore image coordinates, alpha and all four radii in `finally`; reuse radius buffers without changing the avatar base-radius preference.

### Task 2: Keep action material consistent

Files: modify `NebulaProfileArt.Actions`; extend the native `ProfileActionsView` patch in `0175-profile-banner-states.patch`.

- [x] Add a drawing-only extension point, defaulting to false:

```java
protected boolean hasCustomActionSurface(Canvas canvas) { return false; }
```

- [x] Draw the custom material in place of the flat backdrop, and skip the later native blur overwrite only when that material is available on the current canvas. Preserve native label/icon drawing and all action geometry.
- [x] Keep the native fallback for software rendering, reduced effects, missing images, disabled banners and landscape.
- [x] In photographic mode, use the same neutral surface and contrast through expansion; retain the native colour for profiles without a photo banner.

### Task 3: Verify and deliver

Files: create `scripts/check-profile-banner.py`; add it to `.github/workflows/android.yml`; update `docs/USER-CHANGES.md`.

- [x] Execute the production hero/actions with Canvas/ImageReceiver fakes. Verify music-inclusive boundaries, foreground-before-controls integration, continuous opacity across 101 expansion values, gallery source selection, software/reduced fallbacks, cached shader/radius reuse and restoration after a draw exception.
- [x] Compile the modified production hero against the installed Android SDK with signatures matching the native classes.
- [x] Reconstruct the entire pinned Android patch series with `python build/verify-upstream-12106.py`; run the profile regression plus `python scripts/check-render-hot-path.py build/android-upstream-12106`.
- [x] Publish the intended paths in 9739479 without changing the user Telegram checkout.
- [ ] Verify the final Android artifact and release signature, including the subsequent menu corrections in the same APK.
- [x] Finish and verify the iOS 74 rebuild at 87ea4fd; its unchanged native banner fade uses a different hierarchy and the unsigned IPA requires user signing.

Device evidence: ADB currently lists no connected phone. Automated drawing/lifecycle checks and compilation must not be reported as device visual acceptance.

### Task 4: Group the selection actions requested during this fix

Files: modify `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSelectionGlass.java` and `scripts/check-liquid-popup.py`.

- [x] Change the production regression to expect one shared right-side capsule and run it against the previous implementation: it fails on the per-button bounds.
- [x] Gather the native visible-action union; draw one cached, independent material at that union. Preserve the separate counter, close control, icon drawing and each child's hit target.

```java
actions.setBounds(first, top, last, bottom);
actions.setAlpha(Math.round(255 * factor * alpha));
actions.draw(canvas);
material.setBounds(first < last ? first : 0, top, first < last ? last : 0, bottom);
```

- [x] Run the grouped selection regression, native header branch check and actual SDK API compilation; include this correction in the same Android build.

Verification: 170 patches reconstruct against 122 pinned native files. The production profile check passes 312 state cases plus resize/theme/detach checks and actual Android SDK compilation. The grouped selection regression passes 16,968 cases; native header bounds, palette and hot-path checks also pass. ADB still shows no device.
