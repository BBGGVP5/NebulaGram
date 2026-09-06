# In-app Android updates implementation plan

**Goal:** Check NebulaGram releases, download a compatible APK with progress, and offer Android installation from the app.

**Architecture:** The signed-in Telegram account resolves `ngram_releases` and verifies channel ID `3985386470`. Recognized APK filenames carry the independent app version, Telegram base, numeric build code and ABI. Telegram FileLoader downloads the selected document; a private copy is checked against installed package identity and signer before Android's installer opens. News uses the separate `ngram_official` channel. CI produces artifacts; the owner publishes them manually.

**Tech stack:** Java, Telegram MTProto/FileLoader/message entities/animated emoji, Android FileProvider/package metadata, Python release tooling, GitHub Actions.

## Tasks

- [x] Parse CI filenames, choose the highest compatible numeric code and ignore same-code reuploads.
- [x] Implement throttled checks, account isolation, Telegram download progress/cancel/retry and guarded installer handoff.
- [x] Preserve Telegram entities, links, Unicode and native animated emoji in scrollable multilingual changelogs.
- [x] Read APK/album captions or follow an explicitly labelled link to a specific post in the release channel; never guess from adjacent news.
- [x] Add NebulaGram 1.0.0, monotonic CI codes, APK metadata verification and `tg://update` routing through the launch/passcode flow in patch 0055.
- [x] Group settings, keep About last, distinguish news/releases and fill empty About icons.
- [x] Increase bottom lens rebound, compress on hold, deform with drag speed, and animate glass menus/attachments in patch 0056.
- [x] Fix banner rounding, description fade colour and avatar-to-profile interaction in patch 0057.
- [x] Preserve native peer profile backgrounds without a photo banner, use real profile colours/emoji in preview, and add per-style switch motion and thumb padding in patch 0058.
- [x] Document publishing; compile all changed Java, run release/changelog and layout checks, and verify 53 patches against pristine sources.
- [ ] Commit/push and start the APK workflow.

## Acceptance checks

- Old/same codes never offer installation; wrong package/ABI/minSdk/name/signature are rejected.
- Missing releases are distinct from connection errors; automatic checks are throttled and can be disabled; manual retry remains available.
- Downloads show progress, survive navigation, can be cancelled/retried, and never launch an installer without a user action.
- Installer permission denial retains the APK and allows retry; another app cannot be installed through this updater.
- APK captions and explicit linked posts preserve their original changelogs. Ordinary news is never selected by proximity.
- No token, bot or extra server is required in the app. Real installation requires a phone and cannot be claimed from JVM tests.
