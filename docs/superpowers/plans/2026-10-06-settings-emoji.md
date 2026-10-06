# Settings presentation and emoji playback implementation plan

**Goal:** Match the requested grouped settings presentation, keep every section introduction above its controls, and make the shield visibly animate with correct lifecycle behavior.

**Architecture:** Reuse the shared row, hero and settings-root components. Keep all existing settings and icon-pack choices. Use Telegram's animated emoji document when it decodes; keep the native emoji visible until then, with a small local intro motion when a server animation is unavailable. Run inline in the already authorized chat; preserve the modified vendor checkout.

**Tech Stack:** Android Java views, Telegram ImageReceiver/RLottie, ValueAnimator, existing JVM checks and Android CI.

### Settings structure and icons

Files: `NebulaPrivacyFragment.java`, `NebulaSettingsHubFragment.java`, `NebulaSectionFragment.java`, `NebulaDesignFragment.java`, `NebulaSettingsHero.java`, `NebulaSettingsLayout.java`, `NebulaRow.java`, `NebulaSwitch.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`.

- [x] Move the privacy introduction before both feature-control cards and password navigation; use the privacy title for the whole page.
- [x] Put introductory emoji/title/description before controls in category and section pages. Preserve preview widgets below that introduction and preserve row-index search routing.
- [x] Remove colored icon squares, use 24dp monochrome glyphs inside the existing 32dp slots, and use 16sp/13sp row typography. Keep artwork and flags colored; change connected/destructive icon tint without assuming an icon background exists.
- [x] Use the selected switch style in settings, including the existing outlined Material renderer for style zero, rather than the separate compact filled track.
- [x] Support the section page's nested scroller in the shared glass header, disable clipping to padding, and show the small bar title only as the large introduction scrolls away.

### Emoji playback

Files: `NebulaAnimatedEmoji.java`; executable check `scripts/check-settings-emoji.py`.

- [x] Defer the loaded callback until ImageReceiver has installed the drawable. Hide the native fallback only when a Lottie or video animation is decoded, rather than on thumbnail arrival.
- [x] Apply repeat/start permissions to the actual receiver on focus and visibility changes. Stop playback, observers and fallback motion on detach and when reduced motion is active.
- [x] For large introductions without a decoded animation, animate the existing native emoji gently; do not substitute an unrelated emoji or generate replacement artwork.
- [x] Execute production-component lifecycle fixtures: missing document, thumbnail arrival, decoded animation, lost/restored focus, reduced motion and detach. Typecheck against Android SDK/native signatures.

### Verification and publication

- [x] Run the settings root and navigation checks, update their framework stubs for nested scrolling and title collapse, and rebuild the complete pinned Android patch series.
- [ ] Run required CI, finish a fresh APK and verify its source revision, version, release certificate and resources. Update the changelog with the actual UI behavior and device-testing limits.

The user's clarification says the shield emoji animation is the failure; this task does not require rewriting the ad-blocking engine. Browser filtering retains its existing opt-in behavior.

Local results: 85 required checks passed, including production emoji lifecycle fixtures and nested settings-root geometry/title-collapse checks. Account-slot C++ validation on Windows is syntax-only; CI executes its Linux binary. Thirteen shared helper classes typechecked against Android SDK 37 and native signatures. All 177 pinned native patches reconstructed. ADB reports no attached phone.
