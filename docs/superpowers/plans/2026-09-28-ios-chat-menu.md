# iOS Chat Menu Search Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply Android's `menu_search` preference to the iOS chat header context menu without removing the separate native search action.

**Architecture:** Keep the transferable Boolean in `NebulaSettingsStore`, expose it beside chat-header presentation controls, and omit only the Search row from the peer and topic avatar menus in `ChatController.swift`. The dedicated navigation/search route remains available, matching the Android setting's scope as a menu preference.

**Tech Stack:** Swift, UIKit, Telegram iOS Bazel modules, ordered patches, SwiftPM.

---

### Task 1: Persist and expose the preference

**Files:** `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaSettingsStore.swift`, generated overlay mirror, `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsController.swift`, `platform/ios/NebulaSettingsContract/Tests/NebulaSettingsContractTests/SettingsStoreTests.swift`

- [x] Add `menu_search` to `editableKeys` and `public var menuSearch: Bool { boolean("menu_search", fallback: true) }` in both store copies.
- [x] Add a unique `navigationToggle` stable ID and the `Поиск в меню чата` / `Search in chat menu` switch.
- [x] Test default true, persisted false, type rejection, and transfer activation.

### Task 2: Filter only chat-menu Search rows

**Files:** `patches/ios/0040-chat-menu-search.patch`, pinned upstream `submodules/TelegramUI/Sources/ChatController.swift`

- [x] Add `if NebulaSettingsStore.shared.menuSearch` around the Search action appended to the peer-avatar context menu and the reply-topic-avatar context menu.
- [x] Keep `beginMessageSearch` and the dedicated navigation search button unchanged; do not touch the home header.
- [x] Validate the complete ordered patch series against the pinned iOS commit.

### Task 3: Verify and deliver

**Files:** `platform/ios/PARITY.md`, `patches/ios/HOOKS.md`

- [x] Record source wiring and the native/device validation status.
- [ ] Run contract/bootstrap CI and a native module build; fix compilation errors before declaring native acceptance.
- [x] Commit and push only this chat-menu change, leaving the pre-existing home-header work uncommitted.
