# Popup rendering: device regression notes

## Black labels with correct Java colors

Reproduced on Pixel 9 Pro XL, Android 17, release b1000112 / `20e904c`.
Both `TextView.getCurrentTextColor()` and the post-draw `TextPaint` color were
`#FFFFFFFF`; the on-screen labels and icons were black. A diagnostic green marker
was also darkened. A software layer on the row did not solve it.

The separate accessibility **forced color inversion / expanded dark theme** was
enabled (`accessibility_force_invert_color_enabled=1`). Ordinary display inversion
and high text contrast were off. The system's package exclusions included Telegram,
but not `app.nebulagram.messenger`. Temporarily excluding NebulaGram immediately
restored readable labels without changing its own dark theme. The user subsequently
approved retaining this per-app exclusion; all other exclusions were preserved.

This mode deliberately ignores developer Force Dark opt-outs, including the
view flag and theme configuration; see Android's
[ViewRootImpl.determineForceDarkType](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/ViewRootImpl.java).
Do not counter-invert our palette or silently change accessibility settings from
the app. On affected devices, ask the user to exclude NebulaGram from that system
mode. Existing JVM color tests cannot test the platform's final color transform.

## Content drag and translucent material

- Default blurred menu tint: 64% rather than the former 78% minimum. Explicit
  custom glass opacity and the opaque reduced-effects fallback remain supported.
- Content is mapped to the same expanded padded edges as the glass. Pointer
  events are transformed by the inverse matrix, using a recycled event copy.
- `check-menu-drag.py`: both pull directions, padding, empty bounds and inverse
  geometry. `check-menu-colors.py`: actual truncated 8-bit opacity, including
  black/white backdrops, light/dark material and semantic foreground colors.
- Device acceptance: home overflow, long-press folder/tab/account menus, own
  settings, drag/cancel/reopen and absence of diagnostic markers. Do not select
  destructive menu entries while testing.

Diagnostic tools and screenshots are local ignored build artifacts. Shipping
source must contain neither diagnostic markers nor diagnostic logging.

## Final verification, 2026-09-08

- `:TMessagesProj:compileStandaloneJavaWithJavac`: successful (Java 21).
- All 20 `check-*.py` checks listed in the Android workflow passed.
- All 61 patches applied to the pinned vendor tree; patch 0066 reproduces the
  popup touch mapping, folder timing, blur source and lifted-message clip changes.
- The connected Pixel runs a clean local test APK based on b1000112, installed
  in place with the original signing certificate. Resources, native libraries and
  unchanged DEX files were verified byte-identical to that release. This is not
  a fresh complete Gradle/native release build.
- A local DEX repack initially missed a shared R8 API outline and failed at startup.
  Restoring the original shared outlines fixed packaging; subsequent launch,
  navigation, settings preview and message-menu checks succeeded. This packaging
  issue does not belong in the application source patch.
- Home overflow: white labels/icons in the dark theme, distinct QR actions,
  no diagnostic markers. The approved system inversion exclusion remains set.
- Glass preview: active Telegram doodle wallpaper displayed; the customization
  toggle was temporarily enabled for inspection and restored to its original off
  state. No slider values were changed.
- Saved Messages: photo-plus-caption action menu opened with the photo visible
  above reactions/actions; labels readable. No action was selected or message sent.
- Folder bar: drag from first to third folder, release and tap back succeeded;
  intermediate screenshots show the outline following the dragged lens.

## Preview and motion implementation

`NebulaFolderMotion` uses elapsed frame time with vsync scheduling. Drag release
starts from the current lens position; native page progress is not animated a
second time. Both `ScrimOptions` blur-source overloads leave color correction to
the menu material instead of darkening its source twice.

`NebulaWallpaperPreview.drawWallpaper` shares center-crop logic while restoring
the cached drawable's bounds. The glass preview blurs a private downsampled
bitmap, caches it by wallpaper/size/radius and releases it on detach.

The lifted message's viewport clip is mapped back through its current transform
before intersecting the message bounds. `check-menu-state.py` exercises 256
forward/inverse cases including negative (offscreen) source positions.

Remaining coverage limits: the exact long-caption screenshot and grouped-media
edge cases have not all been reproduced on the device; no full-device frame-time
benchmark or exhaustive light/dark/OEM matrix was run. Pure geometry checks do
not establish those results. Native libraries were not rebuilt locally.

## Follow-up: blank long-text previews and media edit controls

The subsequent user screenshots still showed blank or heavily clipped previews.
The earlier photo-only acceptance did not cover the text-block culling cache.
Patch 0067 introduces a scoped `ChatMessageCell.drawMenuPreview`: render all text
blocks for the lifted cell without querying its old list-visible rectangle, then
restore `fullyDraw`, block indices and the pending visible-part flag in `finally`.
It does not change spoiler visibility or the underlying message.

All three clipping paths (cell, grouped background, and deferred caption/name/time
passes) now use the same inverse-mapped viewport. The old chat header/input bounds
relax during the lift; the outer preview clip still keeps content above the menu.
The menu's maximum share decreases from 56% to 48% and long previews can scale to
65%, rather than being clipped immediately at 90%. Extremely long posts still
use a bounded preview; this is not a full-message viewer.

The media edit row is separate from the reply row. Its margins now follow the
composer pill, and visible edit/replace actions share its measured width with
ellipsized labels. Disabling the glass composer restores native margins/widths;
paid-suggestion icon-only rows retain their explicit layout.

`check-message-preview.py` exercises the real scoped drawing method (including
exception restoration), the real clipping helper and edit-row measurement.
`check-chat-layout.py` also checks active/native edit-row margins. This new
regression fails on the pre-fix renderer. Physical-device acceptance of this
follow-up is pending: ADB had no connected device when the new work was tested.

Follow-up source validation: standalone Java compilation passed; all 21 workflow checks passed; 0067 applies cleanly after the 61-patch baseline. Compiled bytecode was checked for the new preview, clip and edit-row methods. No follow-up APK was installed while the phone was disconnected.
