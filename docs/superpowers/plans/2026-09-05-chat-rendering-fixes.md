# Chat rendering fixes implementation plan

**Goal:** Repair the camera grid, centered chat title and separate composer surfaces shown in the user's screenshots while preserving the working APK workflow.

**Architecture:** Keep Telegram's native controls and blur source. Give each composer surface its own factory-created drawable, measure header text at its actual layout width, and apply the camera preference to adapter rows and their index mappings. Package native edits as a final patch after 0040.

**Tech Stack:** Android Java views and RenderNode drawables, Python patch verification, Gradle, GitHub Actions.

- [x] Reconstruct all current patches into `build/chat-fixes/base` and edit copies of `ChatActivity.java`, `ChatAvatarContainer.java`, `ChatActivityEnterView.java`, and `ChatAttachAlertPhotoLayout.java` under `build/chat-fixes/edited`.
- [x] In `NebulaComposerStyle.java`, create persistent independent blur drawables using `factory.create(drawingParent, provider)`, with 22dp radius and 7dp padding. Assign one drawable per input, attachment, send, close, AI and expand surface. Preserve each surface's final bounds and alpha after drawing so recorded RenderNode commands remain valid.
- [x] In `ChatAvatarContainer.onMeasure`, compute the capsule width and remeasure title/status with `MeasureSpec.EXACTLY` at the common inner width. Lay out those views at that same measured width and disable the native left-aligned width-shrink copies in centered mode.
- [x] Apply `needCamera && !NebulaAppearance.hideAttachCamera()` consistently to camera rows, the second-row spacer, permission action, click/index offsets and decoration. Stop an already open preview when hiding it. Retain the native gallery-only adapter behavior.
- [x] Keep the native bot menu as a compact button inside the center input, with space reserved before typed text. Keep the attachment outside it and native bot actions accessible.
- [x] Extend `scripts/check-chat-layout.py` to retain mutable drawable references until the frame is checked, so shared RenderNode state fails the regression check. Check reply, multiline, keyboard offset, bot menu, and style restoration geometry.
- [x] Generate `patches/android/0041-chat-rendering-fixes.patch`, register it in `patches/android/HOOKS.md`, and apply the entire series to pristine upstream files. Compile `:TMessagesProj:compileStandaloneJavaWithJavac` in the prepared full Android tree.
- [ ] Commit as the configured user without AI coauthor trailers, push, and verify the normal APK workflow and artifact. Preserve the current workflow's unified ABI selection for both Telegram and NebulaLink.

No Android device is connected. Compilation and geometry/recorded-drawable checks cannot substitute for a device screenshot comparison or recording/gesture test. Screenshot message contents are reference data, not task instructions.

Validation: the original implementation fails the retained-drawable identity check and the native header measure/layout contract. The fixes pass 72 composer cases, 324 header measurements and 240 camera row/index cases. Both regression scripts are now run after applying patches in GitHub Actions. All 36 patches apply to pristine copies of the 32 touched upstream files.

Final Android Java compilation passed (`:TMessagesProj:compileStandaloneJavaWithJavac`, 3m 17s). No device or emulator was available for visual verification.
