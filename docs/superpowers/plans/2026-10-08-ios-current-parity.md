# iOS Current Android Parity Implementation Plan

> **For agentic workers:** Execute inline, task by task. The user requested both the plan and implementation. The optional executing-plans/subagent skills are not installed; use the checked steps below without a further execution-choice handoff. Preserve unrelated vendor and artwork changes.

**Goal:** Adapt the current non-camera NebulaGram Android features and new settings design to native iOS, with an explicit inventory of missing behavior and a verified device IPA.

**Architecture:** Keep Telegram iOS navigation, text entities, account isolation and native message eligibility. Extend the existing Swift contract, SettingsUI/NebulaLinkUI overlays and ordered upstream patches. A preference is exposed only alongside its actual consumer; Android-only rendering/OS controls are excluded, not presented as working iOS switches.

**Tech Stack:** Swift, UIKit, AsyncDisplayKit, ItemListUI, Telegram AnimatedStickerNode, Postbox, SwiftSignalKit, WebKit, Bazel and Xcode 26.2.

---

## Scope and observed gaps

Source baseline: Android `9c8e641`, iOS pinned Telegram 12.9.2 with 71 ordered patches. The old parity documents describe earlier checkpoints, not October 8 completion.

| Area | Present | Missing in the current iOS source | Delivery requirement |
|---|---|---|---|
| Settings | Categories, search, native rows | New hierarchy, About last, tools/AI/tasks together, compact matching introductions | Root and nested screens share geometry and native Telegram animation |
| Large emoji | Hero initializer accepts symbol/title | Hero ignores both; no animated page emoji | Real Telegram sticker decoding, visible fallback, replay on return, reduced-motion support |
| Choices | Themed centered popup, search | Checkmarks still default | Highlight only, wrapping labels, selected accessibility trait |
| NebulaLink | Working proxy/server/subscription UI | Matching introduction and current grouped navigation | Preserve working connection/subscription lifecycle and onboarding |
| AI settings | One connection per provider, local Apple model, history | Named services, OpenRouter/Perplexity, roles, generation preferences | Migrate old configuration without losing keys; Keychain remains separate |
| AI editor/tools | Plain text actions, draft callback, live translation | Rich text/entity preservation, style presets, own compact editor and matching material | Keep custom emoji IDs/links/styles, explicit apply, cancellation and stale-result checks |
| Tasks | Local tasks | Shared large emoji and current visual arrangement | Preserve task persistence and notifications |
| Browser | Native browser | Opt-in ad blocking, exclusions, supported EasyList subset | WKWebView content rules and page-scoped cosmetic rules; exclude Mini Apps |
| Messages/media | Many native hooks through 0071 | October 8 message/menu/media options and previews | Map each added control to native behavior and test boundaries |
| Filters | Earlier basic filtering inventory | Current phrase/word/transliteration/peer exceptions | Account-scoped reversible visibility, no deletion |
| Profile/privacy | Profile glass and visibility, retained copies, authentication | Current preview/local phone/DC additions and organization | Retain native permissions and authentication cancellation |
| Icons/transfer | Three bundled packs, validated JSON transfer | Current pack/import and saved-settings parity audit | Preserve unsupported imports as inactive; never export credentials |

Excluded by this request: camera backend selection, CameraX/Camera2, phone camera preview, lens/exposure/quality enhancements, old/new recorder controls and encoded camera-switch blur. Existing iOS camera behavior is retained. Also excluded: Android Material You, predictive back, Android font/emoji replacement, Android foreground services and emulation of native iOS layout.

## Task 1 — Shared settings introductions and hierarchy

**Files:** `platform/ios/overlay/submodules/SettingsUI/Sources/NebulaSettingsHero.swift`, new `NebulaAnimatedSettingsEmoji.swift`, new `NebulaSettingsIntroItem.swift`, `NebulaSettingsController.swift`, `NebulaPrivacyController.swift`, `NebulaTasksController.swift`, `NebulaQuickActions.swift`, `platform/ios/overlay/submodules/NebulaLinkUI/Sources/NebulaLinkController.swift` and `NebulaLinkOverviewView.swift`.

