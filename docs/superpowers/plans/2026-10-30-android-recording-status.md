# Android recording status Implementation Plan

> Execute inline; this is one focused Android fix.

**Goal:** Preserve the icon and beginning of a long recording status and truncate its end with an ellipsis inside the chat header.
**Architecture:** Keep Telegram's drawable-aware text width and native END ellipsis. Disable gradient truncation for the ordinary subtitle in the Nebula header; repair the SimpleTextView setter so that disabling actually takes effect. Preserve the title marquee and animated subtitle behavior.
**Tech Stack:** Android Java, ordered patches, Python/Java regression harness.

- [x] Add `scripts/check-header-status.py`: execute the native gradient setter and header subtitle configuration; cover disabling, restoring, idempotence, forced fade direction, short/long text and icon width. Confirm failure before applying the fix.
- [x] Create `patches/android/0249-header-status-ellipsis.patch`. In the boolean gradient setter compare `ellipsizeByGradient` and `forceEllipsizeByGradientLeft` with the requested values, then request layout on change. In `setNebulaCenteredTitle`, configure `subtitleTextView.setEllipsizeByGradient(!enabled)` before gravity and padding.
- [x] Register the regression in `.github/workflows/android.yml` and describe the patch in `patches/android/HOOKS.md`.
- [x] Validate ordered application against the pinned Telegram 13.1.1 revision, run the new regression and existing header clipping/status drawing checks, and parse modified native Java. Do not reset the vendor tree or include unrelated iOS changes.
- [x] Record actual results. APK compilation and phone appearance are separate validation steps and must not be claimed without evidence.

## Verification

- Old `build/prepare-1311` failed the new regression with `Disabling gradient was ignored: long status still clips both ends`.
- `build/header-status-20261030` contains six affected/supporting native files reconstructed from pinned revision `3de37c8cfb039452be7398909461da6288babf15` and the ordered patch series.
- `python scripts/check-header-status.py --tree build/header-status-20261030`: 48 subtitle cases plus setter/header toggle assertions passed using deterministic metrics.
- `python scripts/check-header-text-clip.py --tree build/header-status-20261030`: 15 clipping cases passed.
- `python scripts/check-status-bounds.py build/header-status-20261030`: 48 status drawable cases passed.
- `python scripts/check-upstream-series.py android --tree vendor/telegram-android --ref 3de37c8cfb039452be7398909461da6288babf15`: all 244 patches apply; vendor untouched.
- `python scripts/check-native-java-syntax.py build/header-status-20261030`: javac parsed 291 native/overlay units. This is syntax validation, not dependency typechecking or an APK build.
- No APK build or device visual check performed. Next release validation: recording status on a narrow phone, large font, transition back to online status, and toggling the Nebula header style.
