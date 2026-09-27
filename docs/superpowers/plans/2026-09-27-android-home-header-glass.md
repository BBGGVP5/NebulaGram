# Android Home Header Glass Implementation Plan

**Goal:** Show the main chat-list title and actions as readable liquid-glass controls, including a round NebulaLink button, while preserving search and folder navigation.
**Architecture:** Reuse the existing blur source and ActionBar glass drawables only for the default home DialogsActivity. Keep the setting local to appearance preferences, and style the existing NebulaLink control rather than adding a second action. Give folder titles the actual free width instead of a half-screen cap.
**Tech Stack:** Java, Telegram Android ActionBar and DialogsActivity ordered patches, Android overlay, geometry checks, CI APK build.

- [x] Add a home-header glass preference and control to Appearance; default it on.
- [x] Set up existing ActionBar glass for the default chat list, draw separate round menu surfaces, and keep search/action-mode behavior native.
- [x] Render the existing NebulaLink shield as a round control with a clear active state.
- [x] Correct expanded title size and collapsed folder title width; verify safe bounds on narrow screens.
- [ ] Validate ordered patch application, local checks and Android CI build; record device-only limitations.
