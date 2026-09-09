# NebulaGram iOS — native settings bootstrap

## Current scope

Official Telegram iOS is pinned by the `vendor/telegram-ios` gitlink to
`6ad963e5b62d354da79040f388ae2b9132fb17b8` (12.9.2). Its `versions.json` specifies
Xcode 26.2, Bazel 8.4.2 and macOS 26. These are upstream build requirements,
not a claim that the full app has been built here.

- `NebulaSettingsContract`: Foundation-only validation and persistent store.
- `overlay/submodules/SettingsUI`: native RU/EN NebulaGram screen in ItemListUI,
  using the upstream glass switch style, not a separate cross-platform UI.
- `patches/ios/0001-nebula-settings-bootstrap.patch`: six small integration paths
  for the settings entry, routing, dependencies and folder counter presentation.
- The first experimental control is `hide_tab_counters`. It hides badge width
  as well as pixels, refreshes the folder strip on changes and preserves actual
  unread values, notification behavior and VoiceOver labels.

Other settings remain planned in the catalog until native build/device
acceptance. There are no dummy controls, IPA, signing configuration or
automatic Android-to-iOS sync in this stage.

## Storage and transfer

App-global presentation settings are a versioned `SettingsDocument` stored as
Data under `app.nebulagram.presentation.settings.v1` in UserDefaults. No Telegram
account state is moved or overwritten. Missing settings use consumer defaults;
opening the screen does not save defaults or discard damaged data.
If storage cannot be loaded, the screen disables editing and shows an error
instead of presenting a toggle that cannot be saved.

The native import/export actions open the system Files picker. Imports use a
coordinated, security-scoped, bounded read off the main thread (maximum 1 MiB),
then validate all values and show counts of connected/pending keys before an
explicit replacement confirmation. Cancelling preview does not write anything.
Export uses an app-owned temporary JSON file, cleaned after copy/cancellation;
the chosen destination is never deleted. The API validates all values before
replacing the document. Existing transferable Android keys without consumers
are retained and returned as pending keys, not activated. Importing a valid
document is the explicit recovery operation for corrupt storage. Unknown keys,
invalid types/ranges/versions and secrets are not accepted. UserDefaults uses
its normal asynchronous disk persistence; it is not cross-device cloud sync.

## Reproducible checks (from repository root)

```sh
python scripts/generate-settings-contract.py --check
python platform/ios/tools/generate-overlay.py --check
python platform/ios/tools/check-bootstrap.py
swift test --package-path platform/ios/NebulaSettingsContract
python platform/ios/tools/check-bootstrap.py --swift
```

After changing Swift contract sources or canonical JSON, run
`python platform/ios/tools/generate-overlay.py` and commit the generated mirror.
SPM reads the JSON resource; Bazel uses the generated embedded copy. CI compiles
and executes both variants; byte-for-byte generation checks prevent drift.

The bootstrap checker reads the pinned git objects into a disposable directory,
applies the ordered patch series, adds the overlay and checks integration
anchors. `--swift` parses native Swift sources and compiles/runs the actual
Foundation mirror. It does **not** typecheck the Telegram-dependent screen or
produce an app. The UIKit-only file-transfer adapter and contract also undergo
a real iOS simulator SDK typecheck, without Telegram stubs. No vendor reset,
clean or checkout is performed by the checker.
The CI fetch uses a sparse clone without nested submodules to avoid downloading
the entire app merely to check six source paths.

## Next acceptance gate

### Real native module build

The manually dispatched `iOS native integration build` workflow prepares a fresh
checkout of the pin, initializes recursive dependencies, applies the ordered
patches and copies the overlay with collision checks. It compiles the **actual**
SettingsUI, PeerInfoScreen and ChatListFilterTabContainerNode Bazel targets via
`NebulaIntegrationChecks`, using the upstream arm64 simulator configuration.
No Telegram interfaces are stubbed. It requires the exact pinned Xcode 26.2
on Apple Silicon and never silently overrides the version.

Equivalent commands on a suitable Mac, from the repository root:

```sh
python platform/ios/tools/test_native_build.py
python platform/ios/tools/native-build.py preflight --xcode 26.2
python platform/ios/tools/native-build.py prepare --destination "$HOME/nebula-ios-native-check"
python platform/ios/tools/native-build.py build --tree "$HOME/nebula-ios-native-check" --jobs 2
```

The destination must not exist: the tool never resets/deletes an existing tree
or reuses the vendor checkout. The preparation manifest records the pin and
input/source hashes. Build refuses changed inputs. Failed preparation leaves its
directory intact for diagnosis; use a new path for a new attempt.

This is a compile-only module target with **dummy API id 0 and no real signing
credentials**, not an installable/login-capable app. Fixture configuration stays
inside the disposable build tree and must not be reused for distribution.
The evidence artifact includes logs and a result whose `status` is `compiled`
only after native Bazel compilation succeeds. Passing standalone Swift checks,
preparation or syntax parsing alone is not that result.

### App and device acceptance (still separate)

1. Prepare a separate full build tree at the pin; initialize nested dependencies.
   Apply sorted iOS patches, then copy the iOS overlay to that tree.
2. Use the upstream build procedure/tool versions, the app's own Telegram API
   configuration, bundle identifier and Apple signing setup. Compile the full
   Telegram targets, including SettingsUI and ChatListFilterTabContainerNode.
3. On iPhone: open Settings → NebulaGram; toggle counter hiding; return to a
   folder strip with unread badges; check layout, swiping, editing/reordering,
   account switch, relaunch, themes and VoiceOver with hiding on and off.
4. Test Files import/export locally and through iCloud on iPhone/iPad; cancel
   selection and confirmation; reject invalid/oversized/unavailable files; reopen
   and relaunch after a valid import. Verify failed/corrupt preference handling
   without resetting existing data.
   Only then promote the catalog status and distribute a test build.

Keep feature logic in the platform adapter and Foundation module. On an upstream
upgrade, check patches, compile, then run device scenarios before changing the
pin. A clean patch application alone is not an API compatibility guarantee.

Validation evidence: macOS run
[34334236650](https://github.com/BBGGVP5/NebulaGram/actions/runs/34334236650)
passed eight Swift tests, patch/overlay checks, native syntax parsing and the
embedded Foundation compile/run. No full Telegram typecheck or device test is
included in that result.

Transfer validation evidence: macOS run
[34336821790](https://github.com/BBGGVP5/NebulaGram/actions/runs/34336821790)
passed all 11 Swift tests, the embedded mirror checks and a real iOS simulator
SDK typecheck of the UIKit-only file-transfer adapter. The complete Telegram
settings screen is still syntax-checked only; iPhone/iPad/iCloud interaction
and full application compilation remain the acceptance gate above.

### Notification delivery gate

See [NOTIFICATIONS.md](NOTIFICATIONS.md) for the audited native delivery path,
APNs/provider configuration, Apple capability limits and signed-device checklist.
Push delivery is not enabled or proven by this compile-only workflow.
