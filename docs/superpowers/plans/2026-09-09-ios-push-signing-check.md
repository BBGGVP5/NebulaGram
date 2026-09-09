# iOS Push Signing Check Implementation Plan

> Execute inline; subagent/executing-plans skills are not available here.

**Goal:** Diagnose notification signing prerequisites in an extracted user-signed iOS app without credentials, changing its signature or claiming push delivery.
**Architecture:** Separate a pure plist validation function from macOS read-only codesign/security inspection. Validate the main app and its actual notification service extension. No hard-coded Nebula signing team. Report only fixed diagnostic codes/messages, never profile contents, UDIDs or tokens.
**Tech Stack:** Python 3.11 unittest/plistlib, macOS codesign/security, existing bootstrap CI.

- [x] Add failing fixture tests in platform/ios/tools/test_notification_signing.py: valid other team, missing APS/group/extension, profile mismatch/expiry, sandbox mismatch, malformed input and sensitive-data redaction.
- [x] Add platform/ios/tools/check-notification-signing.py: pure validation, profile prefix support, exact App Group and extension base ID checks, bounded plist reads and subprocess timeouts. Require --expected-environment explicitly; use macOS tools on an extracted .app, no archive extraction or signing/private-key access.
- [x] Add test invocation to .github/workflows/ios-bootstrap.yml; run local tests and existing bootstrap checks.
- [x] Document commands, support boundaries and runtime requirements in platform/ios/NOTIFICATIONS.md. Include prior user-certificate requirement without weakening it.
- [x] Inspect current native run; fix compiler errors if available. Commit owned changes and verify macOS CI. No push-delivery or installability claims from static checks.

## Evidence and limitations

- Windows: 8 diagnostic tests passed; real macOS codesign fixture skipped as intended.
- macOS bootstrap [34344610647](https://github.com/BBGGVP5/NebulaGram/actions/runs/34344610647) at a03e66b passed: 11 Swift tests, all 9 signing diagnostic tests (including actual codesign read/verify on a disposable ad-hoc fixture), 6 preparation tests, embedded catalog/store and UIKit adapter SDK checks.
- Native module run [34341129456](https://github.com/BBGGVP5/NebulaGram/actions/runs/34341129456) at ab32b65 passed.
- New catalog preferences increased the count from 63 to 65. macOS caught a hard-coded embedded smoke count missed by the first local checks; the smoke expectation now comes from the canonical catalog, and macOS passed on rerun.
- Real user-signed IPA with Apple-issued profiles and physical-device/provider delivery testing remain pending. The ad-hoc fixture tests the macOS command reader, not installability, APNs delivery or certificate revocation.
