# Profile banner transition Implementation Plan

> **For agentic workers:** Execute inline under the writing-plans workflow; optional execution plugins are unavailable. Do not delegate or edit the dirty vendor checkout.

**Goal:** Keep the Android profile banner blended into the page and its photo/glass transition continuous while opening, closing and scrolling the avatar gallery.

**Architecture:** Use the pinned Telegram 12.10.6 profile hierarchy. Paint the foreground fade once before the first foreground child, after the actual background/photo/blur children, including when OverlaysView is GONE. Keep native profile geometry and interaction. Remove the 0.5 expansion switch by blending native collapsed/gallery sources continuously; retain state restoration, fallbacks and bounded glass capture.

**Tech Stack:** Java/Android Canvas, native ProfileActivity patch, production rendering fixtures, Android SDK compilation, APK CI.

Evidence: `video_2026-10-11_14-06-58.mp4` (17.82 s) shows collapsed/expanded profiles, a hard photo/page boundary, and transitions. Current patched ProfileActivity only calls `nebulaHero.drawForeground` after drawing OverlaysView. Its native constructor sets visibility GONE; collapsed frames can omit that child entirely. Hero changes the photo receiver at `expanded > .5f`, including glass capture. Existing tests invoke drawForeground directly and do not exercise native child visibility.

## 1. Regression for native draw order

Files: `scripts/check-profile-banner.py`; new ordered `patches/android/0196-profile-banner-transition.patch` (verify the next free number before writing).

- [x] Execute the actual native drawChild hook with instrumented children for collapsed overlays GONE, expanded overlays visible, optional actions/music, fallback title, repeated frames and landscape. Assert one fade after backdrops and before foreground content. Run against current source first and observe the missing collapsed fade.
- [x] Add a per-frame flag reset by dispatchDraw. Classify only known native backdrop children; draw before the first foreground child.

```java
if (!isInLandscapeMode && !nebulaForegroundDrawn
        && child != giftsView && child != avatarGooey
        && child != avatarsViewPager && child != avatarsBlurView && child != overlaysView) {
    nebulaForegroundDrawn = true;
    nebulaHero.drawForeground(canvas);
}
return super.drawChild(canvas, child, drawingTime);
```

## 2. Continuous banner/glass source

Files: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProfileArt.java`; production fixture in `scripts/check-profile-banner.py`.

- [x] Add distinct-photo tests around 0.49/0.5/0.51 and 0/1, fallback/loading, live theme and exception cleanup. Verify continuous source weights and preserved receiver coordinates/alpha/radii instead of encoding a hard threshold.
- [x] Use the collapsed source for its decorative backdrop while it gives way to the native gallery. Blend the collapsed/gallery capture using the native expansion fraction; a missing source uses the available image. Paint veil/tint/fade once per captured surface, and preserve bounds/shader reuse.
- [x] Run production draw cases and SDK compile. Verify the native hook against a fresh owned scratch tree prepared from the pinned source and complete ordered patch series. Never use dirty vendor text as the delivery source.

## 3. Delivery

- [ ] Build and independently verify APK source/version/signature/ABI. Inspect phone rendering only if it becomes reachable; current adb inventory is empty.
- [x] Audit the corresponding iOS banner behavior for the same dependency/threshold before deciding whether it needs a matching fix. The supplied video is Android, so do not apply an unrelated iOS visual change.
- [ ] Record exact acceptance and limits; keep previous unrelated changes intact.

User confirmed both the hard boundary and the transition jerk need fixing.

Baseline native-child fixture fails with `missing/repeated fade: [gifts, avatar, blur, title]`, reproducing the collapsed GONE-overlays bug without changing the vendor checkout. Native 0195 already exists, so the follow-up uses 0196.

Local checks pass: 48 actual native child-order cases, 627 production rendering states covering opening and closing in 1% steps, fractional lower bounds, distinct/identical/missing sources, constant capture opacity, live theme changes, shader reuse, software/reduced mode and draw exceptions including intermediate-layer cleanup. Production hero/actions compile against Android SDK 37.0. The original hard source switch failed the new participation check (`gallery enters too late during expansion`). These are rendering-contract tests, not a physical-device visual acceptance claim.

iOS audit: patch 0062 attaches its CAGradientLayer fade directly to the native avatar list container and updates frame/alpha through the native transition. It has neither Android's hidden OverlaysView draw dependency nor the midpoint image-receiver selection. No iOS runtime change is made for this Android video report.

The complete 191-patch Android series applies to 134 pristine native paths from pinned upstream `f2908b14133bbffbf7ab04f641ecb5bfaf533242`; the resulting profile source matches the reviewed fixture after line-ending normalization. Banner tests pass on this complete tree. Glass preference/effect/shader cache tests and 505,505 scalar glass-edge samples also pass; GPU rendering still requires device acceptance.
