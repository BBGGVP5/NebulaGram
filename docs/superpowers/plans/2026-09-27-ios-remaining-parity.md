# iOS Navigation and Appearance Parity Plan

**Goal:** Port the remaining practical Android navigation and appearance controls to native Telegram iOS modules without inert switches.
**Architecture:** Keep settings in NebulaSettingsStore and connect each key to a native consumer. Add a separate own-profile controller rather than reusing Settings. Observe store changes for live tab and header updates; keep a route to Settings when tabs are hidden. Preserve native gesture and accessibility behavior.
**Tech Stack:** Swift, UIKit, SwiftSignalKit, Telegram Bazel modules, ordered iOS patches.

- [x] Add profile tab, settings/profile visibility, labels and compact tab presentation with live updates. Add controls to Nebula settings and tests for persistence/import defaults. Keep Chats mandatory and Settings reachable.
- [x] Add camera and compose visibility controls to ChatList native buttons. Preserve the opt-in AI shortcut and story recording behavior.
- [ ] Adapt chat header and folder presentation controls to the existing iOS nodes. Reuse native layout paths and run ordered patch validation.
- [ ] Inventory Android-only profile/menu/font switches. Implement UIKit-equivalent consumers where public hooks exist; leave Android-only values pending rather than exposing a switch with no effect.
- [ ] Compile Swift contract and patched UIKit/Telegram modules, then build IPA. Record native and device validation separately.
