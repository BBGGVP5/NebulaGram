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
- Local Windows race tests cannot run: no host C compiler is installed in PATH. The existing Linux CI executes `go test -race ./...` for all three modules and builds Android AAR, Apple XCFramework and desktop libraries.
- Full mobile build acceptance and actual VPN connection/reconnection on a device remain separate checks. A successful configuration test is not evidence of a live server connection.

This refresh covers the NebulaLink Go graph. It does not claim that every Telegram-owned native submodule or Android/iOS UI library has been upgraded; those need independent API/ABI checks against the pinned Telegram sources.
