# Dynamic account registries implementation plan

**Goal:** Remove fixed Java/native instance storage while preserving the identity and lifetime of existing accounts, then enable additional accounts only after persistence and notification routing are migrated.

**Architecture:** Keep the existing numeric account IDs and directories. Replace controller arrays with lazy, safely published per-account instances. Replace the native switch with a process-lifetime registry and attach the Java delegate before starting each network thread. This first independently buildable step leaves the ten-account UI gate in place; enabling it before the remaining state arrays and notification IDs are migrated would be unsafe.

**Tech Stack:** Java 8, C++11, JNI, executable Java/C++ regression harnesses, ordered upstream patches, Android CI.

Execution is inline; the optional superpowers execution skills are not installed. Never modify the dirty vendor checkout.

## 1. Instance storage

- [x] Add `scripts/check-dynamic-account-instances.py`: compile the actual registry and exercise sparse IDs, 128 accounts, concurrent first access, failed construction/retry, per-account locks and independent currency registries.
- [x] Add `platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/AccountInstanceRegistry.java`, with no Android dependencies and no eager controller creation.
- [x] Migrate singleton getters in Android patch 0216; retain constructor arguments, non-creating probes and the separate global NotificationCenter.
- [x] Add native dynamic registry and move delegate creation to per-account initialization; remove indexed JNI environments without changing callback account IDs.
- [ ] Exercise the actual native getter concurrently and validate JNI thread ownership. Replay the complete patch series and compile the application.

## 2. Persisted IDs and UI state

- [ ] Migrate Theme, DialogsActivity, emoji and observer state arrays, including subscribers created before a new account is added.
- [ ] Persist the set of allocated account IDs before login starts; recover existing account 0 and accountN directories without renaming keys or sessions.
- [ ] Replace fixed-count iteration and all add-account entry points; avoid initializing inactive controllers merely to count accounts.
- [ ] Define cancellation, logout and ID reuse so delayed callbacks cannot reach a different account.

## 3. Notification isolation and acceptance

- [ ] Namespace notification IDs and PendingIntent identities, retaining dismissal of old notifications after upgrade.
- [ ] Test migration, restart, removal, failed login, more than ten accounts, stale callbacks and simultaneous notifications using synthetic accounts.
- [ ] Enable the additional-account UI after all preceding checks pass, add search/order for long lists, and document resource behavior.
- [ ] Compile APK, inspect package/signature and report device QA separately.

## Verification checkpoint

Commit `7827492`: all 113 Android workflow regression commands passed locally against the owned integrated tree. All 211 ordered patches replay on pinned upstream `f32ffeec763f0bdbf4bec25b52d654f77bff733d`. Java tests execute the production registry and all 35 extracted singleton getters; native tests execute the production getter concurrently under WSL. The three changed native translation units also pass NDK arm64/API 23 syntax checking using the pinned BoringSSL headers. Existing unrelated upstream warnings remain.

The settings-contract workflow passed. Full APK [run 38726030302](https://github.com/BBGGVP5/NebulaGram/actions/runs/38726030302) is running; neither a successful full build of this commit nor device acceptance is claimed yet. Previously verified APK 1000555 and IPA 120 do not include this new Android registry patch.

Follow-up: the full Java compiler rejected the old NotificationCenter for-each loop and four lazy-child-controller locks in MessagesController. Patch 0217 moves diagnostics to an existing-values snapshot and shares the registry's per-account monitor with those child controllers. The regression harness now compiles and executes the actual sanitize method and all four lazy-child getters, checking account IDs as well as singleton identity. Full rebuild is required.


## UI state migration checkpoint

Patch 0218 replaces fixed account arrays in Theme, DialogsActivity, SelectAnimatedEmojiDialog and FragmentContextView. Theme state restores each account lazily, preserves the existing account-0 preference suffix and saves only initialized entries, avoiding overwrites of unopened accounts. Dialog flags and observer teardown accept sparse IDs; the MessagesController reset call uses the same accessor.

`python scripts/check-account-state.py build/android-1302-integrated-20261015` executes native state methods for 128 accounts and ID 1000000, late theme restore, unrelated cache preservation and repeated observer teardown. The 35 production singleton getters and 16,384 concurrent registry lookups also pass. All 213 patches replay on the pinned official source. Account allocation, dynamic observer enrollment and notification identities remain the next integration phase; the ten-account UI gate is still present.


## Persistent ID and notification implementation

Patch 0219 migrates all 49 native files that enumerate account slots to snapshots of a persisted catalogue. Every add-account route reserves an ID with a synchronous storage commit before presenting login. Legacy preference names and native account directories are discovered without renaming sessions. Logout removes the ID before the next login can start, then detaches subscribers after the existing logout broadcast. IDs are never recycled; cancelled additional-account login also retires its reservation. Inactive/retired IDs are excluded from future enumeration; active accounts retain normal Telegram synchronization and notifications.

`AccountCatalog` has no Android dependencies. Its executable regression checks cover 256 concurrent allocations, restart, migration, removal, malformed metadata, exhausted integer IDs and failed commits. `AccountDirectory` tests execute actual delayed subscriber registration/removal, including creation before a pending callback and closing before callback delivery.

Patch 0220 namespaces Android message notification tags by full account and dialog ID; PendingIntent categories include the account and complete destination metadata on API 23+. Existing Telegram action/data routing is retained. Legacy untagged message notifications are removed on upgrade, preserving foreground and unrelated tagged notifications. Tests cover 128 accounts, full dialog IDs that previously produced identical 32-bit sums, idempotent binding and legacy cleanup. The real helper compiles against Android SDK.

The native registry test executes under WSL with 128 synthetic accounts and concurrent identity/delegate checks. All 215 native patches replay successfully. Full APK compilation of this migration and physical authentication/push acceptance remain required. The previous run 38729333972 passed for source 1c5f4d2; downloaded APK 1000557 matches the original signing certificate, package and API-23/arm64 identity, and passes 16-KB ZIP alignment. It predates patches 0218-0220.


Build 38734389269 rejected two account-entrypoint branches containing orphaned remnants of the old Premium check. Patch 0221 removes those remnants. The workflow now parses every patched native Java file and every overlay Java file with the real javac parser before dependency compilation; the current 448-unit syntax pass succeeds. This adds integration coverage beyond the isolated allocator/controller fixtures. Rebuilding the APK is required.


The complete application compiler then found two additional fixed holders in the separate AppStandalone SMS Jobs sources, outside TMessagesProj. Patch 0222 migrates its controller and service-intent maps, scopes the settings PendingIntent and removes stale service-instance references. The executable singleton inventory now covers 36 production getters, including AppStandalone, and the catalogue invariant scans every Java source module. AppStandalone Java compilation is now included before native assembly. No SMS enrollment, sending policy or server eligibility behavior is enabled by this migration.