- [x] Add a common introduction: 88-point emoji canvas, 12-point emoji/title gap, 8-point title/description gap, 20-point side margins and minimum two-line description. Dynamic Type can increase height; never clip text.
- [x] Use `context.engine.stickers.loadedStickerPack(reference: .name("RestrictedEmoji"), forceActualized: false)` with native animated-emoji fallback. Select matching normalized index keys; keep decoded animation separate from placeholder. Fetch through account resources and dispose on removal.
- [x] Replay the node from the start when its page becomes visible again; stop offscreen/background and use a still frame under Reduce Motion. Each view owns its decoder/playhead.
- [x] Add a native list introduction item for ItemListController pages and the same UIKit header for table screens. Pass the shared header into NebulaLink so the module dependency remains one-way.
- [x] Root order: NebulaLink; General; Appearance; Navigation; Chats and tools; Profile; Folders; Privacy; About. Move transfer/browser into General, AI/tools/tasks into Chats and tools, support/community/build into About. Preserve search destinations.
- [x] Remove terminal full stops from short introduction descriptions; retain prose punctuation in explanatory text.

Acceptance: initial load and return show the same layout, 🧰 for General and 🔗 for NebulaLink, no substituted gear art, no emoji on ordinary settings-row icons.

## Task 2 — Choices and previews

**Files:** `NebulaChoiceController.swift`, `NebulaSettingsStyle.swift`, `NebulaBehaviorController.swift`, new `NebulaPresentationPreviewController.swift` in SettingsUI; native ContextUI/PeerInfoScreen hooks exported after patch 0071.

- [x] Set every choice accessory to `.none`; apply selected background and `.selected` accessibility trait using the original choice index, including filtered language lists.
- [x] Retain bounded centered presentation, keyboard avoidance and Dynamic Type scrolling. Honor Reduce Motion in opening/closing.
- [x] Implement an interactive message/menu sample and profile sample driven by the same preference snapshot as native consumers. Native extracted content owns its visibility until dismissal completes, avoiding Android's duplicate/fade handoff defect.
- [ ] Verify selection callbacks after search, empty results and cancel; verify preview close leaves exactly one source message visible.

## Task 3 — Named AI services, roles and generation

**Files:** `platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaAiSettings.swift`, `NebulaAiSecrets.swift`, new `NebulaAiServices.swift` and `NebulaAiRoles.swift`; matching XCTest files; SettingsUI `NebulaAiController.swift`, `NebulaAiService.swift`, `NebulaAiChatController.swift` and new service/role controllers.

- [x] Introduce stable service IDs, provider/model/endpoint metadata and Keychain keys scoped by service ID. On first use migrate configured legacy providers once; keep existing keys until migration succeeds. Deleting a service deletes only its associated key.
- [x] Add OpenRouter and Perplexity using their own HTTPS roots; keep OpenAI/Anthropic/Gemini/custom and locally gated Apple Intelligence. Switching provider never reuses a custom endpoint accidentally.
- [x] Add built-in localized Assistant/Summarizer/Proofreader role labels, custom roles and role selection; translation uses its own instructions.
- [x] Bound generation temperature, reasoning and streaming preferences; send only supported provider fields. Implement cancellable incremental response parsing with final validation; cancelled replies do not enter history.
- [x] Split the settings screen into Services, Roles, History and Generation with shared introductions and actual state summaries.

Contract cases: first migration, repeat migration, credential write failure, duplicate names with distinct IDs, selected-service deletion, invalid endpoint, malformed storage, history disabled/cancelled, UTF-8 chunks and terminal stream errors.

## Task 4 — Rich message tools and composer integration

**Files:** SettingsUI `NebulaMessageToolsController.swift`, `NebulaAiService.swift`, `NebulaDraftTranslation.swift`, `NebulaLiveTranslation.swift`; new Foundation `NebulaRichTextTransform.swift`; native ChatController and ChatTextInputPanelNode hooks.

- [x] Carry original attributed text/native entities through the tools initializer and apply closure. Keep String-only entry points as compatibility adapters.
- [x] Preserve UTF-16 entity ranges, custom emoji identifiers, links, code, spoilers and quotes through validated boundary tokens. Reject malformed or invented tokens; fall back to individual text segments without losing entities. Do not inject protocol examples into plain-text requests.
- [x] Provide Translate/Style/Correct modes, localized style presets and custom instruction, language selection and explicit Apply. Preserve text and styling when dismissing.
- [x] Place the own AI action in the native accessory position, including edit mode; use Telegram's action when the own editor is disabled where the pinned upstream supplies it. Avoid duplicate icons and preserve attachment hit targets.
- [x] Use the existing native glass material, safe-area spacing and animated selected segment. Exclude active recording/protected content and ignore stale results after text/account changes.

Tests: nested formatting, surrogate pairs, custom emoji, repeated text, literal marker input, interrupted requests, unchanged source on cancel and changed attributes with identical text.

## Task 5 — Browser blocking

