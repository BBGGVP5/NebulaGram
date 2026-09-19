# NebulaGram profile badge artwork

The selected artwork is integrated into Android profile names, profile settings and the badge grant selector.

- `supporter-modern.png`: flat NebulaGram logo for supporters.
- `mira-sparkles.png`: the user-supplied Mira artwork, one large sparkle and two small sparkles. The original PNG is preserved byte-for-byte; native and emoji versions are size exports only.

Both designs retain their original colors and transparent backgrounds. Native resources are 128 × 128 RGBA PNGs in `platform/android/overlay/TMessagesProj/src/main/res/drawable-nodpi`. Original resource identifiers and server assignment keys are retained. The service key `star` is displayed as Mira. iOS does not yet contain the Nebula profile badge renderer.

The supporter artwork was generated with the built-in ImageGen tool. Mira was supplied by the user on 2026-09-20 and was not regenerated or redesigned. Telegram emoji exports are available in `design/emoji`.

## Final prompts

Supporter: Generate a clean flat UI badge using the exact NebulaGram plane silhouette and its two separate speed strokes from `design/motion/assets/logo.png`. Replace white with a smooth diagonal #62C9F7 to #6680ED gradient. Remove the square background. Only three opaque filled shapes on transparency. No glow, halo, blur, shadow, outline, lighting, bevel or 3D. Center with 15 percent padding. No text, mockup or checkerboard.

Mira source: user attachment `codex-clipboard-6fe91bd9-9a5c-489c-8683-285d4fe11b6b.png`, 1254 × 1254 RGBA PNG. No generation prompt applies to this replacement.
