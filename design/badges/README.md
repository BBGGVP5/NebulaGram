# NebulaGram profile badge artwork

The selected artwork is integrated into Android profile names, profile settings and the badge grant selector.

- `supporter-modern.png`: flat NebulaGram logo for supporters.
- `mira-six-ray.png`: solid six-ray Mira with soft tips and the brand gradient.

Both designs use sky blue #62C9F7 through periwinkle blue #6680ED, with transparent backgrounds and no glow. Native resources are 128 × 128 RGBA PNGs in `platform/android/overlay/TMessagesProj/src/main/res/drawable-nodpi`. Original resource identifiers and server assignment keys are retained. The service key `star` is displayed as Mira. iOS does not yet contain the Nebula profile badge renderer.

The artwork was generated with the built-in ImageGen tool. Telegram emoji exports are available in `design/emoji`.

## Final prompts

Supporter: Generate a clean flat UI badge using the exact NebulaGram plane silhouette and its two separate speed strokes from `design/motion/assets/logo.png`. Replace white with a smooth diagonal #62C9F7 to #6680ED gradient. Remove the square background. Only three opaque filled shapes on transparency. No glow, halo, blur, shadow, outline, lighting, bevel or 3D. Center with 15 percent padding. No text, mockup or checkerboard.

Mira: Refine the simple badge into exactly six rays: moderately long north and south rays, four shorter diagonal rays, gently concave joins and soft pointed tips. A substantial solid center and one uninterrupted diagonal #62C9F7 to #6680ED gradient. Transparent background with no dots, holes, seams, glow, outline, shadows, texture or 3D. The user requested a shape less similar to Gemini's four-ray sparkle.
