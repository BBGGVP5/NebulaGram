# iOS glass follow-up

The reported problems concern real Android menus and navigation as well as both editions of the motion film.

## Implementation

- [x] Relocate the hidden gallery camera to a bottom attachment button before bot/wallet entries. Use the existing camera permission and capture flow; do not add a duplicate while the tile is visible.
- [x] Add an AI enable switch. Require it, a stored credential, a selected model, and a valid selected provider endpoint before exposing the message action. Preserve the no-forward restriction and check again on invocation.
- [x] Switch the page while the navigation lens crosses tabs. Allow a new tap to retarget an unfinished transition and align page/lens timing.
- [x] Capture the originating window for overflow menus; keep wallpaper and content visible through the material. Enable the existing RenderNode refraction renderer for bitmap-backed context menus, including asynchronously delivered bitmaps.
- [x] Replace the separate ItemOptions window animation with the shared menu expansion. Use theme-based foreground and surface colours, including the settings logout menu.
- [x] Remove the reaction bar's 50 dp spacer for menus below messages. Round the gallery sheet's upper edge in the optional iOS style.
- [x] Refresh header foreground colours and use the intended surface on the first chat frame. Reserve and draw the unread count in Saved Messages.
- [x] Recompose video support text/controls, fix header insets and attachment lens height, open the menu from the avatar, add a consistent demo conversation, and animate messages from the bottom.
- [x] Export portrait and landscape v10 at 60 fps; move the music excerpt from 00:48.000 to 00:44.500.

## Validation

- AI availability: 53 combinations, no real credentials or network requests.
- Existing navigation, header/search, composer, icon, and message-menu geometry checks passed.
- All 55 Android patches apply to pristine upstream files and reproduce the edited native sources.
- Final Android Java compilation: see `build/ios-followup-0906/compile-surfaces.log`.
- Film: sampled every scene in both formats, checked sending/reply frames and all attachment tabs, then fully decoded both MP4 files. Duration 39.375 seconds; audio peak -0.6 dB.
- No ADB device was connected. Notification cold-start appearance, live blur/refraction, camera capture, and rapid touch gestures still require device verification; compilation and layout checks do not establish visual correctness on a phone.
