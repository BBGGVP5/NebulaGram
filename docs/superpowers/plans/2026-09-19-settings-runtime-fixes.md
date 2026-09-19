# Settings runtime fixes

**Goal:** Repair bottom folder placement/preview, replace emoji badges with owned vector artwork, hide service configuration from profile settings and fix abrupt avatar-ring transitions.
**Architecture:** Preserve the existing preference keys and Telegram runtime namespaces. Use an additional ordered upstream patch against the current 95-patch baseline plus focused Android overlays. Keep prior settings redesign changes intact.

- [x] Correct folder layout, top/bottom insets and live return from settings; cover both bar positions, hidden folders and system navigation insets.
- [x] Fix the native folder preview's dimensions, selection and updates.
- [x] Add five vector badges and a font-aligned drawable span; remove server/token controls from ordinary profile settings.
- [x] Make the avatar-ring alpha follow the search transition.
- [x] Media excluded: the user confirmed it has already been fixed. No media/storage changes.
- [x] Verify ordered patches, focused regression tests and Android Java compilation where available.

Validation: 96 patches apply to upstream; 264 folder/inset cases, 1001 transition
frames and 20 badge font/alpha cases pass. Existing settings-design, settings-root
and chat-layout checks pass. Android `:TMessagesProj:compileStandaloneJavaWithJavac`
passes with JDK 21. Browser preview checked for bottom placement, tab selection
and vector rendering in light/dark themes. No APK assembly or device testing.

The patch was rebased on commit `e21d435` to preserve the user's completed video
fix and the existing live folder preference listener.

## Build submission follow-up

The previously prepared changes were still local and absent from the APK built
from `7202208`. The folder preview now uses the shared 50dp tab height. Patch
0102 removes the renderer's extra vertical clipping inset, which overlapped the
padded tab content; horizontal viewport clipping remains. Existing previews are
retained, including the interactive bottom bar.

All 97 patches apply to the pinned Android source. Runtime checks pass 264
folder/inset cases, 24 native clipping cases, 1001 avatar fade frames and 20
badge font/opacity cases. The checks now run in Android CI. iOS bootstrap and
settings contracts pass locally. Full APK/IPA compilation is delegated to CI;
the later local full Java build attempt ran out of native JVM memory, as recorded
in the native-settings plan. No device verification is claimed.
