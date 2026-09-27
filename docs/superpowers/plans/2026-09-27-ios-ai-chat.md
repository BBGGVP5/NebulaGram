# iOS AI Chat Implementation Plan

**Goal:** Port Android's conversational AI UI, live local-model readiness and optional glass home shortcut to UIKit.
**Architecture:** Reuse NebulaAiService and opt-in history; add a bounded Foundation conversation model. UIKit owns messages, Markdown, cancellation and keyboard layout. ChatListUI posts a scoped request to TelegramRootController to avoid a SettingsUI dependency cycle.
**Tech Stack:** Swift, UIKit, SwiftSignalKit, FoundationModels, existing Bazel integration.

- [ ] Add `NebulaAiConversation.swift` to the Swift contract and XCTest cases for six-turn bounds, provider changes and input limits. Mirror with `python platform/ios/tools/generate-overlay.py`.
- [ ] Replace `NebulaAiChatController.swift` form with a scrolling transcript, fixed composer, native Markdown spans, animated waiting, stop/new-chat controls, copy and explicit apply-to-draft. Guard every completion by a request UUID; cancel on dismissal.
- [ ] Add opt-in `homeShortcut` and notifications to `NebulaAiSettings.swift`; expose the toggle in `NebulaAiController.swift`. Poll local readiness only while visible/active, updating the state row without reloading editing fields. Apple controls downloads; display preparation status without fabricated percentages.
- [ ] Add `0032-ai-home-chat.patch` for live home-button selection and scoped presentation from TelegramRootController. Keep camera behavior when disabled, native glass and iPad-safe sheet presentation.
- [ ] Rename the conflicting `move:` selector in `NebulaGlassController.swift`. Expand real UIKit SDK typechecks in bootstrap to cover chat/settings/glass, not just syntax.
- [ ] Validate ordered patches with `python platform/ios/tools/check-bootstrap.py`; push; run Swift tests/SDK checks plus native integration and unsigned IPA workflows. Clearly distinguish completed checks from builds still running and unavailable device QA.
