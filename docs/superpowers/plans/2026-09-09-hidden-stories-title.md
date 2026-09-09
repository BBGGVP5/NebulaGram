# Hidden Stories Title Implementation Plan

> Execute inline; subagent/executing-plans skills are not available.

**Goal:** Keep the home title readable when stories are disabled, without overriding the centering preference or overlapping menu buttons.
**Architecture:** Measure text plus status drawable against the actual free interval. Prefer a symmetric center slot, expand it only when content needs more width, and clamp the resulting slot between the start inset and menu. Keep native search/selection/non-centered layout unchanged.
**Tech Stack:** Android Java overlay, ordered upstream patch, Python/JVM regression harness, Gradle.

## Tasks
- [x] Add `scripts/check-home-title.py`: regression with width=448, start=18, end=160, content=150 must allocate 150 instead of 128. Exercise densities, long folder names/status icons, short titles, symmetric buttons and extremely narrow widths; assert both safe edges.
- [x] Add `NebulaHomeTitleGeometry.java` with `width=min(available,max(symmetric,content))` and `left=clamp((barWidth-titleWidth)/2,start,barWidth-end-titleWidth)`.
- [x] Generate `patches/android/0076-home-title-space.patch` against the prepared ActionBar: first measure against all available space, include text/side drawables/padding, remeasure against chosen slot; layout uses the same menu margin plus title-container translation. Apply to both title animation views.
- [x] Wire the regression into Android CI, document hooks, verify ordered patches, run title/story/style tests and full Java compilation. Do not dispatch another build without a request.

## Device follow-up
Check stories on/off, center home on/off, long folder title with custom emoji, portrait/landscape, search/action mode and expanded/collapsed story strip. JVM geometry and compilation cannot establish pixel correctness on a phone.

## Verification result
- Regression initially failed because the adaptive geometry was absent; the screenshot-sized case now allocates the required 150 instead of the old symmetric-only 128.
- All 30 Android workflow checks passed, including 2016 production title geometry cases; log: `build/home-title-checks.log`.
- All 71 ordered Android patches apply to pinned upstream `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` without modifying vendor.
- Full `:TMessagesProj:compileStandaloneJavaWithJavac` passed in 2m56s; log: `build/home-title-compile.log`. Only existing deprecation/source-8 warnings.
- No device/pixel verification or new Android APK dispatch in this change.
