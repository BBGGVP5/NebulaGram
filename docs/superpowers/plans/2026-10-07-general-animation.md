# General section animation follow-up

**Goal:** Replace the remaining static General introduction with a genuine animated Telegram asset, preserving the recent menu, header and AI-control improvements.

**Finding:** The component can fall back to stock gear artwork when no matching animated document is available. Prior synthetic document fixtures verified selection and playback behavior, not the server-side existence of an animated gear. A real Telegram Toolbox reference was inspected: 512×512, 45 animated WebP frames with looping. The reference is for validation only and will not be redistributed; the app should continue using Telegram's native asset downloader.

- [x] Resolve the user's preference between the verified animated Toolbox illustration and an exact animated gear requirement. User selected Toolbox.
- [x] Apply the selected illustration consistently to the General hub, General section and resource-to-introduction mapping.
- [x] Inspect all three General introduction routes to confirm they use Toolbox. Keep small row/control icons unchanged; no additional tests for this small visual substitution.
- [x] Verify relevant emoji/routing/API behavior and required checks, build and verify a fresh APK.

No physical-device animation claim should be made without a connected device. Keep the user's modified vendor checkout untouched and execute inline.

Local verification: all 87 existing required checks passed after 179 pinned patches reconstructed. Android SDK helper compilation passed. General hub, General section and settings-icon introduction mapping were inspected and now select 🧰. The external reference is 45-frame animated WebP, not a static image; the app uses Telegram's own document loader rather than bundling that reference. No ADB device is attached.

Delivery: Android 398 / versionCode 1000398 succeeded in run 37667390209 from source `4f30517f465cf46ba532df2411b61b33b597cee3`. Settings contract run 37667389956 succeeded. APK SHA-256: `f5d6d5535d64751007b15794855d015bb39756a95fa9040edce67a7cf1aa8836`. Package, arm64-v8a/native core, existing signing certificate and icon pack were verified in `build/qa-apk-general-toolbox-4f30517/local-verification.json`. Download initially timed out; the complete artifact was retrieved and verified successfully. Actual phone playback remains unverified without a device.
