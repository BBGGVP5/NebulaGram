# iOS Chat Call Menu

**Goal:** Adapt Android's `menu_call` and `menu_video` preferences to the native iOS peer-avatar context menu.

**Scope:** Present voice and video actions only for another non-bot user. Reuse Telegram iOS's existing `callPeer` flow, including draft-discard and privacy handling. Do not change the main screen header.

- [x] Persist both transfer keys with enabled defaults, expose controls, and cover type/persistence/import validation.
- [x] Add the peer-menu actions with native labels/icons and route them to the existing call handler.
- [x] Validate ordered patches and the settings contract.
- [ ] Compile the affected native module and verify call visibility/behavior on a device.
