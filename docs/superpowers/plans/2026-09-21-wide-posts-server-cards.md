# Wide posts and server cards implementation plan

**Goal:** Add an opt-in wider chat layout on Android/iOS and keep each NebulaLink server visually distinct while scrolling.
**Architecture:** Reuse native text/media measurement, retain avatar/share/sidebar insets and default layout when disabled. Store `wide_posts` in the shared presentation contract. Use separate existing NebulaCard containers for server rows; retain selection and latency updates. Notification grouping is already explicitly wired per account and the screenshots show Android's collapsed summary; no notification changes without a demonstrated defect. Admin/payment work was explicitly cancelled.
**Tech stack:** Java Android views, Swift native Telegram list nodes, JSON settings contract, Python regression runners.

- [x] Add cached `NebulaWidePosts.enabled()` preference and geometry helpers, Android toggle under chat behavior, RU/EN labels. Include `wide_posts` default false in transfer schema. Validate external edits/import invalidation.
- [x] Patch Android MessageObject text margins and cached layout state; enable the native full-width media path with safe margins. Rebind the chat on resume after preference changes. Keep albums, round videos and stickers on their native layout paths.
- [x] Add iOS store/control and wire the existing `allowFullWidth` path in ChatMessageBubbleItemNode with its normal share/avatar/failed-send deductions; add the direct Bazel dependency and refresh reused chats after changing the option.
- [x] Replace the single tall server card with one existing rounded NebulaCard per server, 8dp vertical spacing. Preserve server order, titles, flags, selected/connected state and latency badges.
- [ ] Exercise disabled parity, widths/insets, toggle/import and layout invalidation. Run shared contract, settings regressions, iOS bootstrap and Android Java compilation. Review scoped changes, commit/push and start APK/IPA builds.

Validation so far: 2,560 production text-geometry combinations, cached reads, preference import/reset and cell invalidation passed; all 68 shared setting definitions and RU/EN labels validated. iOS bootstrap applies all 18 patches. Existing settings, palette, action capsule, preview and render-hot-path checks passed. Android Java compilation passed in 3m 34s (137 tasks); Apple SDK/device acceptance is separate.

Notification audit: `NotificationsController` assigns `notificationGroup` to children and a group summary; the second screenshot is a collapsed multi-chat summary. Android renders the group and its visual separators: https://developer.android.com/develop/ui/views/notifications/group . No notification or payment/admin code changed.
