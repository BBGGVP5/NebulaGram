# iOS Navigation and Appearance Parity Plan

**Goal:** Port the remaining practical Android navigation and appearance controls to native Telegram iOS modules without inert switches.
**Architecture:** Keep settings in NebulaSettingsStore and connect each key to a native consumer. Add a separate own-profile controller rather than reusing Settings. Observe store changes for live tab and header updates; keep a route to Settings when tabs are hidden. Preserve native gesture and accessibility behavior.
**Tech Stack:** Swift, UIKit, SwiftSignalKit, Telegram Bazel modules, ordered iOS patches.

- [x] Add profile tab, settings/profile visibility, labels and compact tab presentation with live updates. Add controls to Nebula settings and tests for persistence/import defaults. Keep Chats mandatory and Settings reachable.
- [x] Add camera and compose visibility controls to ChatList native buttons. Preserve the opt-in AI shortcut and story recording behavior.
- [x] Hide the Send As avatar in the native composer without changing the selected sending identity; relayout on preference changes.
- [x] Wire centered chat title to the native title view, including subtitle alignment and a live layout refresh.
- [x] Respect the next-channel prompt preference in the existing chat history offer without changing navigation or unread state.
- [x] Use Telegram's existing seconds-aware timestamp formatter in message status, including edited and imported dates, and refresh open chats when toggled.
- [ ] Adapt chat header and folder presentation controls to the existing iOS nodes. Reuse native layout paths and run ordered patch validation.
  - [x] Wire the selected folder title to the native home header with a guarded filter ID and live settings observation.
- [ ] Inventory Android-only profile/menu/font switches. Implement UIKit-equivalent consumers where public hooks exist; leave Android-only values pending rather than exposing a switch with no effect.
- [ ] Compile Swift contract and patched UIKit/Telegram modules, then build IPA. Record native and device validation separately.
