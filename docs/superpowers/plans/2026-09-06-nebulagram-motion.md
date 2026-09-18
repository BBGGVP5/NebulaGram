# NebulaGram motion film implementation plan

**Goal:** Deliver a 9:16, approximately 30–35 second motion advertisement and a bilingual welcome post.

**Architecture:** Recreate demo UI from the existing Android design with the original NebulaGram logo. Animate independent glass, header, navigation and chat layers on a deterministic timeline. Render MP4 with the authorized Monster / MusicbyAden excerpt beginning around 00:48; package credits and the editable source alongside the final video.

**Tech stack:** Local rendering, vector UI assets, FFmpeg H.264/AAC, HTML/JavaScript animation source.

- [x] Confirm portrait format and music start; locate the track's download and attribution terms.
- [x] Create `design/motion/film.html`: black studio background, original icon, typography and warm Telegram-style UI; layered camera motion and beat-aligned scene changes.
- [x] Build 0–3.75s logo intro; 3.75–7.5s Liquid Glass switch; 7.5–13.125s first-person header flyby; 13.125–18.75s navigation bubble; 18.75–24.375s theme/customization; 24.375–28.125s menu and attachment reveal; 28.125–33.75s product close and channel call to action.
- [x] Create a deterministic local renderer and export 1080×1920 MP4, targeting 60 fps where practical.
- [x] Inspect a contact sheet and representative motion segments; verify duration, frame dimensions, audio and final frame with FFmpeg.
- [x] Write `design/motion/welcome-post.md` in Russian and English with emoji, both channel links, and music attribution for publication with the video.
- [x] Deliver the actual video and source files. Demo interface is animated for the film and does not expose real messages or contact details. Do not publish the post to Telegram.
