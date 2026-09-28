# iOS NebulaLink Parity Implementation Plan

> **For agentic workers:** Execute these tasks inline in this session. The user already authorized the port; no additional execution choice is needed.

**Goal:** Give iOS the Android server actions and saved-subscription management while retaining native UIKit navigation and safe credential handling.

**Architecture:** Keep the existing `NebulaLinkService` as the only core bridge. Add a focused UIKit source-management controller, and keep server selection/actions in `NebulaLinkController`. The core already returns `description` in `servers.list`, including query-style `serverDescription`; show it without exposing a subscription URL or raw core errors.

**Tech Stack:** Swift, UIKit, NebulaLink Go core, Bazel iOS modules, Python structural checks.

---

### Task 1: Native subscription management

**Files:**
- Create: `platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaSubscriptionsController.swift`
- Modify: `platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLinkController.swift`

- [x] Add a grouped table controller that calls `subscription.list` and renders `name`, `server_count`, and `updated_at`. Never render the raw `url` or `last_error` because they may contain credentials.
- [x] On a source tap, present a UIKit action sheet with `subscription.refresh` and a destructive `subscription.remove`. Confirm removal with an alert, call the core with `["id": id]`, then reload the list and notify the parent to reload servers.
- [x] Add a “Manage subscriptions” navigation row to the main NebulaLink screen. Push the new controller; keep the import field and selected server unchanged.
- [ ] Run ordered iOS patch validation and the native integration build.

### Task 2: Server actions next to the server list

**Files:**
- Modify: `platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLinkController.swift`
- Modify selectively: `scripts/check-nebulalink-layout.py` (preserve unrelated working-tree changes)

- [x] Put server ping, server sorting and `subscription.refreshAll` in one server-actions group. Read `server_sort` from `settings.get` and write `default` or `latency` with `settings.set`, then reload `servers.list`.
- [x] Keep the active-connection test as a fourth, clearly labeled action row. The check and refresh must give visible progress and a fresh server list on completion.
- [x] Update only the structural assertions for the action-row mapping; keep unrelated test edits out of the commit.
- [ ] Run the structural check, ordered patch validation and native compilation.

### Task 3: Delivery and verification

**Files:**
- Modify: `platform/ios/PARITY.md` only if the verified inventory changes (preserve unrelated working-tree edits).

- [ ] Run `go test ./core/...` and the iOS bootstrap/native CI gates.
- [ ] Review the targeted diff, commit only this plan and the NebulaLink implementation, push, and verify the resulting build logs.
- [ ] Report source integration, build status and remaining physical-device checks separately.
