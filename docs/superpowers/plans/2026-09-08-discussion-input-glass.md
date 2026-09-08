# Discussion profile and input glass implementation plan

**Goal:** Add linked-channel previews to discussion profiles and finish glass/search/tab gestures without changing search counts or message actions.
**Architecture:** Reuse ProfileChannelCell and its account-scoped fetcher, native blur factories and attachment callbacks. Keep gestures in a small shared controller; preserve native behavior when disabled, on taps, cancellation and multi-touch.
**Tech stack:** Java Android overlays, ordered native patch 0069, Python/Java regression harnesses.

## 1. Channel preview
- [x] ProfileActivity: resolve linked channel only for a discussion group (not topics or broadcast channel); reuse channelRow/divider and VIEW_TYPE_CHANNEL.
- [x] Fetch/cache last post with ChannelMessageFetcher.fetch(linkedId, 0); never invent subscriber counts or timestamps. Open the linked channel through the row.
- [x] ProfileChannelCell: use fragment account rather than globally selected account. Preserve personal-channel rows and native story/thumbnail loading.
- [x] Test source wiring, group/user/channel routing and account isolation.

## 2. Search and glass
- [x] ActionBar: ensure hidden ordinary-chat menu is made visible for search and restored after search; preserve normal action-mode behavior.
- [x] ChatActivity: use wallpaper-backed glass for emoji panel and bottom search bar, rather than sampling empty content below the chat list. Do not change searchLastCount/searchLastIndex.
- [x] ProfileActivity: bind popup to the live profile blur factory and Nebula material.
- [x] ChatAttachAlert: reuse one external-window glass surface for sheet bodies; retain native fallback and custom web-app backgrounds. Make native file/contact/audio/location surfaces transparent only while this material is active.
- [x] Profile action surfaces: use existing native blur/material path rather than covering it with an opaque fill.

## 3. Tab lens and gesture
- [x] Create NebulaTabGesture: track horizontal drag after touch slop, cancel child touch once, follow finger, select on release, cancel safely; ignore multi-touch, preserve taps and vertical scroll.
- [x] PagerSlidingTabStrip: install lens/gesture for EmojiView's type switcher. Keep disabled-page restrictions and native ViewPager callbacks.
- [x] ChatAttachAlert buttons: lens and gesture for native attachment tabs only, dispatch through existing click callback after release; do not select bots by dragging.
- [x] Test cancellation, disabled targets, multi-touch, hit bounds, release and native bypass with Java test doubles.

## 4. Verification
- [x] Save native baselines before editing. Generate patch 0069 and verify it applies on the reconstructed prior series.
- [x] Add focused checks to android.yml and run all regression checks.
- [x] Final Java compilation passed (2m40s), including the later folder fix. Bytecode checks confirmed the latest methods and both emoji-tab initialization paths.
- [ ] Device acceptance: blocked by no connected ADB device. Runtime visual acceptance is not claimed.
- [x] Review scoped diff, preserve existing design files and unrelated changes. Deliver only verified claims.

## Added reports during implementation
- [x] Rich-message blank preview: bypass only recycler culling while drawing a lifted cell. Preserve Rich content and details state.
- [x] Blank dialogs after same-folder scrub: prevent same-target transitions, settle interrupted destinations, ignore progress without a visible destination.
- [x] Add production-method regression tests for both reports; old behavior fails these tests.

Notes: native wallpaper-backed material is reused for input panels. Attachment scrubbing intentionally excludes bots and long scrollable strips. Actual map tiles/web-app content remain native. Profile actions retain native blur with a highlight edge, not a new opaque fill.