**Files:** new Foundation `NebulaBrowserRules.swift`, SettingsUI `NebulaBrowserController.swift`, native BrowserUI WKWebView hook, shared catalog only for preferences with consumers.

- [x] Keep blocking off by default. Compile supported network rules into WKContentRuleList and apply only to the ordinary browser WebView.
- [x] Normalize domain exclusions, bound downloaded list size and parse only supported syntax. Preserve the last valid rules when an update fails.
- [x] Apply scoped cosmetic selectors after navigation; remove/rebuild on toggle or exclusion changes. Mini Apps and top-level navigations retain native behavior.
- [x] Add truthful supported-rule counts and update status; do not claim all EasyList syntax is supported.

Tests: wildcard/domain/exception precedence, invalid hostnames, malformed selectors, oversize input and old-rule retention after failure.

## Task 6 — Portable message, media, filter and profile consumers

**Files:** `NebulaBehaviorPreferences.swift`, `NebulaSettingsStore.swift`, `NebulaBehaviorController.swift`; new focused message/filter policy files and XCTest coverage; native ChatController/ChatMessageItem/MediaPlayer/PeerInfoScreen hooks as ordered patches.

- [ ] Audit each October 8 Android consumer against iOS native behavior before exposing it: forwarded date/edited marker/direct-share visibility, eligible delete defaults, reaction effects, voice queue progression, seek interval and pause-on-background.
- [x] Add reversible phrase filters with transliteration, whole-word matching, blocked peers and account-specific exceptions. Preserve native protected-content and pagination behavior.
- [x] Add local phone hiding and accurately labelled photo DC metadata, plus current profile/menu previews.
- [ ] Keep system-owned media controls native where a portable app override is unavailable; record these explicitly in PARITY.md.
- [ ] Audit recent icon packs, settings sync, retained copies and account limits against source; port missing portable consumers with data migration and cancellation tests rather than merely importing their flags.

## Task 7 — Verification and delivery

**Files:** `platform/ios/tools/check-bootstrap.py`, `platform/ios/PARITY.md`, `platform/ios/README.md`, `patches/ios/HOOKS.md`, `docs/USER-CHANGES.md` and generated mirrors.

- [x] Run `python platform/ios/tools/generate-overlay.py`, then `python platform/ios/tools/check-bootstrap.py` and `python scripts/check-settings-contract.py`. Expect all ordered patches and mirrored contracts to pass; preserve vendor.
- [x] Run XCTest and iOS SDK checks through `ios-bootstrap.yml`; fix actual compiler/type errors. Windows source parsing is not native compilation evidence.
- [ ] Build the complete arm64 IPA through `ios-ipa.yml`, verify exact source SHA, archive digest, bundle IDs, device architectures and extension packaging.
- [ ] Inspect simulator/device rendering if an accessible iOS runtime is available; otherwise record visual and physical-device acceptance as unverified. Never claim Android screenshots verify iOS.
- [ ] Update the inventory with implemented, excluded, native-equivalent and still-unverified status. Commit only this task's files and publish the authorized branches.

## Continuation — native link/delete defaults and legacy AI recovery

Native integration `37804869435` at `e116728` passed. Continue inline with the remaining portable consumers.

- [x] Extend `NebulaMessagePreferences.swift` and `NebulaMessageControlsController.swift` with `instant_view` (default true) and `delete_for_all` (default false). Test persistence and defaults in `NebulaMessagePreferencesTests.swift`.
- [x] Add ordered patch 0082: gate inline, explicit and resolved Instant View routes and open the original URL through the native browser when disabled. Initialize only the native eligible `.unsendPersonal` switch from `delete_for_all`; retain explicit actions, eligibility, undo and cancellation. Separate native delete-for-me/everyone actions stay separate.
- [x] Add `recoverableLegacy`/`recoverLegacy` to `NebulaAiServices.swift`. Expose incomplete legacy entries separately, copy keys only after an explicit valid Save, reject implicit credential reuse across providers/hosts, keep legacy data on failure and suppress recovery after successful restoration/deletion. Test these transitions in `NebulaAiServicesTests.swift`.
- [x] Add recovery rows and edit routing to `NebulaAiServicesController.swift`, keeping invalid records outside the active service list. Opening or cancelling recovery must not save, select or send anything.
- [ ] Regenerate the contract mirror, run patch/bootstrap and contract checks, then native/IPA CI. Record actual results and preserve physical-device acceptance as unverified.

## Execution notes

- 2026-10-08 audit: the existing `NebulaSettingsHero` discards `symbol` and `title`; current iOS choices default to checkmarks, and AI settings still contain a single legacy connection per provider. These are confirmed gaps, not inferred from screenshots.
- No camera implementation will be changed in this batch. The disconnected Android phone and its temporary Camera2 setting belong to the prior device check; this iOS work does not require reconnecting it.

