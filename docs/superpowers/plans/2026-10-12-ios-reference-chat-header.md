# iOS reference chat header implementation plan

> **For agentic workers:** Execute inline, task by task; optional superpowers execution skills are unavailable. Do not dispatch agents.

**Goal:** Implement the complete toolbar composition confirmed by the user: Edit at left, centered Chats/title and collapsed stories, Add Story and Compose at right, expanded story row below.

**Architecture:** Prepare an owned fixture at the pinned iOS revision with all 100 current patches. Restore visible native toolbar materials and the separate left/right control capsules, then reconcile the actual root toolbar/title bindings with the reference. Preserve connection/proxy status, editing, configured hiding preferences and the previous story geometry fix.

**Tech Stack:** Swift/UIKit/ComponentFlow, ordered native patch, existing bootstrap geometry tests, full release_arm64 IPA.

### Tasks

- [x] Prepare `build/ios-header-reference-20261012` with current ChatListController, ChatListControllerNode, ChatListHeaderComponent, ChatListNavigationBar and StoryPeerList sources; inspect the real title/button bindings.
- [x] Create `patches/ios/0101-reference-chat-list-toolbar.patch` for the complete toolbar treatment. Keep native Edit/Done handlers, story/compose actions, compact title/status and expanded stories, while showing the control materials seen in the reference.
- [x] Extend the actual source/bootstrap guards for the full composition and run `python platform/ios/tools/check-bootstrap.py`. Execute geometry/SDK checks in macOS CI.
- [x] Commit exact paths, push the already authorized refs and verify a full IPA at the final runtime revision. Record physical iPhone appearance validation honestly.


Final validation: bootstrap 38297459032 at 3f1f07f passed 159 tests and 5394 production story geometry cases. Full IPA 110 / run 38297460672 passed and its source, bundle, arm64 application/extension and SHA-256 `9e72d6ce2393af0f43ebecfc8b86c4737402f38a7571d1f35c332f9937f704ee` were verified. The archive requires user signing; physical iPhone appearance acceptance remains unverified.
