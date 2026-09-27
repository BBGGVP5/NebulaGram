# Nebula dialogs and Nano availability implementation plan

**Goal:** Make Nebula-owned Android dialogs consistent, readable and correctly selected; diagnose Nano availability without misreporting service failures as unsupported hardware.

**Architecture:** A shared themed bottom-sheet builder replaces local AlertDialog builders. Choice state is an explicit index, independent of labels. Nano operations capture model configuration and report errors separately from FeatureStatus; UI results are guarded by request identity and lifecycle.

**Tech Stack:** Android Java, Telegram BottomSheet, ML Kit Prompt API beta4, Python/JVM checks.

- [x] Add `NebulaDialog.java`: rounded surface, scrolling content, wrapping labels, trailing selection control, accessible rows and full-width actions.
- [x] Migrate Nebula dialog callers and supply current selections; remove inline checkmarks.
- [x] Restructure `NebulaAiFragment.java` connection/model cards and keep drafts when changing model configuration.
- [x] Fix `NebulaNanoAi.java`: capture configuration, retain errors, Stable/Full default, useful AICore diagnostics.
- [x] Guard async model checks/downloads from duplicates and stale callbacks.
- [x] Run Java, AI, settings and layout checks; inspect diff; push exact task files and dispatch Android build once.

Validation includes actual Java compilation against available dependencies where possible and focused runtime tests for failure classification. Hardware Nano execution and screenshot verification require an attached Android device; do not claim these without evidence.

Local validation: new dialog and Nano bridge compile against Android 36, cached Telegram classes and ML Kit beta4. AI screen compiles with those dependencies and a stub for the unchanged settings-link binder (the cached Telegram tree predates that helper). Settings contract, localization, root layout, tab state, AI protocol, availability and Nano error/cancellation tests pass. A full compile using the old cached tree is not representative of the pinned CI tree. No connected device: visual QA and Nano inference remain unverified.
