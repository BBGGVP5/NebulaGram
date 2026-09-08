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


## 2026-09-08: album captions and nested reactions/readers

The album-caption reproduction is separate from text-block culling fixed in
0067. The selected caption cell can still be attached while upper album cells
have been recycled. ChatActivity's scrim iterates only attached recycler
children, so lifting the group background alone cannot bring those photos back.
Patch 0068 reserves extra layout space for the album before measuring the popup,
posts creation after layout, and keeps the cells attached until dismissal.
Native/non-blurred menus and document groups do not take this path. Cancellation,
rebound/detached cells, failed menu creation and fragment destruction clear it.
Image receivers still obey native download, viewer visibility and spoiler rules.

The same patch removes PopupSwipeBackLayout's default opaque foreground rectangle
for Nebula menus. The popup host remains the single material; old rows fade out
under the incoming page rather than showing through it. Explicit foreground
colours retain native behavior.

The lifted cell also avoids recycler-relative alpha-layer clipping, updates media
frames within the preview scope, and restores local drawing flags afterwards.
Popup placement stays below the status bar. The splash uses an explicit rounded
vector contour instead of a thick stroked dart; the two brand trails are retained.

Validation: `scripts/check-preview-surfaces.py` executes extracted production
methods for preview state restoration, album preparation/cancellation and page
material policy. All 22 workflow regression commands passed locally. These are
not substitutes for runtime photo loading, swipe-back animation, or system splash
acceptance on Android; ADB was disconnected during this change.


Device acceptance still required:
- Loaded single photo and 2/4/10-item albums, opened through a long caption after
  scrolling the upper media fully off screen; close/reopen and compare pictures.
- Mixed video/photo album, native media-spoiler state, and media not yet downloaded.
- Open reactions/readers, swipe back partway and release; no brown page fill,
  no overlapping old labels, and normal scrolling after dismissal.
- Background the app while an album popup is being prepared, then return; no
  delayed popup or permanently expanded recycler reservation.
- System launch in light/dark mode, retaining the same rounded brand mark.

Final Java 21 compilation passed (7m19s); javap confirmed the latest album/pause
cleanup, receiver restoration and nested-page policy. AAPT2 compiled the splash
resource; a local SVG rendering was visually inspected. No new APK was installed.

## Discussion profiles, input panels and Rich previews (2026-09-08, patch 0069)

- Discussion-group profiles reuse `ProfileChannelCell` for the linked broadcast
  channel, above the bio. The cell and fetcher use the fragment's account. A zero
  message id requests channel history, not `getMessages([0])`; responses are sorted,
  empty history terminates loading, and cached content remains available on errors.
  Topics and inaccessible private channels do not get a linked-channel card.
- The floating chat header restores the menu containing the search EditText when
  search opens. Search result counts/indexing are unchanged. The search island and
  emoji panel sample Telegram's wallpaper backdrop instead of the empty area under
  the list. Profile overflow uses the live profile blur factory and Nebula material.
- Native attachment sheet bodies share a backdrop captured from the originating
  window, with separate drawables for multiple passes. Same-window embedding,
  unsupported Android versions, disabled blur and custom web-app backgrounds keep
  the native fallback. The location wrapper no longer paints over a valid backdrop;
  map tiles and content are not modified. Profile action borders preserve native
  avatar blur, and obey the highlight setting.
- Emoji/GIF/sticker type tabs support horizontal scrubbing with a moving lens.
  Compact native attachment strips use the same release-only gesture and existing
  permission-checking callbacks. Long attachment strips remain horizontally
  scrollable; bot launches are deliberately excluded from scrub targets.
- Rich previews skip recycler-viewport clipping only inside the lifted cell's draw
  scope. Rich rendering itself, media and collapsed details remain native. Preview
  isolation is also used when the source top happens to be zero.
- Releasing a folder lens on the same folder is now a no-op. Programmatic folder
  switches settle the previous destination first, and disabled/settling tabs cannot
  start another scrub. Progress callbacks without a visible destination cannot
  move the primary page offscreen; completed transitions explicitly normalize it.

Validation: all 24 workflow checks passed, including production-method Java tests
for Rich/list clipping, search visibility, account routing, gesture cancellation,
and 60 interrupted folder cycles. Reintroducing the old same-folder transition or
Rich clipping causes the new tests to fail. A fresh reconstruction from vendor HEAD
and all applicable patches matches all 13 changed native files.

Device acceptance remains required (ADB has no connected device): Rich messages
with tables/media/collapsed details; repeated same-folder scrubs and rapid reversals;
search opening/closing with the keyboard; channel cards on multiple accounts;
emoji type switching; photo/file/audio/contact/location sheets and custom web apps;
light/dark themes, reduced motion and disabled blur. No APK has been installed for
this change, and these source tests are not a claim of runtime visual acceptance.

Final patch-0069 Java compilation passed (2m40s). Bytecode checks confirmed Rich
preview isolation, folder settlement, both emoji-tab setup paths, channel history,
profile highlight preference and separate-window sheet capture.
