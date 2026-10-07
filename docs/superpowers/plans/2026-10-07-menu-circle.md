# Circular menu reveal refinement

**Goal:** Open and close menus through a small blurred circle independent of the source control shape, with smoother, faster motion and stable final positioning.

**Architecture:** Keep the source control only as a position anchor. Use one fixed 44dp visible seed for every menu, accounting for native drawable padding. Keep the popup's final geometry and hit targets native. Use synchronized growth/travel with zero-velocity endpoints, a gradual circle-to-panel radius, and delayed content focus. Preserve message context-menu opt-out and reduced-motion behavior. Work inline and leave the modified vendor checkout untouched.

- [x] Replace source outline/size sampling in `NebulaMenuReveal` and `NebulaMenuViewport` with a fixed circular seed.
- [x] Tune `NebulaMenuBubble` for a 420ms open and 160–240ms close, smooth travel/size/radius, and softer delayed text reveal. Avoid extreme content scaling and final-position shifts; interrupted close must begin at the displayed frame.
- [x] Replace obsolete source-shape/old-reference expectations with circular seed, early content suppression, monotone geometry, exact endpoint and interruption regressions. Keep native touch, viewport, message-menu and API/power gates verified.
- [x] Reconstruct the pinned patch series, run required checks and SDK compile checks, publish the change and verify a fresh APK.

Physical-device validation depends on an attached device. Do not describe visual smoothness as device-verified when ADB has none.

### Screenshot follow-up: missing gear and static link

- [x] Use the same `RestrictedEmoji` set Telegram's native top views use; resolve documents from normalized sticker-pack emoticons and custom-emoji attributes. Keep the existing per-view receiver and frame-zero replay.
- [x] Request the set through the account MediaDataController, observe `groupStickersDidLoad`, deduplicate native requests, and retain cached animated documents across reentry. Avoid repeated network requests from drawing/layout callbacks.
- [x] Normalize emoji presentation selectors for stock artwork fallback so the gear never becomes a null drawable. Add fixtures for delayed set loading, selector variants, cached reentry and observer cleanup.

Local verification: all 87 required checks pass, including 288,000 open/close/interruption frames, circular seeds for varying source control sizes, clipped native completion positions, delayed/cached RestrictedEmoji loading, normalized gear lookup and receiver/observer cleanup. Production popup/emoji helpers compile against Android SDK and native APIs. All 178 patches reconstruct on the pinned source. ADB has no device; C++ slot checks are syntax-only locally and executable in CI.

Delivery: Android 393 / versionCode 1000393 succeeded in run 37649133047 from source `e68671bd52766f808ee3e55e730110749c18bdd0`. Settings contract run 37649133030 succeeded. APK SHA-256: `8778b26fdfa5e4553f1564a6f51d56b87cea324bef929ccf64d6f4648a0fc883`. Package `app.nebulagram.messenger`, arm64-v8a/native core, bundled icon pack and the existing signing certificate were verified in `build/qa-apk-menu-circle-e68671b/local-verification.json`. Physical-device smoothness and the actual Telegram account's asset downloads remain unverified without a device. The native page emoji set downloads on first use and remains cached; stock artwork is retained while its animated frame loads.
