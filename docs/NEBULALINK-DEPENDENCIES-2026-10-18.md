# NebulaLink and Telegram source verification — 18 October 2026

NebulaLink embeds Xray-core through its own mobile bindings. Both `runtime/xray/go.mod` and `bind/go.mod` now select `v1.260327.1-0.20261017152356-92ccc939aaf4`, replacing the October 10 revision `701af60772cd`.

- [Xray 26.10.17](https://github.com/XTLS/Xray-core/releases/tag/v26.10.17) resolves to `92ccc939aaf4f8965f369cf0b142e005aeaa11d5`. The release is marked **prerelease**, as was the previous version.
- [libXray manifest](https://github.com/XTLS/libXray/blob/75ccb623c807a1113a064278e3e544b0049c16a5/go.mod) uses the same Xray revision. libXray's wrapper is not installed in NebulaLink and was not substituted for our bindings.
- REALITY advances from `3c98159dee38` to `83f621788ca8`. The new Xray revision supports this API; the compatibility exception documented for 26.10.10 no longer applies. Module tidy removes unused direct indirect pins and their obsolete checksums. Go remains 1.27.2.

Official Telegram production source heads were queried again, rather than inferred from application store version numbers:

| Platform | Official source | Current NebulaGram pin |
|---|---|---|
| Android | [13.0.2 (7175)](https://github.com/DrKLO/Telegram/commit/f32ffeec763f0bdbf4bec25b52d654f77bff733d) | Same commit |
| iOS | [13.0](https://github.com/TelegramMessenger/Telegram-iOS/commit/f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838) | Same commit |

No newer official production source was published at this check. Both gitlinks remain unchanged; the user's dirty Android vendor checkout is preserved.

## Validation

- Local `go test ./...` and `go vet ./...` passed for core, runtime/xray and bind. Runtime/mobile tests exercise local VLESS transport and cancellation/lifecycle behavior; they do not establish connectivity to a user's remote VPN service.
- Android patch 0238 separates composer clearing from text-flight eligibility. With any remaining formatting-panel height, native animated list insertion is retained and the text-flight overlay is omitted. Voice and unobstructed text transitions keep their existing conditions.
- Extracted native tests cover 128 origin states and 64 insertion combinations, including repeated callbacks. Motion, reply geometry, responsiveness and 442 draft-recording checks passed. The complete ordered native patch stack applied to the pinned source in the draft fixture.
- Full Android, iOS and core CI builds are the remaining artifact checks. USB device acceptance and measured input-to-bubble latency are not claimed.
