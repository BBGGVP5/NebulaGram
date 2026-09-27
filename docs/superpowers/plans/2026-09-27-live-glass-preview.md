# Live glass controls and preview

**Goal:** Make glass settings visibly match chat chrome and stop unintended text distortion.
**Architecture:** Refresh cached drawable parameters by settings revision, use the actual RenderNode glass renderer in a draggable text preview, and expose adaptive limits. Preserve saved values; default refraction is off unless explicitly configured.
**Tech stack:** Android Java, RenderNode/AGSL, ordered upstream patches, JVM regression checks.

- [x] Synchronize tint, highlights and refraction on existing glass drawables without layout passes.
- [x] Remove coarse shader thresholds; make zero refraction a true bypass.
- [x] Replace decorative preview with a draggable native glass capsule over readable sample text.
- [x] Show effective adaptive status and accurately scaled transparency.
- [x] Verify patches and compile touched classes; run glass regression checks; push Android build.

Validation: 120 ordered patches apply to pinned Telegram 12.10.5 without changing vendor; touched overlay and native Java classes compile against Android 36 and the local Telegram classpath. Glass preference/blur/shader regression checks and the settings contract pass. No attached Android device: hardware rendering, scrolling and preview appearance still require device verification.
