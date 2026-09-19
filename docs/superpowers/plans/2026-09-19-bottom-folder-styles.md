# Bottom Folder Styles Implementation Plan

**Goal:** Remove the exposed rectangular home-header blur when folders no longer occupy the top, and offer glass, ordinary and minimal bottom-folder styles without replacing native previews.

**Architecture:** Preserve native FilterTabsView selection, counters, scrolling and folder routing. A separate bottom-panel preference selects its background and selector; the existing folder_style still controls labels/icons. Scope the flat header to the compact home state, retaining search/action-mode/stories rendering.

**Tech Stack:** Android Java, native blur3 drawables, ordered upstream patches, Python/Java regression checks.

Execute inline under the user's existing implementation/build authorization.

- [ ] Add `NebulaFolderTabs.panelStyle()/setPanelStyle(int)` with clamped values 0 glass, 1 ordinary, 2 minimal and the existing live-change callback. Add the non-transfer preference to the catalog and regenerate mirrors.
  ```java
  public static int panelStyle() { return Math.max(0, Math.min(2, preferences().getInt("folder_panel_style", 0))); }
  ```
- [ ] Add a separate "Оформление нижних папок" selection row in `NebulaSectionFragment.java`, keeping the existing labels/icons row. Refresh the current native folder preview after selection and keep the choice while folders are on top.
- [ ] Patch `FilterTabsView.java`: retain the real blurred drawable, switch to an opaque rounded background for ordinary style and no background with underline for minimal. Apply liquid selection only to glass, preserve native selection animations and counters. Reapply on theme changes and when returning from settings.
  ```java
  int style = bottomPanel ? NebulaFolderTabs.panelStyle() : 0;
  ```
- [ ] Patch `DialogsActivity.java`: when top folders, stories and expanded search are absent, paint the header with the same opaque surface as the chat list and suppress the residual header shadow. Keep existing search/action-mode rendering. Wire the live folder listener to panel styling.
- [ ] Keep `NebulaFoldersPreview.java` and its native tabs, use the same panel-style API with a themed glass fallback in the absence of the chat-list blur source.
- [ ] Add tests executing the style/header policy and checking native renderer callbacks in `scripts/check-folder-panel-style.py`; add it to Android CI. Run the existing folder runtime/animation/swipe checks, settings contract checks, and all ordered patches in a disposable source tree.
  ```powershell
  python scripts/check-folder-panel-style.py build/folder-styles-0919/tree
  python scripts/check-settings-runtime.py build/folder-styles-0919/tree
  python scripts/check-settings-contract.py
  ```
- [ ] Review the diff, commit only task files, push and verify Android CI starts. Device visual validation remains separate from source/geometry checks.
