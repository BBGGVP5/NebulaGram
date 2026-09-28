# Settings features visible in the Cherrygram reference

This inventory compares the supplied screenshots with NebulaGram's settings and native consumers. The reference is used for grouping and visual density; NebulaGram retains its own themes, background and drawn icon set. Camera configuration is outside this settings inventory. Round-video recording and zoom are tracked separately.

| Reference feature | NebulaGram state | Platform note |
| --- | --- | --- |
| Spring/navigation style | Available | Android and iOS have native transition consumers. |
| Centered title, folder title, compact bottom navigation | Available | Android and iOS; Android header timing and touch targets are being repaired. |
| Hide stories, chat separators, optional home controls | Available | Android and iOS have connected settings. |
| System emoji and font | Android available | iOS already uses system rendering; no separate override is exposed. |
| Per-chat password and local deleted-message archive | Available | NebulaGram uses its own privacy screens rather than Cherrygram's passcode layout. |
| Fade-only navigation, predictive-back setting | Missing | Requires native transition and gesture integration; iOS has system edge-back gestures. |
| Mute non-contacts, ignore mentions, default notification icon | Missing | Notification policy and Android icon selection need native notification-path changes; iOS app notification icons are system-controlled. |
| Archive stories automatically, hide the archive row | Missing | Requires native story/chat-list data-source integration on both platforms. |
| Snowflake decorations, per-chat custom wallpaper controls | Missing as NebulaGram settings | Telegram's wallpaper controls remain available; extra decoration is not enabled by this change. |
| Custom Saved Messages chat, quoted replies, vibration suppression | Missing | Message construction and interaction paths need platform-specific hooks. |
| Biometric confirmation for destructive actions, system PIN fallback | Missing | Must use each platform's native authentication result before deleting local data. |
| Heap diagnostics and memory warning | Missing | Android and iOS report memory differently; a cosmetic switch without a working action is intentionally not exposed. |

The current polish pass changes the settings presentation and fixes the concrete header, profile and round-video defects from the screenshots. A missing row is not exposed as a toggle until its behavior is connected and tested on its platform.
