# Chat loading, home controls and Xray update

**Goal:** Make home-button hiding discoverable, remove demonstrated local bottlenecks without changing Telegram sync semantics, and validate the latest requested Xray snapshot.

**Architecture:** Keep upstream networking and database ordering intact. Optimize only NebulaGram archive indexing and redundant work; move latency probes off the command queue. Keep subscription mutations serialized. Clean up the recorded tunnel endpoint before loading the core, including failure paths, without touching arbitrary user proxies.

**Tech stack:** Java/Android, Go, pinned upstream patch series, Python regression harnesses.

## Work units
- [x] `NebulaSettingsSearch.java`, `NebulaSectionFragment.java`, localized resources: append both existing hide-button entries (do not shift stored search IDs), label the block and navigation subtitle; test settings and native visibility hooks.
- [x] `NebulaDeletedArchive.java`: replace repeated linear membership scans in prune/purge/retain with hash indexes; avoid sorting/republishing unchanged archives. Preserve persistence-before-filtering and per-account clear behavior. Test production helpers with large synthetic archives.
- [x] `NebulaLink.java` and a small command scheduler: run `probe.servers`/`probe.url` on a separate serial queue after initialization; keep mutations ordered; clear only the saved NebulaLink loopback endpoint before core.init. Test a blocked probe versus stop/settings command, and initialization FIFO.
- [x] `runtime/xray/go.mod`, `bind/go.mod`, sum files: pin Xray release v26.9.9 commit `52a412d9e2f5c2a5142b1b4e2ab3771dacb8b120` (upstream marks it prerelease; no v1.260909.0 tag). Resolve Go pseudo-version, compile runtime and bindings, run loopback smoke tests and full core tests. Adapt registered transports if upstream removed a package; document compatibility.
- [x] Verify latest official Telegram master SHAs against both gitlinks and record exact references. Do not reset vendor or merge upstream when SHAs are identical.
- [x] Apply the ordered Android patch series to a disposable tree; copy current overlay to verification tree, compile Java and run targeted CI regression tests. Do not claim the user's minute-long incident is reproduced without device/network logs.

## Upstream verification (2026-09-13)
- Android: `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c`, 12.10.1 (7038), 2026-08-25 — identical to pin.
- iOS: `6ad963e5b62d354da79040f388ae2b9132fb17b8`, 12.9.2 — identical to pin.
- Xray stable/latest endpoint: 26.3.27 (current); newest tagged prerelease: 26.9.9. User requested update; validate the prerelease explicitly rather than pretend it is stable.

## Implementation and validation

- User A/B test: archive enabled makes updating slow; disabling appears immediate. This supports the storage-queue diagnosis but is not a measured device reproduction.
- Replaced quadratic archive difference/membership loops with hash indexes. Decrypted JSON and scope/message membership are reused for the latest account; no reread, re-sort or whole-index scan for repeated/irrelevant deletion updates. Copy-on-write snapshots are published only after successful file persistence. An actual new archival batch still serializes and encrypts the index once; the archive's on-disk format and ordering of Telegram deletions are unchanged.
- Retention has no time-based trimming, including after wall-clock correction. Manual clear rebuilds both indexes; account identity remains guarded. Archive UI receives copies, not mutable cached JSON objects.
- Home camera/compose settings are now in settings search (appended so existing search-history IDs stay stable) and under a labeled chat-list-button group.
- Probes get a separate serial queue. Subscription mutations remain on the control queue. Proxy withdrawal matches the recorded port plus empty credentials, runs before Go initialization, and also handles a missing proxy-list entry; unrelated local proxies and the call-routing preference survive.
- Xray **26.9.9 prerelease**, pinned as Go pseudo-version `v1.260327.1-0.20260908222543-52a412d9e2f5`. Go toolchain raised to 1.27, including core CI jobs.
- Android AAR linking exposed an upstream `anet` compatibility requirement. Android build now uses `-checklinkname=0` as documented by [anet](https://github.com/wlynxg/anet#how-to-build-with-go-1230-or-later); iOS/desktop flags are unchanged. Runtime network restrictions are not disabled.
- Xray's new default blocks the loopback target of the integration-test VLESS server. The **test server only** explicitly allows 127.0.0.1/32; production configurations are unchanged.
- **PASS**: 83 ordered Android patches applied to a disposable tree, vendor untouched.
- **PASS**: all 36 Android workflow check scripts. New executable Java harness: 50,000 archive records, 10,000 updates with no further JSON scans/decryption, account/scope isolation, failed snapshot load, clear and clock correction; blocked-probe scheduling and recorded-proxy cleanup cases.
- **PASS**: `go test ./core/... ./runtime/xray/... ./bind/...`, `go vet` for all three modules; full Xray-registry build/test (`-tags xray_full`). The mobile smoke test transfers actual HTTP traffic through local SOCKS/VLESS and verifies disconnect closes the listener.
- **PASS**: Go 1.27 Android arm64 AAR built; checked classes.jar and AArch64 ELF libgojni.so. Output: `build/nebulalink-xray-26.9.9.aar` (not an installable APK).
- **Not run locally**: Go race detector (no Windows C compiler configured), iOS XCFramework/IPA or device UI/network reproduction. Core CI keeps its race-detector checks.
- **PASS**: full Java compile and standalone app resource processing (3m 26s), then repeat against the freshly built Xray AAR (57s). Initial disposable-tree Firebase-package fixture mismatch was corrected using the same two-package mapping as `inject-keys.sh`; no production credentials changed.
- **PASS**: `go mod verify` for runtime and bindings; `git diff --check`.
- Native-library audit found Go AAR LOAD segments were only 4 KB aligned under NDK r27. Added Android-only max/common page size linker flags per [Android guidance](https://developer.android.com/guide/practices/page-sizes). Rebuilt and verified every Go ELF LOAD segment is 16 KB aligned; Java API bytes match the Gradle-verified AAR. This does not certify the rest of the APK or replace a 16 KB device test.
- Final AAR SHA256: `d5d4afc94c559940011600ce08ed3ef99d8d7de2b7153c3bab670247c8f18c46`.
- No CI APK/IPA build dispatched during this task.
