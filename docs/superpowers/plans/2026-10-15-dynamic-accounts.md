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
