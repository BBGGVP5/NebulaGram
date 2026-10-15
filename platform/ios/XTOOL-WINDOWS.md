# iOS component development from Windows

The xtool trial on this machine succeeded through WSL. It used xtool 1.22.2,
Swift 6.4.1 and the user's Xcode 27.2 beta 3 archive (27B5038j). The installed
Darwin Swift SDK includes iOS SDK 27.2. The archive and SDK stay outside Git.

Two unsigned arm64 probe apps were actually compiled and packaged:

- `build/xtool-probe/NebulaXtoolProbe.ipa`: native formatting toolbar,
  128,199 bytes, SHA-256 `35bb836361035f70cc3cef0f9f8ed9f5cb20d512412dd27c72a9b2335f63ddb3`.
- `build/xtool-probe/NebulaOnboardingProbe.ipa`: production welcome/authentication
  presentation and NebulaLink overview, 277,463 bytes, SHA-256
  `eeeffaa8cf4b43a08e1b709f12f63aabf578163474190dc78467ea277bfabd9f`.

These probe packages use minimum iOS 17 from the xtool template. They are not the
full NebulaGram client, are unsigned and have not been run on an iPhone. The full
client still uses its pinned Bazel/rules_apple build and minimum iOS 15. Its
macOS CI build/IPA remains the application-level compilation result.

## Repeatable component/API check

After installing Swift and the Darwin SDK with [xtool](https://github.com/xtool-org/xtool),
run inside WSL from the repository:

```bash
python3 platform/ios/tools/xtool-component-check.py \
  --sdk-bundle "$HOME/.swiftpm/swift-sdks/darwin.artifactbundle" \
  --output /tmp/nebulagram-component-check-001
```

The output must be a new directory. The checker never deletes/resets an existing
directory. It compiles the real generated Foundation module and typechecks five
production UIKit components at deployment target iOS 15. UIKit, Objective-C and
SDK availability checks are real; Telegram theme and ASDisplayNode modules use
small explicit API adapters. Full Telegram module linkage, application packaging,
device layout and signing are separate checks.

Run the complete Swift package's executable tests on macOS (the bootstrap CI
does this). It imports Apple's Security framework; ordinary Linux `swift test`
cannot provide that framework. The WSL check above targets iOS with the installed
Darwin SDK and typechecks production sources instead of substituting a Linux
implementation of key storage.

`verification.json` records exact source hashes, compiler/SDK and check scope.
No authorization keys, Xcode archive, signing identity or provisioning profile
is read or exported. WSL builds on this host should run in a memory-limited
transient service (2 GiB limit, no swap); there is no need to stop user apps.
