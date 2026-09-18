# NebulaLink Nimbo Ping backend plan

**Goal:** make per-server Nimbo GET the first/default ping method for unset preferences, while preserving existing IDs and Telegram's active connection.

**Architecture:** register an optional per-server prober from runtime/xray into the dependency-free core. While connected, construct uniquely tagged PRIVATE outbound handlers from the current core context, never register them in the outbound manager, and dispatch each request directly through its own handler. This avoids live balancer selection and the pinned manager tag-cache Add/Remove race. While disconnected, a runtime coordinator may own one temporary core; connector start cancels/waits for cleanup before replacing it. Never run an independent second core beside Telegram's active Xray.

**Scope:** core/, runtime/xray/, bind/ plus this new plan and backend report. Preserve all pre-existing dirty patches, platform files and design/docs artifacts. Main owns platform overlays/localization and integration/build decisions. No Nimbo edits, commits, dependency upgrades, pushes, CI dispatch, releases or automations.

- [x] Inspect runtime lifecycle and pinned outbound-manager APIs; report an architectural blocker before implementation if safe handler ownership cannot be established.
- [x] Add PingNimbo `nimbo` before TCP in core settings/menu; default only absent/invalid preferences; keep tcp/http/url IDs.
- [x] Implement route projection from raw server configurations or BuildOutbound with explicit selection, preserving Reality/transport/security and rejecting direct/ambiguous/unsafe chains.
- [x] Implement runtime coordinator, uniquely tagged handlers, private handler Dispatch over bounded pipes, GET/2xx/no redirect/default target TLS, per-request deadline and cancellation/cleanup. Keep active lifecycle/default routes/stats intact.
- [x] Extend probe.servers without changing its id-to-ms result; add bounded cancellation/progress API if needed and send main exact contract before UI work.
- [x] Test real pinned Xray with two distinct proxy servers and a direct-trap target, while connected and disconnected, plus TLS/status/redirect/cancellation/lifecycle handoff and Go race tests.
- [x] Run core/runtime/bind checks, inspect owned diff and unchanged unrelated paths, and save backend report with changed paths, ABI, limits and validation evidence.

Implementation refinement: upstream mux ClientManager is not Closable and its factory uses a background context. The diagnostic builds an owned mux ClientWorker/carrier with request cancellation and a join before lease release. Original mux wire behavior remains tested with real remote Xray processes. Existing checked_at is the measured-presence marker; latency_method records actual snapshotted provenance. Backend values are always raw GET milliseconds.
