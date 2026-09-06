# Liquid bottom bar implementation plan

**Goal:** Give the existing blurred bottom bar a moving glass lens and clearer glass edges.

**Architecture:** Preserve Telegram's blur/refraction pipeline and touch handling. Draw one animated lens behind the native tab icons in MainTabsLayout, replacing separate selection fills while the Liquid Glass animations preference is enabled. Keep native long-press dragging and the disabled-setting appearance.

**Tech stack:** Android Canvas, ValueAnimator, native Telegram blur providers; patch 0053 and a small overlay drawing helper.

- [x] Add interruptible lens movement with bounded stretching and no drawing over icons.
- [x] Integrate selection, drag, detach cleanup and the existing preference.
- [x] Tune the shared bar tint and edge highlights; preserve native blur capability checks.
- [x] Compile, check navigation and clean patch application, then commit and push.

Validation: `:TMessagesProj:compileStandaloneJavaWithJavac` passed. Navigation checks (legacy states, visibility combinations, compact widths, icon restoration) and the existing appearance checks passed. All 48 patches apply cleanly to 52 pristine files and match the compiled Java tree. No Android device was connected for visual verification. The native implementation retains live blur on supported Android versions and refraction when Telegram's Liquid Glass capability is enabled; older/limited devices keep its fallback background.
