# iOS Chat List Dividers

**Goal:** Wire Android's `hide_dividers` choice to the native iOS chat-row separator.

**Scope:** Hide only the chat-list row divider. Preserve row hit targets, folder tabs, unread state and the main header.

- [x] Persist and expose the transferable Boolean with an off default.
- [x] Gate the ChatListUI separator node during row layout.
- [x] Test persistence/import/type validation and the ordered patch series.
- [ ] Compile and visually verify light/dark lists on a device.
