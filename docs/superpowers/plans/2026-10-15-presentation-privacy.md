# Chat-list presentation privacy implementation plan

**Goal:** Let the user hide chat-row names, avatars and previews while showing the list, without changing Telegram profiles or messages.

**Architecture:** A default-off, device-local visual switch. Android renders an opaque cached neutral row before native sensitive drawing and substitutes accessibility text/events. iOS places a neutral row view above native content and masks the native accessibility getters. Existing story visibility is combined with the mask without changing the user's stored story preference. The Nebula account picker uses generic aliases and disables name search while masked. Open conversations, notifications, system surfaces and other profile/account selectors are outside this explicitly described scope.

**Tech Stack:** Android Canvas/cached paints, Swift/UIKit native list nodes, existing global presentation observation.

Execution is inline. This is a visual privacy option, not screen-recording protection or a new chat lock.

- [x] Add Android `NebulaPresentationPrivacy.java` and iOS Foundation `NebulaPresentationPrivacy.swift`; default off, one local boolean, live refresh, no settings/session export.
- [x] Patch Android `DialogCell` sensitive drawing, accessibility node and event before original text. Preserve native swipe/clip transforms and animation advancement; mask archive previews as well.
- [x] Add iOS `NebulaChatListPrivacy.swift` in ChatListUI, a weakly owned observer and a lazily created opaque mask inside the native content container. Patch layout and accessibility label/value. Cancel observers on destruction; no idle mask allocations.
- [x] Combine effective story visibility with this setting and notify existing header observers; never overwrite `show_stories`.
- [x] Add clear scope text/switch to privacy settings, mask names/@username in Nebula account pickers, and refresh immediately when returning.
- [x] Execute default/restore, renderer caching, live observation/destruction, generic accessibility and account-alias fixtures. Android SDK and iOS SDK 27.2 component checks pass; 221/114 ordered native patches replay.
- [ ] Full APK/IPA compilation for this final source batch; rendered/device and TalkBack/VoiceOver acceptance remain separate.

Files: Android overlay `NebulaPresentationPrivacy`, `NebulaFeatureControls`, `NebulaAppearance`, `NebulaAccountsFragment`; Android patch `0226-chat-list-presentation-privacy.patch`; iOS Foundation store/helper, `ChatListUI/Sources/NebulaChatListPrivacy.swift`, SettingsUI privacy/accounts controllers; iOS patch `0114-chat-list-presentation-privacy.patch`; `scripts/check-presentation-privacy.py`.
