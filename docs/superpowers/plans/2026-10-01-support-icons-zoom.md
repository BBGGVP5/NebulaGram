# Support, icons and camera controls implementation plan

> **For agentic workers:** Execute inline in the existing checkout. Preserve the pinned vendor tree and unrelated artwork.

**Goal:** Redraw settings and issued badges, add project support, remove the Saved Messages title flash, and refine camera zoom presets and motion.

**Architecture:** Generate Android and UIKit glyphs from shared paths. Retain server-issued badge identities, donation configuration and Telegram theme colors. Export native fixes as the next ordered Android patch.

**Tech Stack:** Android Java/vector drawables, UIKit Swift, Python generators, ordered Telegram patches.

### Camera controls
- [x] Add supported 1× and 2× shortcuts alongside physical lens stops in `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaZoomSlider.java`: `if (minimum <= 2f && maximum >= 2f) stops.add(2f);`.
- [x] Animate preset changes in logarithmic zoom space for 160 ms, cancel on touch and detach, keep drag changes immediate. Fit labels to the selected segment using `Paint.measureText` and font metrics.
- [x] Run `python scripts/check-camera-zoom.py build/cherrygram-android` and compile the overlay against Android SDK 36.

### Saved Messages header
- [x] Modify scratch `TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java`; keep Saved Messages search/selection material full-width and fade its alpha with `max(searchFactor, actionModeFactor)`. The resting title never acquires a capsule.
- [x] Export `patches/android/0156-saved-header-settings-and-camera-motion.patch`; run `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref dc780e81ed1261c369c27870e8e0999a1eb0b600`.

### Original settings and badge artwork
- [x] Extend `design/settings/icons.json` and `scripts/generate-settings-icons.py` for notification, data, sticker, energy, devices, help and policy glyphs. Map stock Telegram settings icons without replacing global chat action assets.
- [x] Replace developer, tester and heart paths in Android drawable and UIKit artwork. Preserve supporter (Nebula logo) and star (Mira) resources byte-for-byte.
- [x] Run `python scripts/generate-settings-icons.py --check`; inspect the rendered glyph sheet at profile size.

### Project support
- [x] Add a themed `NebulaSupportFragment.java` and settings entry, with donation link, badge explanation, and proof submission instructions using approved project details. Use the existing server-issued `supporter` badge, never a local award after opening a link.
- [x] Reflect support in the UIKit settings screen and route approved links through the existing link-opening mechanism.
- [ ] Payment recipients and badge thresholds depend on the pending user answer; do not copy Cherrygram recipient details or promise unapproved tiers.

### Delivery
- [ ] Run the patch-series checks and affected contract checks. Commit exact task paths, push the authorized Git branches, and verify the APK workflow.
- [ ] Record device-only camera and animation QA separately from compile and fixture verification.

Verification: camera model, mocked camera discovery and recorder deferred-close fixtures pass; Java zoom overlay compiles against API 36. Android 151-patch series and iOS 59-patch bootstrap pass. The role sheet was inspected at 16 px on light/dark surfaces. Exact logo and Mira PNG resources and embedded UIKit image data have no changes. Native APK and macOS checks are the next delivery gate.

Support payment destinations, confirmation contact and badge thresholds have not been provided. The current support screens route to the existing official project channel and use the existing server-issued supporter kind; no invented payment destination or price is published.
