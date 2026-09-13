# NebulaLink Nimbo Ping Mobile UI Implementation Plan

> **For agentic workers:** Execute the checked tasks below in this session. Backend core/runtime/bind work belongs to task `01a09ab8-9553-79c1-a5a3-ef6dbc43ed3d`; do not edit its files. No commit, push, release, signing or automation.

**Goal:** Expose per-server Nimbo Ping on Android and iOS with honest estimated latency, cancellable progress and responsive connection controls.

**Architecture:** Keep raw backend values unchanged. Pure platform formatters use each server's measurement provenance, never the current preference, to render only `nimbo` as `≈round(raw / 3.3)`. Android's existing probe queue stays separate; iOS dispatches probes off its control queue only after core initialization. Each screen owns a unique request ID and cancels it on close.

**Tech Stack:** Java/Android overlay, Swift/UIKit overlay, JSON gomobile bridge, Python source contracts and standalone JVM tests. Windows host has Java/Python; Mac/UIKit compilation is unavailable.

---

## Scope and existing state

Only `platform/android/overlay`, `platform/ios/overlay`, related UI tests and this plan may change. Preserve dirty `NebulaMenuBackdrop.java`, patches android 0075/0084 and iOS 0012, design directories and September 6 documents. No AGENTS.md found in repository or ancestor directories. Writing-plans skill read; the user's execute-now/no-commits instruction overrides its optional handoff/commit steps. No subagent tool is available, so independent reads are batched and backend coordination uses the existing task.

## Task 1: Provenance and formatting

Files: create Android `.../ui/NebulaLatency.java`, iOS `submodules/NebulaLinkUI/Sources/NebulaLatency.swift`; update Android `NebulaServersFragment.java`, `NebulaConnectFragment.java`, `NebulaConnectionCard.java`, `NebulaLinkShortcut.java`.

- [x] Confirm backend provenance and measured-zero names directly with backend worker and inspect final model/probe implementation.
- [x] Add pure `isMeasured(raw, marker)`, `displayMillis(raw, method)` and `format(raw, method, marker, units, unknown, failed)` functions. Negative is failure; zero without marker is unknown; marked zero is numeric. Only exact `nimbo` gets `≈` and division by 3.3. Example: 330 nimbo -> ≈100 ms; 330 tcp/http/url/missing -> 330 ms; measured nimbo zero -> ≈0 ms.
- [x] Use helper in every numeric server renderer and its success color threshold. Shortcut snapshots the requested method and sends that explicit method so result provenance cannot change mid-request.

## Task 2: Android menu and cancellable progress

Files: `.../nebulalink/NebulaLink.java`, `.../ui/NebulaServersFragment.java`, `.../ui/NebulaMenuFragment.java`, `res/values/strings_nebula_menu.xml`, `res/values-ru/strings_nebula_menu.xml`.

- [x] Add RU/EN `nl_ping_nimbo` and estimate explanation. Menu options remain schema-driven (backend owns nimbo first/default). Show explanation beside ping settings and probe action.
- [x] Forward only `probe.progress` data to UI-thread listeners; leave tunnel listeners and routing intact. Never print event JSON or credential-bearing errors.
- [x] Start `probe.servers` with `request_id`, timeout in seconds, and existing server IDs; cancel via `probe.cancel`. Filter events by owned ID. Show completed/total and update individual row badges without rebuilding hundreds of rows on each result.
- [x] Cancel on close/destroy, remove listeners, ignore late completions. Keep selection and sort control independent of probing; reload canonical server data after completion.

## Task 3: iOS action and queue integration

Files: `submodules/NebulaLinkUI/Sources/NebulaLinkController.swift`, `NebulaLinkService.swift`, `NebulaLatency.swift`.

- [x] Add `probeQueue`; initialize on existing control queue, then dispatch `probe.servers`/`probe.url` to probe queue. `probe.cancel`, stop, selection and status remain on control queue.
- [x] Register gomobile event sink with service lifetime, parse progress without logging, post notifications on main. Verify generated binding signature from available source/header before choosing Swift conformance.
- [x] Add first action Nimbo Ping for IDs on current visible page (`method: nimbo`, `timeout: 5`, unique request ID), with same row becoming Cancel and completed/total progress. Keep active-tunnel URL test semantics unscaled and separate from busy state.
- [x] Render per-server estimated latencies using provenance; preserve UIKit inset-grouped layout. On close, finish and disappearance cancel owned server request and ignore late callbacks. Page changes cancel old-page request.

## Task 4: Focused checks and freeze

Files: create `platform/android/overlay/tests/check_nimbo_ping_ui.py`, `NebulaLatencyTest.java`, `NebulaProbeQueueTest.java`, `compile_nimbo_overlay.py`, and `platform/ios/overlay/tests/NebulaLatencyTests.swift`. The Swift test is supplied for execution on Mac.

- [x] JVM tests cover rounding, zero/unknown/failure, legacy methods, large values and provenance. Exercise actual Android command queue with latches: initialization precedes probe; blocked probe cannot block control/cancel.
- [x] Python contracts verify every numeric renderer, localization parity, request ID filtering/lifecycle cancellation, visible-page IDs, iOS queue split and probe UI independence. Run `python platform/android/overlay/tests/check_nimbo_ping_ui.py` (temporary output under UI test directory).
- [x] Run `python scripts/check-java.py`, parse changed XML, run feasible compilation without modifying vendor or release outputs, inspect scoped diff and `git diff --check`.
- [x] Record checks, exact changed paths and Mac/device limitations below; send contract/result summary to backend task and freeze edits.

## Verification results

