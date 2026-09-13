# Editable Forward Copy and Status Headers Implementation Plan

**Goal:** Opt-in privacy switch exposing an anonymous editable copy in the forwarding preview, including text, photos, videos, files and albums; state-aware reusable headers.

**Architecture:** Android keeps deep-copied MessageObjects in the existing forwarding preview, tagged with a dedicated subtype. Only the normal Send action routes those copies through native sendMessage/sendMedia parameters after native posting and paid-message checks. iOS edits attributed text/captions in a native modal, then uses explicit Send and native paid/scheduled-message handling with message-backed media references. Neither platform mutates source messages or reuses forwarded attribution.

## Forward editor
- [x] Persist an opt-in setting on both platforms and show the action in forwarding options.
- [x] Preserve attachments, original album grouping, text entities, media spoilers and caption placement. Leave independently typed composer text untouched.
- [x] Native callback validates the complete Android batch before enqueueing any member, so a rejected last attachment cannot strand an album.
- [x] Keep original media references for file-reference refresh, paid posting, topics and scheduled sends. Lock author visibility for edited copies, including recipient changes.
- [x] Reject unsupported types rather than flattening them: protected/expiring messages, secret chats, rich articles, polls/paid media/stickers/voice/round video. Supported media editing means editing captions, not modifying image pixels or video timelines.
- [x] Prevent subsequent server message edits from replacing the edited copy in Android's preview.

## Headers
- [x] Android/iOS shared hero: shorter direct title, state badge, green right edge only for explicitly active features; neutral default.
- [x] Use active state for archive and configured/enabled AI. Add neutral headers to Android section screens (appearance, chats, bottom panel, general, folders, messages, profiles, controls, chat actions, about).
- [x] No animation loop/readback. UIKit avoids unconditional layout invalidation and preserves Dynamic Type. iOS other settings currently use alerts/item lists and were not migrated to new standalone screens in this pass.

## Verification
- [x] Android actual Java compilation passed against disposable full Telegram tree. Final post-review compile: BUILD SUCCESSFUL in 2m56s, recorded in build/forward-javac-verified.log.
- [x] Executable tests run the production eligibility and batch builder with an inert transport. Verify albums/final flags, captions, attachment identity, source reference, paid/scheduling/topic metadata, spoilers and preflight rejection without partial sends.
- [x] Settings tab tests: 600 transitions. AI availability: 53 cases. iOS 14-patch bootstrap passes. Swift native files parse.
- [ ] New Swift preference XCTest requires a Swift-equipped host. Full UIKit/Telegram typecheck and real-device sending/visual QA remain required; parsing is not a substitute.
- [x] No new application builds dispatched for this request. Preserved unrelated NebulaLink changes.
