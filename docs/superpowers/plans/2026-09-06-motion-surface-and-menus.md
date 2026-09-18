# Motion surface and menus implementation plan

**Goal:** Show travelling refractive waves, functional attachment tabs and a glass overflow menu in both video compositions.

**Architecture:** Keep the deterministic HTML timeline. Draw travelling surface refraction on a canvas below the title, keep the capsule stationary, and animate attachment content and an anchored popup. Extend the soundtrack and renderer together to 39.375 seconds.

**Tech Stack:** HTML/CSS, Canvas 2D, Playwright, FFmpeg.

- [x] In `design/motion/film.html`, replace the fly capsule transform with `transform: none` and call `drawGlassWaves(u)`. Use radial wave gradients to offset wallpaper samples and draw narrow travelling light/shadow crests; retain the top-down camera movement.
- [x] In the same file, add rounded glass attachment tabs and four distinct panels. Select the panels at `u = 0, 1.4, 2.6, 3.8`, with a short lens stretch between tabs.
- [x] Add a separate 2.8125-second overflow scene after the 5.625-second attachment scene, showing a three-dot button morphing into a rounded dark translucent menu from its top-right anchor.
- [x] In `design/motion/render.cjs`, set `duration=39.375`. Recut the existing licensed music with input seek `-ss 48`, duration 39.375, fade-out starting at 37.875.
- [x] Render stills in both compositions, inspect the waves and menu, then export both MP4s and decode them with FFmpeg. Update `design/motion/README.md` with the new files and timeline.

The separate Android UI defect request remains active and will be handled in the source overlay and patch series after these motion changes.
