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

The import/export **API**, not yet a file-picker UI, validates all values before
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
produce an app. No vendor reset, clean or checkout is performed by the checker.
The CI fetch uses a sparse clone without nested submodules to avoid downloading
the entire app merely to check six source paths.

## Next acceptance gate

1. Prepare a separate full build tree at the pin; initialize nested dependencies.
   Apply sorted iOS patches, then copy the iOS overlay to that tree.
2. Use the upstream build procedure/tool versions, the app's own Telegram API
   configuration, bundle identifier and Apple signing setup. Compile the full
   Telegram targets, including SettingsUI and ChatListFilterTabContainerNode.
3. On iPhone: open Settings → NebulaGram; toggle counter hiding; return to a
   folder strip with unread badges; check layout, swiping, editing/reordering,
   account switch, relaunch, themes and VoiceOver with hiding on and off.
4. Verify failed/corrupt preference handling without resetting existing data.
   Only then promote the catalog status and distribute a test build.

Keep feature logic in the platform adapter and Foundation module. On an upstream
upgrade, check patches, compile, then run device scenarios before changing the
pin. A clean patch application alone is not an API compatibility guarantee.

Validation evidence: macOS run
[34334236650](https://github.com/BBGGVP5/NebulaGram/actions/runs/34334236650)
passed eight Swift tests, patch/overlay checks, native syntax parsing and the
embedded Foundation compile/run. No full Telegram typecheck or device test is
included in that result.