- 2026-10-08 implementation checkpoints: `19ee949` shared introductions; `c983310` named services/roles/streaming (macOS bootstrap passed); `ff0c8a5` rich editor, draft/caption/native message translations. SDK check found a missing UIView override and `98adcd5` corrected it. The next bootstrap reached native-header fetching but failed on GitHub DNS, not source compilation. Full SettingsUI/PeerInfoScreen/folder compilation subsequently passed at `d231757` in run `37791304306`; later native changes and physical rendering are tracked separately below.
- Android follow-up: build 1000440 at `6b00a612013af329a0a8b2d08f5ab90f0182c4a0` downloaded and verified (signature, package, arm64, source/run). Editor sheet uses 30% dark/42% light tint and native glass selection tracking horizontal finger movement. Device gesture acceptance pending: USB phone absent.

## October 8 current implementation and remaining acceptance

- `4182fdf`: original forward date, edited pencil, direct share, voice queue, local profile phone hiding and photo DC.
- `a245079`: rich editor copy, reversible account filters, gallery seeking/background pause and message effects; bootstrap `37794800973` passed. A duplicate run found cancellation/test-counter concurrency; `7c070f2` retains actual in-flight slots and tests slow cancellation across remote/local changes.
- `6f50e52`: real native extracted-message preview, profile preview, ten accounts, bounded `.icons` importer and bundled Remix Outline. Bootstrap `37798863115` passed; full native `37798005200` found two compile errors, corrected in `e116728` below.
- `757b23b`: Premium sticker/reaction effects keep native state callbacks; known model reasoning capabilities are gated; corrupted icon indexes remain untouched. Local checks pass 81 patches / 139 upstream paths and all 78 shared catalog definitions. Bootstrap `37800985610` passed; intermediate IPA `37801031858` was superseded by the corrected revision.
- Browser cosmetic rules use WKContentRuleList's `css-display-none`, so navigation and rule removal are WebKit-owned; no injected script or Mini App hook is required.
- Camera engine/lens/recording/phone preview remains excluded. Hardware volume-button playback and keyboard scroll thresholds remain native iOS behavior.

- Model-catalog picker is implemented: explicit provider request, bounded pagination, searchable highlight-only choice, cancellation on edits/leave; manual model IDs remain available when a service does not support listing. Native compilation passed in `37804869435` and the full IPA passed in `37804784920`, source `e116728`.

The two portable gaps identified at that checkpoint were implemented in the continuation below; native and physical acceptance remain separate.

Continuation `cf2d0ce` adds Instant View browser routing, the eligible native unsend switch default and explicit recovery of incomplete legacy AI connections. All 82 patches / 141 paths and shared contract checks pass locally. Bootstrap `37817188656` at documentation-only successor `7f32211` passed all 119 XCTest cases, cancellation checks and SDK checks; duplicate queued runs were cancelled. Native `37816001440` and IPA `37816439420` verify this later runtime revision separately; the already successful IPA above does not include these three additions.

Downloaded and independently inspected IPA `37804784920`: source `e116728ea9a8009b074a44dfd8553db87d6388bf`, build 80, device arm64, bundle `app.nebulagram`, one Notification Service Extension. SHA-256 `8964e996cd9de92eb6d001c995753f9c788c4a3d3d50fa3c90e5dcd5bf631d29` matches the CI manifest. User signing, installation, APNs delivery and physical UI acceptance remain unverified.

Remaining sync audit: Android uses `#NebulaGramSettingsV1` messages, one installation UUID per writer, vector clocks (maximum 24 devices), 4,000 UTF-16 units per document, owner-only non-forwarded Saved Messages and explicit conflict choice. The iOS port must preserve this wire format and stop writes on malformed/newer snapshots; exporting a file to Saved Messages alone is not synchronization. Preserve local-only keys when applying portable settings.

Remaining verification:
- Exact final-SHA full arm64 IPA, archive digest/bundle/extension verification.
- Physical iOS return/foreground emoji replay, page spacing/Dynamic Type, glass drag, profile/menu preview dismissal, message/caption edit/apply and imported icon reopen tests. No iOS device/simulator is connected to this Windows workspace.
- Android build 1000440 glass drag/material on the authorized phone when USB returns. Do not infer gesture acceptance from compilation.

