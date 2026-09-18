# NebulaGram motion film

A 39.375-second product film in separately composed portrait (9:16) and landscape (16:9) editions. The interface is recreated as deterministic animated scenes, using the actual NebulaGram mark and the supplied interface references. It is not an app screen recording.

## Deliverables

- `../../build/motion/NebulaGram-portrait-v10.mp4` — 1080 × 1920 portrait, 60 fps, with music.
- `../../build/motion/NebulaGram-landscape-v10.mp4` — 1920 × 1080 landscape, 60 fps, with music; separate composition, not a crop.
- `welcome-post.md` — one bilingual Telegram post, including required music credits.
- `CREDITS.md` — music source, license and edit details.
- `film.html` — editable animation source. Open with `?play` to loop the silent visual preview.

The post is supplied as a draft; nothing has been published to Telegram.

## Timeline

| Time | Scene |
| --- | --- |
| 00:00–00:02.8125 | NebulaGram logo and introduction |
| 00:02.8125–00:05.625 | Reference-style Telegram header/composer transforms into glass in 250 ms; stationary avatars crossfade instead of travelling across the header |
| 00:05.625–00:09.375 | Direct top-down horizontal tracking shot, with no tilt or perspective distortion and travelling refractive waves through the glass; the capsule does not bob |
| 00:09.375–00:12.1875 | Faster navigation swipes, compressed glass bubble and tap rebound |
| 00:12.1875–00:17.8125 | Header/composer separation, typing, send animation, adaptive text-only status pill, restrained 28 px expansion for the short typing status and quick 160 ms collapse, restored input placeholder and bottom-aligned sending/reply with animated conversation movement |
| 00:17.8125–00:21.5625 | Three chat colours sharing the same separate back button, centred name/status capsule and separate avatar on the right |
| 00:21.5625–00:27.1875 | Attachment menu above the composer: glass tabs switch between gallery, files, location and contacts |
| 00:27.1875–00:30.000 | Avatar tap opens the glass overflow menu from the header |
| 00:30.000–00:34.6875 | NebulaLink power button is tapped at its centre, then connects |
| 00:34.6875–00:39.375 | Brand, features and news/releases links |

## Render

Requires Node.js, Playwright, Chrome and FFmpeg. The renderer starts its own isolated headless Chrome instance. It does not use an existing browser profile.

Set `NEBULA_NODE_MODULES` to a directory containing the Playwright package, and `NEBULA_FFMPEG` to the FFmpeg executable. The Chrome executable path is near the top of `render.cjs`.

The music file must be at `../../build/motion/music-edit.wav`, containing the 39.375-second excerpt starting at 00:44.5 of Monster by MusicbyAden. See `CREDITS.md` for the source and required attribution.

```powershell
node design/motion/render.cjs --stills
node design/motion/render.cjs --name=NebulaGram-portrait-v10.mp4
node design/motion/render.cjs --landscape --name=NebulaGram-landscape-v10.mp4
```

Timing is fixed by `window.render(seconds)`; each exported frame is sampled at its exact timeline position. `--stills` exports review images at key points.
