# Ten Launcher Icons Implementation Plan

**Goal:** Add ten original NebulaGram launcher choices without replacing the six existing aliases; deliver a skip-CI commit.

**Architecture:** Extend the existing code-native mark renderer, not a generated bitmap logo. Keep new variant recipes and scalable SVG masters in design/icon/variants. Render legacy mipmaps and safe-zone adaptive foregrounds, matching XML backgrounds, and a contact sheet. Append stable enum/component identifiers through a new ordered Telegram patch; the native selector already enumerates all icons.

**Tech Stack:** Python/Pillow existing geometry renderer, SVG, Android resources/manifests, Java selector.

- [x] Extend scripts/check-launcher-icons.py to require 16 variants, 256 native switching pairs, unique aliases, free choices and valid resource dimensions/layers.
- [x] Add scripts/gen-launcher-extras.py and ten recipes: ink, paper, mint, lavender, tangerine, rose, orbit, blueprint, nova, monogram. Generate SVG/1024px masters, five densities, adaptive XML and RU/EN names without rewriting old artwork.
- [x] Add patches/android/0090-extra-launcher-icons.patch for the enum and main/standalone manifests, preserving original enum ordering and alias names.
- [x] Run the failing check before implementation, then passing native controller/resource checks and Android Java/resource compilation in the disposable tree. Inspect the contact sheet at small sizes.
- [x] Commit only this task's files and push with [skip ci]. No APK/IPA workflow dispatch. iOS alternate-icon wiring is not implemented in the current project; export reusable full-bleed masters without claiming iOS selector support.

Follow-up: two additional original logos requested. Nova and Monogram have their own monochrome vectors; the original generator remains available and invokes the extras generator to retain all labels.

Verification: PASS 256 native-controller transitions, 16 resource/alias choices, 50 safe-zone foregrounds, opaque SVG/PNG masters, ordered patch round-trip and actual merged release aliases. Java compilation and release resource processing: BUILD SUCCESSFUL in 3m26s (build/launcher-variants-check.log). Contact sheet visually reviewed. No device install or APK/IPA workflow started.
