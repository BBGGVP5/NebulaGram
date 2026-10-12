# iOS story header layout implementation plan

> **For agentic workers:** Execute this plan inline, task by task. The optional superpowers execution skills are unavailable in this session; do not dispatch agents.

**Goal:** Keep the collapsed story avatars beside the iOS chat-list title without covering its text or toolbar buttons, using the two supplied iOS screens as placement references.

**Architecture:** Preserve Telegram's story views, gradients, hit targets and expansion animation. Replace the fixed title allowance with the actual collapsed stack, lock and status footprint, and derive safe bounds from the visible toolbar controls. Use an owned Swift layout helper in the existing StoryPeerListComponent overlay and an ordered native patch; leave vendor checkouts untouched.

**Tech Stack:** Swift, UIKit/ComponentFlow, ordered Git patches, Python bootstrap checks, macOS native IPA build.

### Task 1: Reproduce and specify the overlap

- [x] Prepare `build/ios-header-20261011` from the committed iOS pin and all 99 existing patches.
- [x] Add `platform/ios/tools/check_story_header.py` to reject the existing fixed `maxTitleX - minTitleX - 46.0` budget: three avatars use 52 pt before their ring overflow, spacing, lock and status.
- [x] Record the failing baseline check. Cover 0–3 avatars, 320–1024 pt widths, asymmetric controls, long titles, lock/status/activity and expanded/collapsed endpoints.

### Task 2: Correct native layout

**Files:**
- Create: `platform/ios/overlay/submodules/TelegramUI/Components/Stories/StoryPeerListComponent/Sources/NebulaStoryHeaderLayout.swift`
- Create: `patches/ios/0100-story-header-layout.patch`
- Modify: `platform/ios/tools/check-bootstrap.py`

- [x] Add production geometry with these values: avatar footprint 34 pt (24 pt photo plus native ring), stack step 14 pt, title gap 8 pt, lock 20 pt and status 26 pt. Bound the stack count to 0…3; derive the available text width from their sum. Clamp the centered group origin to the toolbar interval.
- [x] Use the existing account-story selection rule for both the title budget and the collapsed stack. Sort current subscriptions before measuring the title, so live subscription changes cannot use stale counts.
- [x] Pass `sideInset + max(44, buttonWidth) + 8` as the occupied control edge when a control exists. Preserve the normal side inset when it does not.
- [x] Keep the native 24 pt collapsed photo scale, 52 pt expanded photo, story actions and gradients. Guard the single-avatar interpolation against division by zero.
- [x] Apply the patch to the prepared baseline and run `python platform/ios/tools/check-bootstrap.py`. The macOS `--swift` path executes the production helper and actual extracted native geometry.

### Task 3: Build and validate

- [x] Commit only the exact patch/helper/check/document paths, then push the authorized main and working branch refs.
- [x] Require successful iOS bootstrap checks and a full `ios-ipa.yml` build at the runtime revision.
- [x] Download and verify the resulting original IPA, source revision, arm64 application and extension metadata. Record its build number and hash in `platform/ios/PARITY.md` and this plan.
- [x] Report the source fix, verified build and remaining physical iPhone appearance check honestly.

Local evidence: the existing baseline failed the fixed 46 pt assertion. The patched pinned tree passes the strict bootstrap with 100 patches / 160 native paths / 46 mirrored contract sources. The native ring values and single-avatar interpolation are extracted from production sources for the macOS execution fixture. No physical iPhone verification has occurred.

Final build evidence: runtime `80e2d1cbba151e45098444fc5a2b53817ac958cb`; bootstrap `38170188364` passed 157 XCTest cases and 5394 production geometry/native interpolation cases. Full device IPA `38170244650` / build 107 succeeded with 6041 native actions, including StoryPeerListComponent and ChatListHeaderComponent. The branch bootstrap duplicate was cancelled; the main bootstrap passed.

Verified artifact: `build/artifact-38170244650/NebulaGram-unsigned.ipa`, 88,019,222 bytes, SHA-256 `7c3cf4f31ffe745521e0c8f3b96b84c2a4dc3e3caca8ab063e70486edcbaf083`. Its source revision, 12.9.2/build107, app.nebulagram, arm64 device executable, one Notification Service Extension and Speech privacy purpose match the original CI manifest. The artifact is unsigned and physical iPhone appearance/gesture/push verification has not occurred. No additional runtime changes followed this build.