**Status: implemented, verified where feasible, frozen.** No commit, push, signing, release build, workflow or automation was performed.

- `python platform/android/overlay/tests/check_nimbo_ping_ui.py` — PASS. Real JVM formatter tests cover 330→≈100, rounding boundaries, positive timings that round to zero, measured zero vs unknown, failures, large values, unknown provenance and all legacy methods. Actual Android screen methods run with deterministic JSON/callback fixtures: empty-page guard, owned IDs, raw per-result update, untouched unvisited cache, incremental progress, selection while probing, cancel-once, completion/failure, unique requests and stale callbacks. Actual `NebulaCommandQueue` passes initialization/FIFO and blocked-probe versus select/stop/cancel checks. RU/EN XML and Android/iOS source contracts pass.
- `python platform/android/overlay/tests/compile_nimbo_overlay.py` — PASS for the final 7 Java classes, twice including the final cancellation-state change. Uses the existing `build/final-verify-0904/tree` compiled Telegram classes, Android 35 SDK and cached gomobile/dependencies. Temporary R declarations include new strings and validate resource names; this is Java compilation, not a fresh resource-linked APK. Only ordinary deprecation notes.
- `python scripts/check-java.py` — repository-wide check reports existing duplicate/import heuristics outside this change set. Applying the same checker to all 7 changed Java classes passes.
- `git diff --check` over owned paths, UTF-8 decoding and trailing-whitespace checks — PASS. Temporary compiler/test directories were removed by their scoped test harnesses.
- `gobind -lang=objc ./mobile` (read-only generated stdout) verified `@protocol NebulalinkEventSink`, same-named ObjC class, `onEvent:(NSString* _Nullable)json`, and `NebulalinkSetEventSink`. No binding sources changed.

**Confirmed backend contract:** `latency_method` identifies the measurement, absent means legacy unknown; `checked_at > 0` marks measured zero; negative latency is failure. `probe.progress` carries snapshot provenance and is emitted after raw result persistence. The shortcut sends its snapshotted new-request method explicitly; it never infers cached provenance. Backend owns nimbo-first/default settings. UI applies the estimate only for exact `nimbo`. No physical-interface/VPN-bypass claim is made.

**Limitations:** Mac/Swift/UIKit SDK and physical-device execution were unavailable. Swift formatter tests are supplied but not executed here; Swift event-sink conformance needs the normal Mac compilation. No APK/IPA, resource-link, signing or release validation was run. The existing active-tunnel `probe.url` has no backend request cancellation contract; it now leaves controls responsive and its late UI callback is discarded on close. Per-server batches cancel with their owned request ID, including queued/pre-start requests. Other concurrent workers' files and pre-existing dirty files were not edited.

## Exact changed paths

18 files belong to this task: the 17 implementation/test files below plus this plan:

`C:/Users/Danila/Desktop/NebulaGram/docs/superpowers/plans/2026-09-13-nebulalink-nimbo-ping-ui.md`

The hashes below freeze implementation/test contents; they exclude this report itself.

| Exact absolute path | SHA-256 |
| --- | --- |

| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/nebulalink/NebulaLink.java` | `1ee9612f1db6177987ad33a8d1aa7ddfac14c5a219995475b7f0e7200d8e36ee` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLatency.java` | `5456d61dfbc2842c8a14e61eb8219c6c880e7a3eef5170a795332864e52ac8b5` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaServersFragment.java` | `370230b678704043247e1d64ba4424d26cee791b098fc25a5026dd7abf050ce5` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaConnectFragment.java` | `de32fa0c66e5a3e81314ea388d7ec6bf191a6eaa896ad63947f377678f9428a6` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaConnectionCard.java` | `9cd78a851f515067accacf344a1a8626ea2a834a348d2f25e590e8d657ecba89` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLinkShortcut.java` | `401d507070b795d71dd262e24a8fa922de13b6f9981b9cb9c497aad6f9797806` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMenuFragment.java` | `8ee762127cd420884d75614d394025363a82c9531465d21d8f70441a5251dd67` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/res/values/strings_nebula_menu.xml` | `72a70c92e773e285bcfc10dca7e09fd448c3c952a8833b1ac7cbc6e1c8d975b4` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/TMessagesProj/src/main/res/values-ru/strings_nebula_menu.xml` | `6865cfd05d2f203f11508c4242f4449a3468780f774e1a9a568e499cd339aa78` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLinkController.swift` | `71273b0a472e987aef79163f38900fd4de33045932c807239364150dbd57b4a7` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLinkService.swift` | `be38d5d0054f85a231452fb7c03055b5b887b94fc0f61d3da8c715d2e45a511b` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLatency.swift` | `cd87d223c65a3b5facae8efcace0f5109778915b0ba7bb9bc7c83195169be72d` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/tests/NebulaLatencyTest.java` | `5981405a2de3e215b012423bba0c1244a4b7766e323cb8a0a745017b7cb13f70` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/tests/NebulaProbeQueueTest.java` | `7513b431e47e770e86df5f2096672912672fbf21eb5d82ad7b679d406cf079db` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/tests/check_nimbo_ping_ui.py` | `871092a71e24c3704125c004e8270d3e711122550602d8b427b139879e3de1ff` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/android/overlay/tests/compile_nimbo_overlay.py` | `0d94bfc3c2532875d0e884005e13c1ef0d9bc1a6690ec4aacce0ca0afe62771e` |
| `C:/Users/Danila/Desktop/NebulaGram/platform/ios/overlay/tests/NebulaLatencyTests.swift` | `7cc1887ce5711fde9a1d0866828f480871ae203e26c7e034da78330b075b7788` |
