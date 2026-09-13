# NebulaGuard Copy and Header Spacing Implementation Plan

**Goal:** Give NebulaGuard a promotional subtitle and separate the settings hero from the first card, as confirmed by the user.

**Architecture:** Change only the existing guard subtitle in shared menu metadata and Android RU/EN resources. Add an external bottom margin at the section hero insertion point, not inside the hero or globally on all cards.

**Tech Stack:** Android Java/XML, shared Go menu, Python regression checks.

## Tasks
- [x] Add `scripts/check-guard-header-spacing.py`: assert RU/EN `nl_guard_sub` text, shared fallback and unchanged `guard.open` action; verify the hero is added using MATCH_PARENT/WRAP_CONTENT parameters with a 12dp bottom margin.
- [x] Run the new check and confirm it fails on existing code.
- [x] In `NebulaSectionFragment.build`, create `heroParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)`, set `heroParams.bottomMargin = AndroidUtilities.dp(12)`, and pass it to the hero `addView` call. This applies to all section types and palette rebuilds.
- [x] Set RU subtitle to `Ваш лучший сервис для свободного интернета`, EN/shared fallback to `Your go-to service for internet freedom`. Keep the destination and command unchanged.
- [x] Run regression checks, shared settings Go tests and compile the changed Java class against the previously compiled disposable Telegram tree. Inspect focused diffs to preserve concurrent NebulaLink edits. Follow-up: user requested commit/push only, without CI builds. Use [skip ci].

Verification: resource/source guard PASS; go test ./core/settings PASS; actual Android Java compilation BUILD SUCCESSFUL in 26s (build/guard-spacing-javac.log). No APK/IPA dispatch.
