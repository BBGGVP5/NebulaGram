# Nebula tools and formatted translation implementation plan

**Goal:** Open Nebula's own tools from the smaller composer glyph and preserve Telegram formatting and emoji through manual, live and draft translation.

**Architecture:** Keep the native composer slot and disabled-Nebula fallback. Add a pure Java range mapper with validated protected delimiters and a deterministic segment fallback, plus a Telegram entity adapter. Carry CharSequence snapshots through the tools and draft substitution. Present the tools as a compact sheet with mode tabs, original/result surfaces and a persistent Apply action.

**Tech Stack:** Android Java Views, Telegram entities/spans, existing translation providers and APK CI.

- [x] Add `NebulaTranslationFormat.java` for Unicode-safe protected ranges, style-boundary mapping, validated marker replies and lossless fallback. Add `NebulaRichText.java` for capture/render/copy/fingerprint and translation via existing engines. Protect custom emoji, code, links, mentions and standard emoji; preserve entity attributes and recompute UTF-16 ranges.
- [x] Wire rich results into `NebulaAutoTranslate.java`, `NebulaDraftTranslation.java` and `NebulaMessageToolsFragment.java`. Keep snapshots and cache identities sensitive to formatting, retain original spans on restore, reject stale account/provider/text results.
- [x] Update `NebulaDraftOriginal.java` to preserve CharSequence spans across prepend/append/restore. Keep IME and cancellation behavior.
- [x] Add a native patch after 0184: open Nebula tools on tap when enabled, retain native Telegram callbacks when disabled, preserve caption/draft spans at entry and prevent stale apply. Use a separate 20 dp composer icon so other icons retain their current size.
- [x] Build the popup with Telegram-like rounded tabs, original/result cards, language selector and bottom Apply; retain Ask AI, summarize, speech, tasks and settings. Use native EditText effects to render custom emoji and styles.
- [x] Add meaningful translation tests for nested/overlapping formatting, custom emoji IDs, URLs, UTF-16 emoji sequences, malformed/reordered markers, whitespace, cancellation and original restoration. Update existing fixtures for rich transport and routing. Compile against SDK, reconstruct native patches and run required checks.
- [ ] Review scoped changes, commit/push, complete CI and verify the resulting APK. Include the preceding settings-spacing changes in the same delivered build. Report unavailable physical-device validation.
