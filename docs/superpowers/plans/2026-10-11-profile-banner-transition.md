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

- [x] Build and independently verify APK source/version/signature/ABI. Inspect phone rendering only if it becomes reachable; final adb inventory is empty.
- [x] Audit the corresponding iOS banner behavior for the same dependency/threshold before deciding whether it needs a matching fix. The supplied video is Android, so do not apply an unrelated iOS visual change.
- [x] Record exact acceptance and limits; keep previous unrelated changes intact.

User confirmed both the hard boundary and the transition jerk need fixing.

Baseline native-child fixture fails with `missing/repeated fade: [gifts, avatar, blur, title]`, reproducing the collapsed GONE-overlays bug without changing the vendor checkout. Native 0195 already exists, so the follow-up uses 0196.

Local checks pass: 48 actual native child-order cases, 627 production rendering states covering opening and closing in 1% steps, fractional lower bounds, distinct/identical/missing sources, constant capture opacity, live theme changes, shader reuse, software/reduced mode and draw exceptions including intermediate-layer cleanup. Production hero/actions compile against Android SDK 37.0. The original hard source switch failed the new participation check (`gallery enters too late during expansion`). These are rendering-contract tests, not a physical-device visual acceptance claim.

iOS audit: patch 0062 attaches its CAGradientLayer fade directly to the native avatar list container and updates frame/alpha through the native transition. It has neither Android's hidden OverlaysView draw dependency nor the midpoint image-receiver selection. No iOS runtime change is made for this Android video report.

The complete 191-patch Android series applies to 134 pristine native paths from pinned upstream `f2908b14133bbffbf7ab04f641ecb5bfaf533242`; the resulting profile source matches the reviewed fixture after line-ending normalization. Banner tests pass on this complete tree. Glass preference/effect/shader cache tests and 505,505 scalar glass-edge samples also pass; GPU rendering still requires device acceptance.

## Delivery evidence

- Runtime revision: `7e4d810e1cfcda933e3744e21cc049303e154e00`.
- Android workflow [38132549337](https://github.com/BBGGVP5/NebulaGram/actions/runs/38132549337) succeeded. All workflow interface/regression checks passed, including the 48 native order and 627 banner state cases; the production hero/actions were SDK-checked in CI, followed by the complete native/Java/R8 application build.
- Downloaded immutable artifact: `build/artifact-38132549337/NebulaGram-1.0.0-TG-12.10.6-b1000492-arm64-v8a.apk` (60,051,757 bytes).
- Independently checked `app.nebulagram.messenger`, version `1.0.0`, build `1000492`, arm64 Telegram and NebulaLink libraries, retained local audio/chat markers and valid signature with the existing certificate SHA-256 `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.
- APK SHA-256: `7b025b3dbc07b2e1b471d5188a316368de8922508b1809afc7bd30f0e630e46a`. Retrieval and verification receipts are saved beside it.
- The phone remains absent from the final USB inventory. APK installation and physical-device visual/GPU acceptance are **not verified**. No iOS rebuild is needed because this fix changes only the Android profile renderer.

## Follow-up after testing APK 1000492

User confirms Android 1000492 still shows the plain background and jerks. Native source shows `avatarsViewPager.setAlpha(0f)` throughout expansion/collapse, restoring its opacity only in the completion. Fading our decorative photo by `1 - expansion` therefore removes the background before native gallery handoff. Native end callbacks also apply visibility changes when cancellation dispatches `onAnimationEnd`.

- [x] Require a full-strength photo/veil backdrop in every production expansion frame, including when native gallery alpha is zero. Observe the failure on 1000492 rendering code before changing it.
- [x] Reuse the existing constant-opacity two-photo blend for both the real backdrop and captured glass; retain the backdrop under the native gallery and apply the foreground fade once. Do not hand off by expansion progress while the actual gallery remains transparent.
- [x] Execute the two actual native listeners with cancel/end, reversed state, repeated end and normal completion. Add ordered patch 0197 so cancelled/stale completions cannot swap avatar/gallery visibility; native sizing and duration remain intact.
- [x] Re-run banner/native checks and build the replacement APK. Adapt the equivalent current-state and interrupted-completion behavior to iOS as explicitly requested in the separate iOS plan.

Both regressions fail on 1000492 source: `decorative photo fades before the native alpha-zero gallery handoff` and `cancelled callback changes native photo visibility`. After the fix, all 192 patches apply to 134 pinned native paths. The 48 native order cases, 8 actual animator cases, 628 rendering states (including main/capture exception restoration) and SDK compile pass on the full tree, as do render/cache checks. Physical-device acceptance still requires a reachable phone.

Replacement Android workflow [38137213946](https://github.com/BBGGVP5/NebulaGram/actions/runs/38137213946) succeeded at runtime `2fb2c4d0bb5b0f4c08ca29d928c5a893fe3e3689`. Downloaded and independently verified APK `1000493`: `build/artifact-38137213946/NebulaGram-1.0.0-TG-12.10.6-b1000493-arm64-v8a.apk`, 60,051,657 bytes, SHA-256 `2a4b7c131fcda6697eebd55f535a52aaae7e186e22231a9ae52eb2160055a403`, same valid signing certificate/package/version, arm64 Telegram/NebulaLink libraries and retained chat/local-audio markers. The user confirms the phone is currently unavailable, so installation and physical visual/GPU acceptance remain unverified.

### Explicit instant endpoint

The actual `collapseAvatarInstant()` also calls `cancel()` and then forces progress to its target. With cancelled listeners now skipped, it must explicitly settle native avatar/gallery visibility and gallery opacity. Four extracted native instant cases (both target states, with/without a running animator) initially fail with `instant endpoint leaves a transparent/incorrect gallery after cancellation`. Patch 0198 restores the target background/visibility/opacity after forced progress, without re-enabling stale completion callbacks. All 193 patches apply; 48 native order, 12 native animation/instant cases, 628 rendering states and SDK compilation pass on the full tree.

- [ ] Rebuild and independently verify the APK containing this instant-settlement follow-up; retain the successful iOS 106 build at unchanged iOS runtime e632317.
