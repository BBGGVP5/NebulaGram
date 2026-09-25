# Android tools design and remaining features

**Goal:** Make message tools and tasks consistent with Nebula settings; implement the remaining chat locks and opt-in settings synchronization with honest capabilities.

**Architecture:** Reuse NebulaCard/NebulaRow and a small themed form helper. Keep private task and lock data out of the settings transfer schema. Cloud transport is selected separately; do not upload anything while developing.

**Tech Stack:** Android Java overlays, existing Telegram navigation/MTProto, ordered upstream patches.

- [x] Add NebulaFormUi with explicit theme colors for action bars, input text/hints/cursor, fields and labels. Keep font scaling and scrollable forms.
- [x] Rebuild NebulaMessageToolsFragment using source/language fields, action groups with icons and an initially hidden result card. Copy only a successful result; show cancellation only while work/speech is active.
- [x] Rebuild NebulaTasksFragment with grouped active/completed rows and an empty state. Move editing into NebulaTaskEditorFragment with a single reminder switch, date/time value and save action. Retain account-scoped storage and validation.
- [x] Recompose all five intro pages around a consistent art box and text position; remove tilted overlapping ribbons.
- [x] Implement separate chat passwords with authenticated settings changes, automatic lock on background and guarded chat/media/preview routes. Passwords never enter exported settings.
- [x] Implement opt-in settings synchronization in Saved Messages with bounded data, conflict handling and credential exclusion.
- [x] Run Android standalone Java compilation and focused persistence/crypto/sync regression checks against the 12.10.4 patch tree. Firebase processing was skipped because the local JSON lacks a standalone client.
- [ ] Commit the completed task files and push to the build branch; the user installs independently.

Validation commands: python scripts/check-settings-root.py; python scripts/check-local-tasks.py; python scripts/check-settings-contract.py; native :TMessagesProj:compileStandaloneJavaWithJavac --offline. UI on a real device must be distinguished from compilation.
