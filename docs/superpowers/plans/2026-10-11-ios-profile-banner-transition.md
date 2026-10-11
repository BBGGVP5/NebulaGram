# iOS Profile Banner Transition Implementation Plan

> **For agentic workers:** Execute inline under writing-plans; optional execution plugins are unavailable. Do not delegate or reset either vendor checkout.

**Goal:** Port the Android banner fix to native iOS: a seamless photo/page edge and smooth, interruptible opening and closing.

**Architecture:** Keep Telegram's native image/gallery crossfade. Put the themed fade in the common header foreground, after the avatar clipping node and before controls/title, rather than inside the gallery that shrinks and hides. Follow the real header bottom including its buttons/music, protect the small avatar from the fade, and continue Core Animation transitions from their current presentation state. Ignore obsolete collapse completions after reopening.

**Tech Stack:** Swift/Foundation geometry policy, ASDisplayNode/CAGradientLayer, ContainedViewLayoutTransition, XCTest, extracted native-hook execution, full arm64 IPA CI.

---

### 1. Geometry and regression checks

Files:
- Create `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaProfileBannerPolicy.swift` and its generated Bazel mirror.
- Create `platform/ios/NebulaSettingsContract/Tests/NebulaSettingsContractTests/NebulaProfileBannerTests.swift`.
- Create `platform/ios/tools/check_profile_banner.py`; call it from `platform/ios/tools/check-bootstrap.py`.

- [x] Add bounded geometry and gating tests for fractional edges, 1x/2x/3x pixels, wide screens, collapsed-avatar protection, invalid values and opening/closing sequences.
- [x] Native structural regression must reject the current fade parent under `listContainerNode`. With `--swift`, execute the actual fade-update helper and native expansion/collapse callbacks using deterministic layer/queue shims; test theme caching, interrupted animations and weak lifetime. This executes production bodies, not a reimplementation.
- [x] Implement the policy:

```swift
public enum NebulaProfileBannerPolicy {
    public static func fadeFrame(width: CGFloat, bottom: CGFloat, protectedBottom: CGFloat, pixel: CGFloat) -> CGRect {
        guard width.isFinite, bottom.isFinite, protectedBottom.isFinite, pixel.isFinite,
              width > 0.0, bottom > 0.0 else { return .zero }
        let top = min(bottom, max(max(0.0, bottom - 148.0), protectedBottom))
        guard top < bottom else { return .zero }
        return CGRect(x: 0.0, y: top, width: width, height: bottom - top + max(0.0, min(1.0, pixel)))
    }
    public static func opacity(style: Bool, photoBanner: Bool, hasPhoto: Bool, editing: Bool) -> CGFloat {
        return style && photoBanner && hasPhoto && !editing ? 1.0 : 0.0
    }
}
```

### 2. Native adaptation

Files: create ordered `patches/ios/0099-profile-banner-transition.patch`, affecting `PeerInfoHeaderNode.swift` only. Derive it from a fresh pinned-source fixture with all 98 existing patches. No changes to photo resources, native hit targets or camera engines.

- [x] Move `nebulaBannerFade` to `regularContentNode`, immediately after the avatar clipping node, before native gallery controls and title nodes.
- [x] Replace the early expanded-size/boolean fade block with a helper called after `apparentBackgroundHeight` is resolved. Use:

```swift
self.nebulaUpdateBanner(width: width, bottom: apparentBackgroundHeight,
    protectedBottom: self.isAvatarExpanded ? 0.0 : apparentAvatarFrame.maxY,
    hasPhoto: peer?.profileImageRepresentations.isEmpty == false && self.forumTopicThreadId == nil,
    editing: state.isEditing, color: presentationData.theme.list.blocksBackgroundColor,
    transition: transition)
```

The helper uses the production policy above, caches the existing gradient colors with disabled implicit actions, updates node/layer frames with `beginWithCurrentState: true`, ends the gradient at `bottom` and paints one extra physical pixel below it. Fade visibility depends on settings/photo/editing, not the expanded boolean; empty geometry yields zero opacity. Reduced Motion uses `.immediate` for the decorative fade.

- [x] In the native collapse completion, require `finished && !self.isAvatarExpanded` and a matching `nebulaAvatarTransitionGeneration` before hiding the gallery or reattaching its video. Increment the generation on each accepted expansion change; capture it before scheduling the completion. The queued collapsing-state reset captures weakly and rechecks both expansion and generation so it cannot reset a later close. In `updateIsAvatarExpanded`, compute the setting-gated target before comparing/expanding/collapsing:

```swift
let expanded = isAvatarExpanded && NebulaSettingsStore.shared.profilePhotoBanner
if self.isAvatarExpanded != expanded {
    self.nebulaAvatarTransitionGeneration += 1
    self.isAvatarExpanded = expanded
    if expanded { self.avatarListNode.listContainerNode.selectFirstItem() }
    if case .animated = transition, !expanded { self.avatarListNode.animateAvatarCollapse(transition: transition) }
    self.updateAvatarMask(transition: transition)
}
```

- [ ] Check the complete ordered iOS patch series and generated contract mirrors locally, then run bootstrap XCTest/native-hook checks on macOS. Preserve the native gallery's image crossfade instead of adding a competing photo snapshot or a midpoint source switch.

### 3. Build and delivery

- [ ] Commit exact affected files, push the already-authorized main/working branch refs, run bootstrap and the full arm64 IPA build at the same runtime revision.
- [ ] Download and independently check successful IPA source manifest, bundle/build, arm64 executable, extensions and SHA-256. Record these facts in this plan and `platform/ios/PARITY.md`.
- [ ] Report the unsigned IPA and physical-iPhone acceptance limit without claiming device visual verification.

The prior Android plan audited that iOS lacks Android's hidden OverlaysView/midpoint source bugs. This explicit follow-up now requests the equivalent iOS behavior; the native adaptation also addresses iOS's shrinking fade parent and unguarded late collapse completion.

Local baseline regression rejects the old source with `fade shrinks/hides with the native gallery`. Local bootstrap passes all 99 patches on 160 pristine native paths at pin `6ad963e5b62d354da79040f388ae2b9132fb17b8`, including 46 generated contract mirrors and new layer/call-site guards. Swift execution and UIKit typechecking are not available on this Windows host and run in macOS bootstrap CI, followed by full IPA native compilation.

While porting, the user confirmed Android 1000492 still has a photo handoff gap. Follow-up Android revision `2fb2c4d` keeps the backdrop visible while native gallery alpha is zero and guards cancelled animator callbacks. Its 628 rendering, 48 order and 8 actual animator cases pass; the equivalent iOS foreground is independent of native gallery hiding and has generation-guarded completions.
