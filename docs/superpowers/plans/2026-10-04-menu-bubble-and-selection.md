# Android bubble menus and selection materials

Implementation proceeds in this chat under the user's existing authorization.

**Goal:** Match the reference's moving, expanding bubble on Android, preserve the initiating corner, repair selection action materials, and unblock the requested iOS rebuild.

**Approach:** Independently implement the observed surface/center/content motion after studying FlClash's popup renderer. Keep window placement and native controls intact. Use separate cached drawables for each selection action so deferred GPU rendering cannot reuse mutable geometry. Move the iOS icon inset to its UIButton owner.

1. Record the reference's independent size, center and content transitions. Implement a pure Java frame calculator and integrate it into popup drawing, clipping, focus and inverse touch mapping. Verify interruptions, corner origins, final geometry, reduced motion and cached effects.
2. Cache one selection material per native action and retain the existing hit-test union. Add a deferred-draw regression that detects shared mutable render nodes and checks allocation reuse.
3. Remove the UIButton-only API from the native UIView integration hook; apply the inset in the actual UIButton owner. Add an early real UIKit typecheck for the native layout block.
4. Reconstruct and validate both pinned upstream patch series, run the affected UI checks, publish only these changes, start Android and iOS builds, and report their actual status. A local geometry preview does not replace device verification.

Reference: https://github.com/chen08209/FlClash/blob/main/lib/widgets/popup.dart
