# iOS reference chat header implementation plan

> **For agentic workers:** Execute inline, task by task; optional superpowers execution skills are unavailable. Do not dispatch agents.

**Goal:** Implement the complete toolbar composition confirmed by the user: Edit at left, centered Chats/title and collapsed stories, Add Story and Compose at right, expanded story row below.

**Architecture:** Prepare an owned fixture at the pinned iOS revision with all 100 current patches. Restore visible native toolbar materials and the separate left/right control capsules, then reconcile the actual root toolbar/title bindings with the reference. Preserve connection/proxy status, editing, configured hiding preferences and the previous story geometry fix.

**Tech Stack:** Swift/UIKit/ComponentFlow, ordered native patch, existing bootstrap geometry tests, full release_arm64 IPA.

### Tasks

- [x] Prepare `build/ios-header-reference-20261012` with current ChatListController, ChatListControllerNode, ChatListHeaderComponent, ChatListNavigationBar and StoryPeerList sources; inspect the real title/button bindings.
- [x] Create `patches/ios/0101-reference-chat-list-toolbar.patch` for the complete toolbar treatment. Keep native Edit/Done handlers, story/compose actions, compact title/status and expanded stories, while showing the control materials seen in the reference.
- [x] Extend the actual source/bootstrap guards for the full composition and run `python platform/ios/tools/check-bootstrap.py`. Execute geometry/SDK checks in macOS CI.
- [ ] Commit exact paths, push the already authorized refs and verify a full IPA at the final runtime revision. Record physical iPhone appearance validation honestly.


Local validation: Go API/settings tests pass, native import/layout guards pass, clipboard helper typechecks against Android SDK 37.0, and the iOS bootstrap applies all 101 patches strictly. Native platform builds remain pending.
