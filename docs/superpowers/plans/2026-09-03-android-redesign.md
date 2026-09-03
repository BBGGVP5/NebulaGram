# Android Redesign Implementation Plan

> **For agentic workers:** Implement the bounded onboarding/authentication, chat chrome, and information-page tasks in this session. Keep upstream changes reproducible as patches; review each resulting integration before compiling.

**Goal:** Match the supplied rounded chat controls and onboarding prototype, including phone, verification code, password, group information and chat settings, with a standard composer option.

**Architecture:** Preserve Telegram's actual widgets, input listeners, navigation and authentication state. Put presentation and preferences in the Android overlay and integrate through explicit, small hooks against pinned Telegram 12.10.1. Reuse the upstream blurred-background renderer and keyboard insets, with normal rendering for unsupported/transient states.

**Tech Stack:** Android Java views, Telegram theme/blur APIs, persistent SharedPreferences, Gradle and git patch series.

### 1. Welcome and authentication

- [x] Build a shared responsive onboarding layout in `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaOnboardingLayout.java`, with a scrollable content region, bounded width and actions below content. Replace fixed 160dp content padding in `NebulaIntroFragment.java` and `NebulaConnectFragment.java`.
- [x] Extend `NebulaLoginStyle.java` with explicit phone, code and password presentation. Reuse native phone/OTP/password inputs and native callbacks; keep error/resend/recovery controls and special authentication methods functional.
- [x] Expand `patches/android/0006-login-typography.patch` with constructor/layout hooks and a full-width native continue button. Apply only to ordinary login and supported steps; leave other account operations functional.
- [x] Add live NebulaLink status and progress below actions, consistent title/field shapes, scrolling under IME, tablet width limits and RTL handling.

### 2. Chat header and composer

- [x] Add `NebulaAppearance.java` with `iosComposer()` and `chatHeader()` boolean preferences, enabled by default and persisted in `nebulagram` preferences.
- [x] Add `NebulaChatStyle.java` and a patch for chat chrome: use the native glass factory, separate attachment/voice circles and a rounded text capsule; preserve send/record/emoji controls and text listeners.
- [x] Keep upstream measurement/insets and native layout for complex recording, bot and editing transitions where separation cannot be applied reliably. Standard composer preference returns the original rendering and layout without replacing widgets.
- [x] Add appearance rows in `NebulaSettingsFragment.java`, plus RU/EN labels and descriptions. Apply the selected style when returning to the chat.

### 3. Chat information and settings

- [x] Add `NebulaProfileStyle.java` and hooks for ProfileActivity, ChatEditActivity and relevant shared information rows. Keep original participant/media adapters, item click listeners, role labels and permission checks.
- [x] Use consistent rounded grouped surfaces, spacing, theme-derived contrast and action buttons. Preserve avatar/header and media transitions and viewport-dependent sizing.
- [x] Add a `profileStyle()` preference and user switch in NebulaGram Settings.

### 4. Verification and delivery

- [x] Record every upstream hook in `patches/android/HOOKS.md`; revise `docs/DESIGN.md` to describe the implementation and maintenance limits.
- [x] Apply all patches in order against the pinned clean tree using an isolated index and copy overlays into `build/android-review/tree`.
- [x] Run `:TMessagesProj:compileStandaloneJavaWithJavac` with JDK 17. Resolve Java/resource errors before committing.
- [x] Check layout geometry for compact screens, landscape, large fonts and tablet bounds; verify all original input/action views remain reachable in code.
- [x] Run `git diff --check`, review source/patch agreement and validate available runtime previews. No physical device or available AVD is connected for visual runtime testing.
- [ ] Commit and push the completed changes, then verify the existing Android workflow starts for the exact commit.

## Acceptance checks

Welcome and tunnel screens remain usable with the keyboard visible. Phone country selection, OTP autofill/paste/resend, password masking/recovery, back and cancellation still use Telegram handlers. Message typing, sending, long-press recording, attachment menu and emoji remain available; standard composer restores the upstream view. Chat information retains members, media, role labels and management actions. No real OTP or account credentials are required for presentation verification.
