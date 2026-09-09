# iOS Push Signing Check Implementation Plan

> Execute inline; subagent/executing-plans skills are not available here.

**Goal:** Diagnose notification signing prerequisites in an extracted user-signed iOS app without credentials, changing its signature or claiming push delivery.
**Architecture:** Separate a pure plist validation function from macOS read-only codesign/security inspection. Validate the main app and its actual notification service extension. No hard-coded Nebula signing team. Report only fixed diagnostic codes/messages, never profile contents, UDIDs or tokens.
**Tech Stack:** Python 3.11 unittest/plistlib, macOS codesign/security, existing bootstrap CI.

- [ ] Add failing fixture tests in platform/ios/tools/test_notification_signing.py: valid other team, missing APS/group/extension, profile mismatch/expiry, sandbox mismatch, malformed input and sensitive-data redaction.
- [ ] Add platform/ios/tools/check-notification-signing.py: pure validation, profile prefix support, exact App Group and extension base ID checks, bounded plist reads and subprocess timeouts. Require --expected-environment explicitly; use macOS tools on an extracted .app, no archive extraction or signing/private-key access.
- [ ] Add test invocation to .github/workflows/ios-bootstrap.yml; run local tests and existing bootstrap checks.
- [ ] Document commands, support boundaries and runtime requirements in platform/ios/NOTIFICATIONS.md. Include prior user-certificate requirement without weakening it.
- [ ] Inspect current native run; fix compiler errors if available. Commit owned changes and verify macOS CI. No push-delivery or installability claims from static checks.
