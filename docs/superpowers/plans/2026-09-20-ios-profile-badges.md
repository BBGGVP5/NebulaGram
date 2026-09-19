# iOS Profile Badges Implementation Plan

> **For agentic workers:** Execute inline in this session; no delegation is needed.

**Goal:** Show assigned Nebula badges, including the user's selected Mira artwork, in native iOS profiles.

**Architecture:** A Foundation client reads the existing public per-user badge endpoint with a bounded memory cache. A UIKit artwork helper supplies original-color images. An ordered upstream patch adds views to both native profile title states, preserving Telegram status icons and transitions.

**Tech Stack:** Swift, Foundation/URLSession, UIKit, XCTest, Bazel, pinned Telegram iOS overlay.

## Work and verification

- [x] Add `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaBadges.swift`: allowlisted `supporter`, `dev`, `tester`, `star`, `heart`; `star` displays Mira. Fetch `https://hooks.nebulaguard.mooo.com/v1/badge/{positive-user-id}`; no credentials. Cache positive/negative answers for 24 hours, errors for 60 seconds, coalesce concurrent requests, deliver on main queue.
- [x] Add `Tests/NebulaSettingsContractTests/NebulaBadgesTests.swift` covering parsing, invalid IDs, same-user coalescing, expiry, bounded cache, negative results, bad status/data, retry after failure, main-thread delivery. Inject transport and clock; never query real users in tests.
- [x] Add `platform/ios/overlay/submodules/TelegramUI/Components/PeerInfo/PeerInfoScreen/Sources/NebulaProfileBadgeArtwork.swift`. Embed the exact Android PNGs for supporter/Mira; reproduce the other three existing vector shapes in UIKit. No external image downloads or tinting.
- [x] Build a disposable sparse tree from the pinned revision and apply the existing patch series. Add `0017-profile-badges.patch` for `PeerInfoHeaderNode.swift`: only user profiles without a topic, weak asynchronous callback with peer identity check, regular/expanded title children, extra title width reservation, native layout refresh.
- [x] Extend `platform/ios/tools/check-bootstrap.py` with integration and artwork parity assertions, and document the hook in `patches/ios/HOOKS.md`.
- [ ] Run `python platform/ios/tools/generate-overlay.py`, `python platform/ios/tools/check-bootstrap.py`, and `git diff --check`. Run `swift test --package-path platform/ios/NebulaSettingsContract` and native syntax/SDK checks on macOS CI (no local Swift toolchain).
- [ ] Commit only scoped tracked files, push, dispatch `gh workflow run ios-ipa.yml --ref main`. Verify Swift CI and report the actual IPA run state. An in-progress build is not an available IPA, and source checks do not establish device rendering quality.