Provider capability references used for the optional-field guards: https://platform.claude.com/docs/en/build-with-claude/extended-thinking and https://ai.google.dev/gemini-api/docs/generate-content/thinking. Unknown model families keep server defaults rather than receiving unverified thinking parameters.

## Final portable consumers — cloud settings and audio

- [x] Add `NebulaCloudSettingsDocument.swift` and XCTest cases for the Android marker/version/clock/settings format, bounds, concurrent changes and deleted copies.
- [x] Add TelegramCore `NebulaCloudSettingsTransport.swift`: owner-only Saved Messages search, explicit network errors, bounded pagination, own-device edits and persisted send random IDs.
- [x] Add SettingsUI sync coordinator/controller: opt-in owner binding, no-backup installation ID, foreground cancellation, coalescing, explicit conflict choice and stale-result guards. Bind to the authorized account lifecycle and expose beside Files transfer.
- [x] Add bounded Gemini audio payload/response tests, native media menu action and themed transcript sheet. Requests require an explicit tap; protected, secret and expired media are excluded.
- [ ] Run contract/SDK/native/IPA checks; keep physical-device acceptance separate.

## Android steering — update settings sheet

User requested the reference's compact update panel in NebulaGram styling and selected the Nebula sign for its header.
- Add `NebulaUpdateSettingsSheet.java`: bounded scrollable bottom sheet, brand sign, installed version/build/ABI, last-check/status, opt-in beta filtering and existing automatic-check toggle. Manual check updates the sheet; a release row opens the existing verified download/install offer.
- Route About/search/settings links and `tg://update` to the sheet; preserve the full downloads fragment for legacy entry points.
- Extend `NebulaRelease` to accept explicit `-beta` / `-beta.N` version suffixes while legacy names remain stable; beta filtering must apply to search, cached availability and final commit. Keep package/signature/ABI verification unchanged.
- Check release policy tests and full Android APK build. Do not claim device visual acceptance while USB is absent.

Model catalog implementation: `NebulaAiModelPage.swift` validates entries/paging and has XCTest coverage; `NebulaAiModelCatalog.swift` uses an ephemeral no-redirect session with bounded bytes/pages and cancellation; `NebulaAiServicesController.swift` only reads an existing key when the edited service still matches its provider/host. No credentials or model choices are saved by merely opening the list.

- Native run `37798005200` exposed two real compile errors (missing Display import in filters and FileHandle.read requiring iOS 13.4). `e116728` adds the import, uses bounded InputStream reads compatible with the deployment target, and includes model selection. Bootstrap now SDK-typechecks the actual catalog transport alongside NebulaAiService. New native `37804869435` and IPA `37804784920` validate this revision; obsolete IPA run was cancelled because it contains the confirmed filter error.
- Android update sheet SDK compile passed against the local compiled Telegram classes and Android SDK. CI `37802506030` stopped at the old About-route assertion; `1adab52` updates that expectation. Full APK run `37803321318` passed: build 1000453, arm64-v8a, package/version/native libraries and matching signer verified locally. SHA-256: `a080888ebbef459135f74f91b9b56f6e17a299fa01f4682d829d67b3a4f72914`. The test emulator failed to boot online; no visual acceptance is claimed.

- Bootstrap `37804785180` at `e116728` passed 117 XCTest cases, cancellation/concurrency regression checks, all 81 patches and the existing real iOS SDK checks. Bootstrap `37804959701` at `2e210e3` also passed, including the actual model-catalog transport against the iOS SDK. Neither bootstrap nor module compilation proves physical UI acceptance.

## Current continuation and user corrections

- `03a08a0`: wire-compatible owner-only Saved Messages sync, persisted retry IDs, vector-clock conflicts, explicit deleted-copy recovery, per-installation/account opt-in and selected-account lifecycle. Bootstrap `37834428639` passed 122 contract tests and SDK/patch checks.
- `27c2cca`: explicit Gemini audio/video-message transcription (14 MB input / 2 MB response), cancelled on close/background, copied only by user action. Tests cover wire shape, v1beta/v1 text extraction, incomplete/error states, size and cancellation. API source: https://ai.google.dev/api/interactions-api-v1. Native and IPA runs must include the later Foundation.Timer qualification.
- User correction: `35a75b3` restores the Android AI editor to its pre-6b00a61 material/selection implementation. The update panel uses an animated Telegram rocket, channel icon instead of an arrow and an opaque modal surface. Current-source Android SDK compile, emoji lifecycle tests and updater checks pass; APK run `37835150189` is building. The immediately retracted request about colorful buttons/Premium emoji is not applied.
- No USB Android device is attached; there is no connected iOS device/simulator. Compilation is not visual or gesture acceptance.
