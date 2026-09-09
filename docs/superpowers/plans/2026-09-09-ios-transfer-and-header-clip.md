# iOS transfer and Android header clipping Implementation Plan

> Execute inline: no subagent skill is available in this workspace.

**Goal:** Fix reported long-title overflow and expose validated iOS settings file transfer.
**Architecture:** Clip only header text/copies to their measured text area; do not clip avatars or whole containers. Keep transfer parsing/read limits in Foundation, UIKit file handling in a separate adapter, and the existing small Telegram hooks unchanged.
**Tech Stack:** Java/Canvas, Swift/Foundation/UIKit, ItemListUI, Python regressions, macOS CI.

## Android regression
- [x] Add scripts/check-header-text-clip.py: execute extracted native drawChild text branch in a recording Canvas harness, with floating/classic modes, title/subtitle/copies and disabled Nebula layout. Old code must fail classic-title clipping.
- [x] In ChatAvatarContainer.drawChild use the existing Nebula text guard independent of chatHeader; clip to primary title/subtitle getX() through getX()+getWidth() before optional floating bounce. Preserve avatar/decorations and restore Canvas state. Persist as patches/android/0073-header-text-clip.patch.
- [x] Run the regression, check-chat-native.py, ordered patch application, and Java compilation in the disposable existing build tree; register patch and workflow check.

## iOS transfer
- [x] Add Foundation preview(data:) returning validated document, supported/pending key counts; maximum payload 1 MiB, strict existing v1 validation, no mutation during preview. Add tests for preview purity, malformed/oversized input and pending-key preservation.
- [x] Add UIKit-only NebulaSettingsFileTransfer coordinator: weak host, JSON picker, bounded coordinated read off main with balanced security scope, explicit replacement confirmation, cancellation without writes, export picker with owned temporary-file cleanup. RU/EN user-facing errors; do not expose file content or paths in errors.
- [x] Wire import/export ItemListActionItem entries; coordinator lifetime retained by action arguments, weak reference to screen, disable export on corrupt storage but keep recovery import available.
- [x] Regenerate overlay, run Python checks, add real iOS-SDK typecheck for UIKit coordinator/contract in CI, run Swift tests. Full Telegram/IPA and device acceptance remain explicitly unverified.
- [x] Document and publish only owned changes; preserve unrelated existing changes.

## Added user report: emoji panel and scrub reversion
- [x] Patch 0074 chooses transparent child construction before layout, routes the panel to chat glass and reuses it in the bottom-control capture, without capturing the panel recursively.
- [x] Separate full touch cells from visible lens geometry; add limited vertical release drift tolerance after horizontal capture. Added regression fails old gesture and passes new code; disabled/cancel/multitouch cases remain passing.

## Evidence / remaining acceptance
- Android: all 28 workflow checks passed, 69 ordered patches apply, native Java compilation succeeded in 2m38s. Logs: build/header-emoji-0909/checks.log and compile.log.
- iOS: commit 84377d1; macOS run 34336821790 passed 11 Swift tests, embedded mirror compile/run and actual iOS SDK typecheck for UIKit transfer adapter. Settings contract run 34336821810 passed.
- ADB has no device connected. No Android visual/device result, full iOS Telegram build, IPA or iPhone/iCloud acceptance is claimed. Remaining device cases are listed in platform/ios/README.md.
