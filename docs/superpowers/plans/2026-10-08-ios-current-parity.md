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
- [ ] Root order: NebulaLink; General; Appearance; Navigation; Chats and tools; Folders; Privacy; About. Move transfer/browser into General, AI/tools/tasks into Chats and tools, support/community/build into About. Preserve search destinations.
- [x] Remove terminal full stops from short introduction descriptions; retain prose punctuation in explanatory text.

Acceptance: initial load and return show the same layout, 🧰 for General and 🔗 for NebulaLink, no substituted gear art, no emoji on ordinary settings-row icons.

## Task 2 — Choices and previews

**Files:** `NebulaChoiceController.swift`, `NebulaSettingsStyle.swift`, `NebulaBehaviorController.swift`, new `NebulaMessageMenuController.swift`, new `NebulaProfilePreview.swift` in SettingsUI; native ContextUI/PeerInfoScreen hooks exported after patch 0071.

- [x] Set every choice accessory to `.none`; apply selected background and `.selected` accessibility trait using the original choice index, including filtered language lists.
- [ ] Retain bounded centered presentation, keyboard avoidance and Dynamic Type scrolling. Honor Reduce Motion in opening/closing.
- [ ] Implement an interactive message/menu sample and profile sample driven by the same preference snapshot as native consumers. Native extracted content owns its visibility until dismissal completes, avoiding Android's duplicate/fade handoff defect.
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
- [ ] Place the own AI action in the native accessory position, including edit mode; use Telegram's action when the own editor is disabled where the pinned upstream supplies it. Avoid duplicate icons and preserve attachment hit targets.
- [ ] Use the existing native glass material, safe-area spacing and animated selected segment. Exclude active recording/protected content and ignore stale results after text/account changes.

Tests: nested formatting, surrogate pairs, custom emoji, repeated text, literal marker input, interrupted requests, unchanged source on cancel and changed attributes with identical text.

## Task 5 — Browser blocking

**Files:** new Foundation `NebulaBrowserRules.swift`, SettingsUI `NebulaBrowserController.swift`, native BrowserUI WKWebView hook, shared catalog only for preferences with consumers.

- [x] Keep blocking off by default. Compile supported network rules into WKContentRuleList and apply only to the ordinary browser WebView.
- [x] Normalize domain exclusions, bound downloaded list size and parse only supported syntax. Preserve the last valid rules when an update fails.
- [ ] Apply scoped cosmetic selectors after navigation; remove/rebuild on toggle or exclusion changes. Mini Apps and top-level navigations retain native behavior.
- [x] Add truthful supported-rule counts and update status; do not claim all EasyList syntax is supported.

Tests: wildcard/domain/exception precedence, invalid hostnames, malformed selectors, oversize input and old-rule retention after failure.

## Task 6 — Portable message, media, filter and profile consumers

**Files:** `NebulaBehaviorPreferences.swift`, `NebulaSettingsStore.swift`, `NebulaBehaviorController.swift`; new focused message/filter policy files and XCTest coverage; native ChatController/ChatMessageItem/MediaPlayer/PeerInfoScreen hooks as ordered patches.

- [ ] Audit each October 8 Android consumer against iOS native behavior before exposing it: forwarded date/edited marker/direct-share visibility, eligible delete defaults, reaction effects, voice queue progression, seek interval and pause-on-background.
- [ ] Add reversible phrase filters with transliteration, whole-word matching, blocked peers and account-specific exceptions. Preserve native protected-content and pagination behavior.
- [ ] Add local phone hiding and accurately labelled photo DC metadata, plus current profile/menu previews.
- [ ] Keep system-owned media controls native where a portable app override is unavailable; record these explicitly in PARITY.md.
- [ ] Audit recent icon packs, settings sync, retained copies and account limits against source; port missing portable consumers with data migration and cancellation tests rather than merely importing their flags.

## Task 7 — Verification and delivery

**Files:** `platform/ios/tools/check-bootstrap.py`, `platform/ios/PARITY.md`, `platform/ios/README.md`, `patches/ios/HOOKS.md`, `docs/USER-CHANGES.md` and generated mirrors.

- [ ] Run `python platform/ios/tools/generate-overlay.py`, then `python platform/ios/tools/check-bootstrap.py` and `python scripts/check-settings-contract.py`. Expect all ordered patches and mirrored contracts to pass; preserve vendor.
- [ ] Run XCTest and iOS SDK checks through `ios-bootstrap.yml`; fix actual compiler/type errors. Windows source parsing is not native compilation evidence.
- [ ] Build the complete arm64 IPA through `ios-ipa.yml`, verify exact source SHA, archive digest, bundle IDs, device architectures and extension packaging.
- [ ] Inspect simulator/device rendering if an accessible iOS runtime is available; otherwise record visual and physical-device acceptance as unverified. Never claim Android screenshots verify iOS.
- [ ] Update the inventory with implemented, excluded, native-equivalent and still-unverified status. Commit only this task's files and publish the authorized branches.

## Execution notes

- 2026-10-08 audit: the existing `NebulaSettingsHero` discards `symbol` and `title`; current iOS choices default to checkmarks, and AI settings still contain a single legacy connection per provider. These are confirmed gaps, not inferred from screenshots.
- No camera implementation will be changed in this batch. The disconnected Android phone and its temporary Camera2 setting belong to the prior device check; this iOS work does not require reconnecting it.

- 2026-10-08 implementation checkpoints: `19ee949` shared introductions; `c983310` named services/roles/streaming (macOS bootstrap passed); `ff0c8a5` rich editor, draft/caption/native message translations. SDK check found a missing UIView override and `98adcd5` corrected it. The next bootstrap reached native-header fetching but failed on GitHub DNS, not source compilation. Full SettingsUI build and physical iOS rendering remain unverified.
- Android follow-up: build 1000440 at `6b00a612013af329a0a8b2d08f5ab90f0182c4a0` downloaded and verified (signature, package, arm64, source/run). Editor sheet uses 30% dark/42% light tint and native glass selection tracking horizontal finger movement. Device gesture acceptance pending: USB phone absent.
