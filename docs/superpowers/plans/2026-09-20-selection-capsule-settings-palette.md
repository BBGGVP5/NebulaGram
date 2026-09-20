# Selection capsule and independent settings palette

**Goal:** Restore the glass capsule behind selection actions and prevent Telegram themes from recoloring Nebula settings.

- [x] Patch ActionBar's separate-avatar drawing branch: retain the avatar fade and draw the actual action-menu capsule with selection progress. Preserve measured action width, taps, built-in glass edges and non-chat behavior.
- [x] Make `NebulaTheme.of` use the system light/dark mode and fixed brand palette. Separate the explicitly enabled Material You application to Telegram from settings rendering, so that feature still derives the system wallpaper accent.
- [x] Build iOS settings list presentation from a fixed default light/dark palette instead of the user's Telegram theme; keep language and other presentation data. Native UIKit settings already use system colors.
- [x] Test actual action drawing over transition frames and visibility combinations, and palette getters against changing Telegram/accent state. Keep all previous preview and settings checks.
- [x] Compile Android sources and check iOS hooks locally.
- [ ] Push and dispatch updated APK/IPA builds; full iOS SDK validation runs on CI.

Validation: 12,928 production action-menu draw cases and 40,031 palette/accent restoration assertions passed, including repeated theme/wallpaper changes without draw-time configuration reads. Existing preview geometry, capture, icon, settings and hot-path checks passed; iOS bootstrap applies all 17 patches. Physical-device visual verification remains necessary.

Android `:TMessagesProj:compileStandaloneJavaWithJavac`: BUILD SUCCESSFUL in 3m 57s (137 tasks).
