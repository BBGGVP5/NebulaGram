# NebulaLink Nimbo Ping Build and Delivery Implementation Plan

**Goal:** produce and deliver actual Android APK and iOS IPA artifacts from the reviewed per-server Nimbo Ping implementation.

**Architecture:** integrate only the 16 backend and 17 UI implementation/test files verified by their frozen SHA256 reports, plus the three owned reports/plans and this build plan, into an isolated clean Git worktree based on current origin/main. Reuse official build/signing/version pipelines and rebuild Go native bindings from the exact integration commit. The iOS worker dispatches the existing iOS pipeline on that same SHA.

**Tech Stack:** Go/gomobile, Android Gradle/NDK, GitHub Actions, existing iOS native pipeline, Python/JVM verification.

- [x] Record current HEAD/origin, dirty-path inventory and hashes; verify both frozen source reports. Do not copy unrelated patches, backdrop, design files or foreign changes.
- [x] Read existing workflow/build/signing/version documentation and confirm build ref with main. Keep current certificate and version policy; do not expose credentials or create a public release.
- [x] Create isolated worktree under build/nimbo-ping-release at origin/main. Copy exact allowlisted implementation/test/report paths, review diff, rerun Go tests/vet and UI source/JVM checks.
- [ ] Commit only reviewed ping allowlist. Push ordinary fast-forward/ref after coordination, never force. Record exact commit SHA; send SHA/ref to iOS worker before its dispatch.
- [ ] Dispatch official Android workflow for universal APK with minification enabled; verify job really rebuilds Go AAR and includes source changes. Watch actionable failures and fix only scoped integration issues.
- [ ] Download APK artifact; verify signature, package/version/code, four ABIs, rebuilt Go binary feature markers and SHA256. Compare certificate to existing official artifact where available.
- [ ] Coordinate successful iOS artifact from the same SHA, documenting signing/install requirements rather than representing unsigned IPA as signed.
- [ ] Deliver local artifacts and official run links to main/user, with exact hashes/test evidence and remaining device-only limitations. Preserve all original workspace changes; no public posting without explicit coordination.

## Integration review

Base: existing origin/main `3c4e60c`. 37-path exact allowlist contains33 implementation/tests and4 owned docs. Thirty implementation files match frozen SHA256 exactly. Three (core/settings/menu.go and EN/RU strings_nebula_menu.xml) preserve already-committed371c869 NebulaGuard subtitle update; reversing only that line restores frozen hashes, including prior XML line ending. No other content drift. Original dirty workspace is left untouched in its checkout; detached build worktree only is committed/pushed to existing main. Go tests/vet, JVM/UI source contracts and shared settings contracts pass from the isolated worktree. Native runtime uncached race coverage previously passed on Go1.27.1. Official Android manual dispatch rebuilds all four ABIs and signed AfatStandalone; public version1.0.0 remains unchanged, build code derives from workflow run number.
