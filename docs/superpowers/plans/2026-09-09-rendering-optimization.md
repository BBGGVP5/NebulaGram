# Rendering hot-path optimization Implementation Plan

> Execute inline; no execution/subagent skill is available.

**Goal:** Remove redundant per-frame work without reducing display refresh rate or turning off glass.
**Architecture:** Cache immutable glass preference snapshots with explicit invalidation, and reuse identical native RenderEffects. Keep dynamic blur updates, theme controls, selection geometry and account routing intact. User reports Oppo, approximately 120 Hz; no device trace available, so do not claim measured frame-rate improvement.
**Tech Stack:** Java, Android RenderNode/RenderEffect, SharedPreferences, JVM regression harness, native Gradle compilation.

### Task 1: Settings hot path
Files: `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaGlass.java`, `scripts/check-render-hot-path.py`.
- [x] Test 10,000 calls to custom/opacity/blur/refraction after warmup: zero additional preference reads/lookups, same values as existing formulas.
- [x] Implement a lazily registered strong listener and immutable volatile snapshot; refresh on relevant external edits, null/clear and our setters. Ignore unrelated preference updates. Cache contains no Activity or View.
- [x] Test opt-out defaults, bounds clamping, edits/imports, clear and unrelated keys.

### Task 2: Native effect reuse
Files: new ordered patch `patches/android/0080-reuse-blur-effects.patch`, same test script.
- [x] Execute the patched source methods against RenderEffect/RenderNode stubs. Repeating equal radius/chain must create and install an effect only once.
- [x] Cache effective radius and input effect identity in the source. The plain setter must reset an old chained effect. Zero blur keeps only the input effect; it must not construct an invalid zero-radius blur.
- [x] Test radius changes, custom override, chain changes, chain-to-plain reset, zero and first-call zero.

### Task 3: Verification
- [x] Run JVM tests, native patch-series application and Android Java compilation; run existing glass/tab regressions.
- [x] Document that on-device frame timing remains unmeasured and no APK has been dispatched.

## Verification results (2026-09-09)
- Regression harness: 32 Android checks passed, including the new hot-path and tab icon tests.
- Hot-path harness: 40,000 warmed glass getter calls cause no SharedPreferences lookup/read; 20,000 identical native blur setter calls create no additional effects. These are operation-count regressions, not FPS measurements.
- All 75 ordered Android patches apply without modifying vendor. Android `:TMessagesProj:compileStandaloneJavaWithJavac` succeeded in 2m 50s; log: `build/performance-compile.log`.
- Oppo model / exact display refresh rate and on-device frame trace are still unknown. No claim of a fixed frame rate, no forced display mode and no glass disabled. No build dispatched.
