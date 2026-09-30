# Xray 26.9.30 and regression update

User requested an updated embedded core and then withdrew provider/payment bookkeeping.

- [x] Remove every provider/payment model, API and screen added in b686b61. Keep the interface, Saved avatar, glass and playback fixes.
- [x] Verify official Xray v26.9.30 release at commit b26a91de4f3294e26a0ad0a970b81a386a41f789. Upstream labels it a prerelease.
- [x] Verify current XTLS/libXray uses the same core revision. NebulaLink embeds Xray-core through its own mobile bindings, so update the actual dependency used by both platforms.
- [x] Pin runtime/xray and bind to v1.260327.1-0.20260930074004-b26a91de4f32, update checksums and required transitive dependencies.
- [x] Run local core, runtime and binding tests/vet and full protocol registry tests.
- [ ] Build Android APK/AAR and iOS native modules/XCFramework against this revision.
- [ ] Publish verified source and report compilation/device validation separately.

Initial Android CI found an old structural guard expecting a single wide selection capsule. The intended controls now use separate close/action surfaces; the guard was updated alongside the executable 12,928-case geometry check.
