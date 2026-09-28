# iOS Topic Mute Menu

**Goal:** Give the existing iOS topic-avatar mute/unmute action a transferable visibility preference matching Android's `menu_mute` control.

**Scope:** Only the chat topic context menu. The app's notification settings and main screen header remain unchanged.

- [x] Persist `menu_mute` with an enabled default, expose a switch, and cover persistence/import/type validation.
- [x] Replace the disabled topic-menu condition with the preference without changing the native mute implementation.
- [x] Validate the ordered iOS patch series and settings contract.
- [ ] Compile the affected native module and verify menu behavior on a device.
