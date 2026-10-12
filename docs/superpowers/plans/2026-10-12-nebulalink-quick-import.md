# NebulaLink quick import implementation plan

> **For agentic workers:** Execute inline, task by task; optional superpowers execution skills are unavailable. Do not dispatch agents.

**Goal:** Remove provider-panel navigation/branding and expose explicit clipboard import, paste and refresh actions on Android and iOS.

**Architecture:** Keep the existing `onboarding.import` core endpoint for subscription URLs, share links and pasted configurations. Read the clipboard only after the user's paste action. Use the current themed Android controls and native iOS controls, with the same busy/lifecycle gates as manual import.

**Tech Stack:** Go menu schema, generated Android strings/Java, UIKit Swift, core import tests and full platform builds.

### Tasks

- [x] Update `core/settings/menu.go`: remove `provider.open` from the servers menu; add `clipboard.import` and `subscription.refreshAll` under a quick-actions section; describe subscription URLs without panel branding.
- [x] Update the canonical translations in `scripts/gen-menu-strings.py`, then run `python scripts/gen-menu-strings.py` to regenerate Java/XML.
- [x] Add `NebulaLinkClipboard.java` and wire it into `NebulaMenuFragment.java` and `NebulaSubscriptionsFragment.java`. Clipboard action opens a prefilled import editor; the editor's paste button updates the field without dismissing it. Route mixed supported input through `onboarding.import`.
- [x] Add explicit clipboard/paste controls to `NebulaLinkController.swift` and `NebulaSubscriptionsController.swift`, retaining refresh and busy-state handling.
- [ ] Run the relevant Go core/import tests and `python scripts/check-menu-api.py`; update the existing layout guards for the new iOS action row. Build and verify the new APK/IPA at the runtime commit. Preserve dirty vendor checkouts.


Local validation: Go API/settings tests pass, native import/layout guards pass, clipboard helper typechecks against Android SDK 37.0, and the iOS bootstrap applies all 101 patches strictly. Native platform builds remain pending.
