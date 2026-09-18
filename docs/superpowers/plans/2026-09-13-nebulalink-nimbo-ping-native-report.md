# NebulaLink Nimbo Ping backend report — 2026-09-13

## Scope and integration
Implemented only core/, runtime/xray/, and these newly owned backend plan/report files. No Nimbo files, platform UI, native artifacts, dependencies, commits, publishing, or workflows changed by this worker. Pre-existing dirty patch files, backdrop, design and docs remain present; UI worker concurrently owns platform changes.

The existing mobile/C ABI remains unchanged: synchronous JSON Call plus the existing event sink. New JSON method `probe.cancel` and additive server metadata require no new native export. Main/UI worker rebuilds mobile bindings and verifies actual Android/iOS application integration; no Apple archive was run by this worker.

## Final JSON contract
- Default absent/invalid ping preference is `nimbo`; it is first above TCP in shared menu, title key `nl_ping_nimbo`. Existing `tcp`, `http`, `url` IDs remain valid.
- `probe.servers`: `{ids:[...], timeout:seconds, request_id?:string, method?:string}`. Empty IDs means all stored servers, not just a visible page; UI must send explicit visible IDs. Timeout 0 defaults to 5 seconds, maximum 60. Nimbo uses snapshotted `settings.ping_url` or the existing default target. The response remains `{server_id: raw_ms}`; -1 is failure, 0 is valid success. No /3.3 arithmetic exists in backend.
- `servers.list`: `latency_ms` is always serialized, including 0. Existing `checked_at` > 0 marks a completed measurement (Unix seconds). New optional `latency_method` is actual method provenance; absent/empty means legacy unknown and MUST NOT be inferred from current settings. `nimbo` alone permits UI-only approximate presentation. A legacy URL server batch measures TCP and is accurately tagged `tcp`; separate active-only `probe.url` remains unchanged.
- Each completed result persists latency, method and checked_at atomically BEFORE its progress event. Subscription refresh/reload preserves all three; measured zero sorts ahead of unknown. Old APIs writing unknown-provenance latency clear stale method metadata.
- `probe.progress` event data: `{request_id,latency_method,completed,total,done,cancelled,id?,latency_ms?,checked_at?}`. Result fields are present on result events. Method/target are snapshotted once, so changing settings during a batch cannot relabel old results. UI ignores superseded request IDs, cancels the old batch, and queues a replacement.
- `probe.cancel {request_id}` returns `{request_id,cancelled}`. IDs must be nonempty and at most 128 chars for cancellation; callers should mint a fresh unique ID per batch. A bounded 128-ID / one-hour history makes pre-start cancellation safe and prevents immediate ID reuse. Wrong IDs never cancel another active batch.
- One batch per Core (overlap returns an error); runtime probes queue globally, never parallel independent cores. The synchronous probe runs on a probe queue; cancellation/control must run on another queue. Completed subset is returned on cancellation; active canceled/unvisited nodes retain their prior cache. Events use the existing sink outside the Core mutex. Initialization remains required before calls.

## Runtime invariants and fidelity
- With an active core, CreateObject builds a private handler against its context. It is NEVER added to the live outbound manager: no default-route replacement, tag-cache mutation, balancer visibility, selected-server change or active lifecycle call. Each GET is explicitly dispatched into that handler using bounded Xray pipes; the target is never directly dialed by Go net/http.
- One global coordinator serializes probe lifetimes and Start/Stop transitions. Disconnected probes may create exactly one listenerless temporary core. Connector transitions cancel the lease and wait for transport cleanup before constructing/closing the managed core. While connected no second core is created.
- Pinned upstream mux ClientManager has no Close implementation and creates background carriers. Diagnostics therefore retain the configured mux wire protocol using an owned ClientWorker and a cancellable carrier, joining I/O before lease release. This is not silent mux disabling. Target-strategy resolution is applied before mux framing; the virtual mux carrier name is not resolved. UDP/XUDP is not exercised by TCP HTTP(S) diagnostics.
- Shared root config is NOT loaded: no user Env, root DNS, inbounds/TUN or root routing change. Choose explicit ConfigTag, or the sole real proxy outbound after ignoring pseudo/direct outbounds. BuildOutbound is used for share-link models. Native sendThrough bind, stream/security/Reality/mux settings are retained; only the private tag is substituted. Ambiguous multiple proxies require ConfigTag.
- Fail closed for direct-only, unsupported engines/protocols, explicit proxy chains/dialerProxy, external certificate/key-file dependencies or secret-log file outputs. No fallback through active/default/DIRECT. Supported proxy protocols: VLESS, VMess, Trojan, Shadowsocks, SOCKS and HTTP. Root balancer/routing policies are intentionally not recreated for a per-selected-outbound measurement.
- GET only, response headers timed around client.Do, before body/handler/core cleanup. Overall deadline includes setup and queue wait. 2xx only, no redirects, normal platform TLS trust for target HTTPS; userinfo/fragment/non-HTTP URLs are rejected. API/runtime failures are fixed diagnostic codes rather than raw config/URL/credential text. No process logger is replaced; existing user-configured Xray diagnostics/logging retain their usual behavior.
- **Measurement path limitation:** no platform physical-interface binding or VPN exemption is introduced. OS routing, active VPN and the existing core's DNS/system-dialer environment can influence proxy-server transport and latency. This is a per-node proxied GET, NOT a claim of physical-direct independence from another VPN. Target GET routing nevertheless always uses the selected private handler, never active-node fanout.

