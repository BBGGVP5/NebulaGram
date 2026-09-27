# Saved Messages retention and liquid glass implementation plan

**Goal:** Retain locally cached Saved Messages deleted from another client, reduce archive work on the sync queue, and make refraction fluid without abrupt text displacement.

**Architecture:** Keep interception at the existing remote-deletion hook. Add Saved Messages as a separate retention scope, skip already archived IDs before SQL, and avoid rewriting non-expiring message blobs. Preserve pending snapshots across account switches. For glass, replace the discontinuous edge distortion with bounded, smooth displacement; keep the existing zero-refraction option and adaptive fallback.

**Tech stack:** Android Java, Telegram SQLite, encrypted AtomicFile snapshots, AGSL, JVM regression harnesses.

## Archive

Files: `NebulaDeletedArchive.java`, `NebulaRetentionPolicy.java`, `NebulaDeletedStyle.java`, `NebulaPrivacyFragment.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; `scripts/check-deleted-retention.py`.

- [x] Add a pure eligibility helper: `incoming || (owner != 0 && peer == owner)`; test outgoing Saved Messages, outgoing ordinary chats, and logged-out state.
- [x] Remove the explicit owner exclusion and use the helper before the existing protected/secret/expiring policy. Add `SAVED = 4` and its own switch.
- [x] Filter existing archive IDs before querying SQLite; prepare one update statement per affected chat and serialize only expiring messages. Test repeated deletion events and explicit/global scopes.
- [x] Keep latest pending snapshots visible while the writer is running, including after switching accounts. Move read-only archive sorting outside the shared monitor. Test the real snapshot/filter and async writer methods with in-memory fixtures.
- [x] Explain that Saved Messages must have reached this device before remote deletion; already removed uncached content cannot be reconstructed.

## Glass

Files: new ordered Android patch for `TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl`; `NebulaGlassSettings.java`; shader regression script.

- [x] Fade refraction to zero on both boundaries of the edge band and cap its displacement relative to edge thickness. Test continuity, zero bypass and bounded finite results across slider values.
- [x] Add a convenient liquid-glass preset to the existing live controls without overwriting settings until selected. Keep the preview synchronized with every changed control.
- [x] Compile touched Java against the local Android/Telegram classpath, apply all patches to pinned upstream in scratch, run regression checks, then commit and push the Android build.

Runtime acceptance: cached Saved Messages deleted on desktop remain marked on Android; local deletions remain local deletions; switches affect each scope separately; scrolling text does not jump at the glass boundary. Device-only acceptance must be reported separately from JVM checks.

Validation: touched Android Java classes compile; production archive-loop/writer fixtures, 512 retention-policy cases, 50,000-entry index regression, 505,505 scalar shader samples, settings design/contract and wallpaper preview checks pass. All 121 Android patches apply to the pinned source. iOS bootstrap without Swift passes after regenerating the embedded catalog. Android device rendering/desktop-to-phone deletion has not been exercised; no phone is attached.
