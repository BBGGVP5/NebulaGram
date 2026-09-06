# Settings and gestures refinement

**Goal:** Address the new device screenshots: structure settings, explain AI setup, simplify previews, repair tab dragging and refine chat headers.

**Architecture:** Keep forms, project links and drawing helpers in the overlay. Patch native tab gesture routing and header integration in patch 0054 without replacing Telegram navigation or call actions.

**Tech stack:** Android Java views, Canvas, ValueAnimator and existing Telegram components.

- [x] Group settings and put expanded About last; add release, source and issue links. Channel URL will be supplied later.
- [x] Rebuild AI into labelled setup/request/response cards with visible credential state.
- [x] Align dividers, redesign profile sample and remove the folder sample requested by the user.
- [x] Recognize horizontal tab drags independently of long-press menus and add a bubble tap response.
- [x] Fade chat header shade from top to transparent; move call/search shortcuts into overflow. Saved Messages uses a standard Material You header regardless of floating/iOS style.
- [x] Remove hidden search space; bound switch thumbs consistently and identify platform styles; show compact, independent icon previews with a checkmark selection.
- [x] Center settings titles and expanded home titles under the existing centering preference.
- [x] Apply the system palette before constructing the notification-opened chat; refresh cached chat colors after global palette changes while preserving per-chat themes.
- [x] Separate AI/expand controls vertically from attachment/send and preserve native animation and restored layout.
- [x] Compile and check patch application and relevant regressions.
- [x] Commit and push.

## Validation

- `:TMessagesProj:compileStandaloneJavaWithJavac --offline`: successful against the final overlay and native patch.
- Complete series: 49 patches applied to 52 pristine upstream files; Java matches the compilation tree.
- Composer: 72 geometry cases, repeated accessory positioning/restoration, native scale/fade/translation and 14 folder bindings.
- Navigation/icon previews: legacy settings, hidden-tab combinations and all 9 current/preview pack combinations through nested resource wrappers.
- Layout: 72 hidden-search/header cases, 10 centering states, 1,818 switch shape bounds including RTL/intermediate frames.
- Native/appearance: 648 headers, 240 camera grid cases, 24 status icons, 1,080 message/menu combinations; unread updates and icon exclusions.
- Palette: 26 checks including consecutive themes without elapsed delay, recursive callbacks and accent restoration.
- AI: fixture-based protocol checks for four providers; no paid or real credential requests.

Device rendering, notification entry and touch gestures still require checking in the APK on a phone. No connected device was available. The Telegram channel remains omitted until the user supplies its URL.
