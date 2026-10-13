# AI/audio and source upgrade verification

Android uses the latest published upstream pin `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6). The dirty Android vendor checkout is preserved. iOS now uses `f1dd7a2dbd02cbbf513e75d5695d8d36d1cf5838` (13.0), Xcode 26.6 and upstream rules_apple `485016eb5b0948cc17307064e7977a6d68e94046`. The clean local iOS checkout was updated to that pin.

## Completed checks

- All 106 Android interface/regression commands passed locally against the owned pinned tree and in the successful APK 1000516 CI run. The Linux CI executed the native account-slot runtime check; Windows ran its syntax/contract check.
- 22 scenarios execute the real native record-button touch and lock methods. Active recording taps finish through native handling; the matching release cannot toggle modes or send twice. Idle tap switching, holds, cancellation and payment handling remain covered.
- 438 selected-clip, snapshot, draft and send cases pass. Text is captured before confirmation, and conversion occurs only on accepted native send. Cancelled, trimmed, resumed, stale-account or duplicate callbacks cannot apply an old result.
- SDK typecheck compiles the real inline controller, host, drawing helpers and audio decoder.
- iOS bootstrap applies all 104 ordered patches to 163 upstream paths, parses the native/overlay Swift sources, tests the embedded Foundation module and executes the real AVFoundation CAF/AAC selected-clip exports without modifying the original bytes.
- iOS bootstrap run `38402381024` passed 15 cases executing the actual native recording-send capture/payment callback: immutable text, native chunk/charge count, scheduling/repeat, original voice/view-once, changed account/peer/trim/service/format, superseded attempts and duplicate confirmation.
- Android widget QA run `38411802167` rendered the production Aa controller/host on Android 35 with light/dark palettes, a narrow display, font scale 1.3 and an actually visible IME. The recording host is an offline adapter which models Telegram moving the recorder above the keyboard. Reports require fully visible format choices and editor, plus the retry action when the keyboard is closed. Images were inspected. This verifies widget geometry, not full Telegram/camera/ASR acceptance.

## Artifacts and pending builds

APK 1000516: run `38397138020`, source `98d6d76759adc6b5464ed748e4ba8eb84606d146`, 60,069,922 bytes. SHA256 `6c23db2a960c394f18d9b3b587ff70aaed91cacd550108b183e8595452987761`. Verified ZIP integrity, arm64-v8a libraries, package/version and signing certificate `a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab`.

APK 1000522 failed dependency resolution after an interrupted parallel submodule clone left jlatexmath at its pin without working files. Fresh CI now forces nested checkouts and verifies missing tracked files before Gradle; the read-only verifier has a fixture reproducing this state. The latest home-toolbar correction is building as APK 1000526 in run `38417946397`, source `3d26ad9`. All 106 local checks pass, including flat/glass layout independence. Download/signature verification is pending.

Full unsigned iOS IPA run `38403389424` and native run `38405018482` failed compilation. The reported issues are repaired in `f6254352e00e23480c1723d7eddace1c9d5b22cd`: ListViewItemNeighbors, modern UIButton configuration insets, successful PasscodeSession routing and an unused camera binding. All 105 ordered patches pass local bootstrap. New native run `38417177542` and IPA run `38417186420` are in progress; neither is claimed successful. The integration minimum is iOS 15, matching upstream, and bootstrap checks this parity.

No physical device acceptance was performed. Both local read-only emulator attempts stalled; their owned processes were stopped. CI widget images do not prove camera operation, voice-recognition availability on a particular phone, iOS installation or APNs delivery.
