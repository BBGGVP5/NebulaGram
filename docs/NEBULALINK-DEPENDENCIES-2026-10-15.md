# NebulaLink dependency refresh — 15 October 2026

NebulaLink embeds Xray-core directly in its own Go mobile binding. It does not depend on the separate [libXray wrapper](https://github.com/XTLS/libXray). Replacing our binding with another framework would not be an upgrade of an installed component.

| Component | Before | Selected |
|---|---|---|
| Xray-core | 26.9.30, `b26a91de4f32` | [26.10.10](https://github.com/XTLS/Xray-core/releases/tag/v26.10.10), `701af60772cd` |
| Go for runtime/mobile workspace | 1.27 | 1.27.2 |
| golang.org/x/mobile | 2026-08-21, `4776eadac327` | 2026-10-11, `197770444867` |
| REALITY | 2026-09-08, `8cdf7bf9c7f0` | 2026-09-21, `3c98159dee38` |
| x/crypto / x/net / x/sys | 0.57 / 0.59 / 0.48 | 0.58 / 0.61 / 0.49 |
| x/text / x/sync / x/tools | 0.42 / 0.23 / 0.49 | 0.43 / 0.24 / 0.52 |

Other compatible transitive updates are pinned in `runtime/xray/go.mod`, `bind/go.mod` and their checksums (including compression, UPnP, DTLS, WireGuard and Go RPC metadata). The Xray release is marked **prerelease**, as was the previously used September release; it is not described as a stable release. `/releases/latest` still points to March and is insufficient to identify the newest published release.

## Compatibility exceptions actually reproduced

- `gvisor.dev/gvisor@v0.0.0-20261015055921-d4d3e28027b4`: Go package loading fails because `pkg/tcpip/stack` contains incompatible `stack` and `bridge` package files. Retained the Xray-compatible `89a5d21be8f0` pin. No patch was applied to the global module cache.
- `github.com/xtls/reality@83f621788ca8`: changes private-key API types to `*ecdh.PrivateKey` / `*mldsa.PrivateKey`, incompatible with the selected Xray release's byte slices. Selected the newer compatible September 21 revision, also used by the inspected libXray manifest.
- Xray's maintained apernet/quic-go fork stays at its required pseudo-version; upstream release numbers from a different module path are not substituted.

## Verification

- `go test ./...` and `go vet ./...` pass for `core`, `runtime/xray` and `bind` after the selected dependency updates.
- `go mod verify` passes for the runtime graph.
- GitHub Actions run [38704456335](https://github.com/BBGGVP5/NebulaGram/actions/runs/38704456335) passed race tests for all three modules and built Android AAR, Apple XCFramework and Windows/Linux/macOS shared libraries. Local Windows race tests remain unavailable without a host C compiler.
- Full mobile build acceptance and actual VPN connection/reconnection on a device remain separate checks. A successful configuration test is not evidence of a live server connection.

This refresh covers the NebulaLink Go graph. It does not claim that every Telegram-owned native submodule or Android/iOS UI library has been upgraded; those need independent API/ABI checks against the pinned Telegram sources.


## Android client libraries — separate upgrade group

Patch `0214-client-library-refresh.patch` selects the following published stable versions from Google Maven / Maven Central. The AAR metadata of the AndroidX updates was checked against compileSdk 36 / AGP 8.13.2. Full app compilation and device acceptance are still required.

| Dependency | Before | Selected |
|---|---|---|
| `androidx.camera:camera-camera2` | 1.4.2 | 1.6.2 |
| `androidx.camera:camera-lifecycle` | 1.4.2 | 1.6.2 |
| `androidx.exifinterface:exifinterface` | 1.3.6 | 1.4.2 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 | 1.1.0 |
| `androidx.webkit:webkit` | 1.14.0 | 1.15.0 |
| `androidx.mediarouter:mediarouter` | 1.7.0 | 1.8.1 |
| `androidx.car.app:app` | 1.4.0 | 1.7.0 |
| `androidx.car.app:app-projected` | 1.4.0 | 1.7.0 |
| `com.google.android.gms:play-services-location` | 21.0.1 | 21.4.0 |
| `com.google.code.gson:gson` | 2.11.0 | 2.14.0 |
| `com.google.guava:guava` | 31.1-android | 33.7.2-android |
| `com.google.android.play:integrity` | 1.3.0 | 1.6.0 |
| `com.google.android.gms:play-services-safetynet` | 18.0.1 | 18.1.0 |
| `androidx.credentials:credentials` | 1.6.0-alpha04 | 1.6.0 |
| `androidx.credentials:credentials-play-services-auth` | 1.6.0-alpha04 | 1.6.0 |
| `com.google.android.recaptcha:recaptcha` | 18.7.1 | 18.8.0 |

ML Kit language-id 17.0.6, translate 17.0.3, genai-prompt beta4, speech-recognition alpha1 and coroutines 1.11.0 already match the latest published artifacts. Their version numbers are not changed for appearance.

The inventory is `ANDROID-DEPENDENCIES-2026-10-15.json`, including source URLs and unchanged dependencies. Remaining major migrations include AGP 9.4.1 / Kotlin 2.4.21, AndroidX core 1.19.1 (requires compileSdk 37 and AGP >=9.1), Billing 9, Firebase, Google service APIs and Stripe 23. The Telegram application modules still use legacy `applicationVariants` / `variantFilter` DSL; these migrations require adapting code and checking their runtime paths, not suppressing metadata requirements. Native media/crypto submodules remain at the Telegram pins. No claim is made that all technologies have been updated.

Android run 38706584331 found that reCAPTCHA 18.9.x requires minSdk 24. The published AAR manifests confirm all 18.9 releases require 24 and 18.8.0 supports 23; selected 18.8.0 to preserve existing device support. No manifest override is used. The same full compilation found an out-of-scope context variable in the toolbar hook; it now uses the view’s getContext(), covered by a native-callsite SDK compilation check.


## Downloaded core artifact receipt

Downloaded `nebulalink.aar` from run 38704456335, SHA-256 `d09668ed29dfdafcef37d446f88115e5d2b2677c1f13020d9f6cea8009057533`. Parsed the real ELF program headers: all four ABIs (armv7, arm64, x86 and x86_64) have 16,384-byte PT_LOAD alignment and matching file/virtual-address page offsets. This verifies the library alignment, not APK ZIP alignment or a device connection.

The repeated core workflow on main, run 38708490245, also passed after the test-bootstrap Go pin update. Latest Android application rebuild: 38709488599 (source c60b2e3); latest full iOS build: 38708491103 (source 1e20168; later changes affect Android only).

Android run 38709488599 exposed two further integration failures: WebKit 1.17.1 requires API 24, and Guava no longer supplies javax.annotation.Nullable transitively. Audited the published manifests of all selected AARs; WebKit 1.15.0 is the newest stable version supporting API 23 (1.16/1.17 require 24). Patch 0215 migrates the three upstream Nullable imports to the already-declared AndroidX annotations. This preserves nullability without adding an unused legacy JSR-305 dependency. Full rebuild remains required.

## Build feedback improvements

The Android workflow now runs AAR metadata, manifest merging and Java compilation before native assembly. Transport retries recognize Maven 429/502/503/504 responses, while manifest, Java compiler and checksum failures stop immediately even if an earlier log entry mentions a network outage. Eight executable retry scenarios pass. Onboarding tests run production methods with delayed callbacks instead of merely comparing the request-ownership predicate to itself.