## Validation
- Windows Go1.27.0: `go test ./core/... ./runtime/xray/... ./bind/... -timeout 60s` PASS.
- Windows: `go vet ./core/... ./runtime/xray/... ./bind/...` PASS.
- Linux Go1.27.1: `bash runtime/xray/test-race.sh` runs uncached `go test -race -count=1 ./core/... ./runtime/xray/... ./bind/... -timeout 120s`. Final uncached rerun PASS (runtime/xray 4.388s, core/api 1.095s, bind/mobile 1.097s), including real VLESS/mux and mux-cancel cleanup.
- The optional Linux test bootstrap verifies official Go1.27.1 archive SHA256 `63d339f0da5ab53635a56f2490a7984dfe12dfcff22ad749f63edaf590168445`; no repository Go version/dependency was upgraded. Existing pinned Xray `v1.260327.1-0.20260908222543-52a412d9e2f5` unchanged.
- Real pinned runtime: two distinct CONNECT proxy fixtures; each receives its own GET, direct-target trap remains 0; default handler/list/stats unchanged with concurrent balancer selects and stats reads.
- Real pinned VLESS: TWO separate remote Xray test processes (never a second remote fixture core in the app process), distinct endpoint counters, GET through each with mux disabled/enabled. Mux cancellation completes cleanup before managed Start/Stop.
- Actual active local HTTP proxy requests succeed before, during and after a suspended/canceled diagnostic, with same managed instance; no active traffic sent through probe handler.
- TLS self-signed target rejected through selected CONNECT proxy; 302 and 503 fail; redirect never followed; direct-only and credential URLs rejected. Connector preemption, canceled lease cleanup, API wrong-ID/pre-start cancellation, partial-cache preservation, zero presence, raw330 unchanged, settings-switch provenance and reload/refresh sorting covered.
- Owned `git diff --check` PASS; module/workspace manifests/sums unchanged. Device-specific Android protected-network routing and real iOS app builds are not claimed by these host tests.

## Frozen backend file SHA256

- `core/api/api.go`: `b8b6da91984959f50df7a821195b880c2d5092ade188fefee24ecdc4f55f7f2e`
- `core/api/probe.go`: `b5b04446db2336b6cd02dc677a23e0141264aeb193604c6dc7746e9781c29ed2`
- `core/api/probe_test.go`: `909b820f5316aca4d00cc3abc51ebb8975e6a3b2371da696c918399941ce3b79`
- `core/model/model.go`: `2c32435aebbcc840d701e05bd9ad71f7c18dfc0a29d199dd447cf58066907b45`
- `core/probe/nimbo.go`: `2042e44eec8b44147b2b0997bddea324cd662dde6d54d5ab7205fadd926df3cd`
- `core/settings/settings.go`: `f48c44fbd399481235cc2a2c5d5fa8c39df8ad48b0cce5a90748bce97675656e`
- `core/settings/menu.go`: `84acb6801c1a4c2105c17e03119ec8c500a15dfa6a72b451e3f238236c735539`
- `core/settings/nimbo_test.go`: `e9fa4b4b0ecf46ef59a42a441fdc805783fa93d2785c50c95cc3a0abd7121698`
- `core/store/store.go`: `dd054766a880bb1405c933c4fea07164be921e1535331d4f050a83c2215fb201`
- `core/store/latency_test.go`: `669b1887cc0b4978ff65c59ab418049dbad75e7d321bfa6cdc2ff65346922a4c`
- `runtime/xray/xray.go`: `3a504bb7cc73def906082995cd83c7074320df7153e371783ca3e024424e4384`
- `runtime/xray/probe.go`: `e57e8e17cd8f0f1356bbe75d2f0a35c9bacfae480c4495ea3517c76ca8a3bf59`
- `runtime/xray/probe_config.go`: `e440c86899883e5a206b32ab306ef5dd18c0ac71bea61dff39d076d35435a6c8`
- `runtime/xray/probe_mux.go`: `907f594dbf80ab6df8052d1bb6e0e029101065b502c98b60bede86ffa8863e80`
- `runtime/xray/probe_test.go`: `e35882b9aa1f1b0e6a6ace2180b63ebb815b0a511b77ccd7629b14cf5dff2853`
- `runtime/xray/test-race.sh`: `3a094fb08ff61bf741befd6ff11c3aad3d432f9cbf8c9973cfb65b5326f65618`
