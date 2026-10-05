# exteraGram-inspired features implementation plan

Implementation proceeds inline in this chat. Checkboxes distinguish finished work from remaining publication and build validation.

**Goal:** Add working AI services and roles, the requested settings presentation, importable messenger icon packs, browser ad blocking and missing chat preferences using the user's screenshots and release notes as the reference.

**Architecture:** Retain Nebula's provider transport, encrypted credentials, native Telegram messages and resource boundary. Each subsystem owns its state and settings screen. Native patches connect browser requests, navigation, messenger icons and chat preferences; existing translation and retention engines remain the starting point.

**Tech Stack:** Java, Android views, Telegram RLottie animated emoji, AndroidX spring animation, JSON/ZIP icon-pack format, immutable browser filter snapshots, deterministic JVM checks and the pinned Telegram Android patch series.

---

Execute inline in the authorized chat. Preserve the modified Android vendor checkout. Android build `1000372` and iOS build `75` have already been delivered; neither contains this new feature set. No unrequested Python plugin platform or unrelated release-note optimization claims.

The reference clone at `build/exteragram-12106-reference` is pinned to `1ef789d2b3098d35f361988b86286bdfa3299dab`; it is an unofficial reconstructed source, not the maintained official repository. Use it to inspect behavior and file formats. Implement focused Nebula classes and preserve attribution for any reused licensed assets.

## Project 1: AI and settings presentation

**Files:** new `NebulaAiSettingsFragment.java`, `NebulaAiServices.java`, `NebulaAiServicesFragment.java`, `NebulaAiRoles.java`, `NebulaAiRolesFragment.java`, `NebulaAiOptions.java`, `NebulaAnimatedEmoji.java` under `platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/`; modify `NebulaAiFragment.java`, `NebulaAiClient.java`, `NebulaAiSecrets.java`, `NebulaAiChatView.java`, `NebulaSettingsHero.java`, `NebulaSettingsFragment.java`, `NebulaSettingsSearch.java`; checks `scripts/check-ai-services.py`, `scripts/check-ai-protocol.py`, `scripts/check-ai-chat.py`.

- [x] Add a grouped AI settings screen with Services, Roles, History, Telegram editor/summaries and generation options. Keep the ordinary chat screen directly accessible. Render robot/masks/key using Telegram's `MediaDataController.getEmojiAnimatedSticker`, `RLottieImageView.setAnimation(document, 112, 112)` and attached-only playback; use a native static emoji while the standard pack loads. Remove observers and stop playback on detach.
- [x] Store named service metadata separately from keys. Selecting a service copies its provider/model/endpoint into the existing active connection only after its encrypted key is available. Legacy connection migrates once without dropping its provider or credentials. Two services of the same provider keep different secrets; deleting one never deletes another's key. Add OpenRouter and Perplexity with their real compatible API endpoints while preserving provider IDs 0–4.
- [x] Add Assistant, Summarizer and Proofreader roles plus editable custom name/prompt/emoji. Capture the selected prompt with each request. Keep AI history local and provide its existing viewer.
- [x] Add real SSE response streaming for supported remote APIs, response-only/quote insertion preferences and generation temperature/reasoning options. Validate event types and final/error events; do not display reasoning as the answer or save a canceled partial response. Nano's UI describes supported controls accurately.
- [ ] Compile and execute fixtures that select/migrate/delete services, switch two keys of one provider, parse split UTF-8 SSE events, propagate provider errors and cancel streaming. Run `python scripts/check-ai-services.py`, `python scripts/check-ai-protocol.py`, `python scripts/check-ai-chat.py` and the Android SDK UI typecheck.

Role application is explicit, using captured source data:

```java
String rolePrompt = NebulaAiRoles.prompt();
String prompt = rolePrompt.isEmpty() ? configuredPrompt : rolePrompt;
```

Generation defaults remain deterministic when a caller has not opted into chat options; translation transport never inherits a conversational role.

## Project 2: Messenger icon packs

**Files:** new `NebulaIconPackStore.java`, `NebulaIconPacksFragment.java` and a bounded archive reader; modify `NebulaIconResources.java`, `NebulaIcons.java`, `NebulaSectionFragment.java`; resources under `platform/android/overlay/TMessagesProj/src/main/res/drawable/`; checks `scripts/check-icon-packs.py` and existing settings/API checks.

- [x] Support the `.icons` ZIP format with root `metadata.json`, `packName`, `packId`, `author`, `version` and an `icons` map of drawable resource names to image paths. Accept bounded SVG/PNG/WebP images, validate canonical paths, duplicate entries, expanded sizes and image dimensions before installation. Stage files in an app-private temporary directory and publish atomically; reject partial archives.
- [x] Add pack preview, import via Android document picker, selection, export and deletion. Importing does not automatically replace the active pack. Preserve Standard/Cupertino/Solar and add a consistent additional outline set with asset attribution.
- [x] Resolve imported images at the existing `NebulaIconResources` boundary, including native chat/list/settings/attachment icons. Cache immutable drawable states by selected pack and density, clone mutable drawable instances and preserve native intrinsic size/hit rectangles. Unknown or unmapped drawable names fall back to the native icon.
- [ ] Execute fixtures for a valid pack, traversal/absolute paths, duplicates, oversized SVG/image/ZIP entries, missing icon paths, replacement rollback and switching packs without a stale drawable cache. Compile the actual Resources overrides against Android SDK; verify native resource names against the pinned source.

The accepted metadata has a concrete interoperable shape:

```json
{"schemaVersion":1,"packName":"Example","packId":"example","author":"User","version":"1.0","icons":{"msg_search":"icons/search.svg"}}
```

## Project 3: Browser and translator settings

**Files:** new `NebulaAdBlockRules.java`, `NebulaBrowserAdBlock.java`, `NebulaBrowserSettingsFragment.java`; new native patch for `org/telegram/ui/web/BotWebViewContainer.java`; extend translation settings/search and manual translation routing; checks `scripts/check-browser-adblock.py` and translation regressions.

- [x] Add a browser-only blocker with network rules, cosmetic hiding, filter refresh and per-site exclusions. Keep Mini Apps, main-frame navigation and TON proxy behavior native. Build immutable snapshots off the UI thread; never fetch a filter list or parse it for every request.
- [x] Match host suffixes at label boundaries, honor allow rules and domain/third-party restrictions, and ignore unsupported filter syntax safely. Cosmetic rules become CSS text in a fixed style element, never arbitrary scriptlets. Bound list size, selector size and matching work.
- [x] Hook both native request overloads before ordinary browser subresource loads, reset page scope on navigation and inject bounded CSS at page finish. Return a typed empty `WebResourceResponse` for blocked subresources. Expose global enable, site exclusions, list preparation/status and refresh in settings.
- [x] Route manual message/draft/caption translation through the explicitly selected translation engine. Keep user-selected AI and the existing fast local engine available; add third-party translation choices only with their actual transport and visible consent/settings. Never silently select Telegram or another service after failure.
- [ ] Run fixtures for `ads.example.org` versus `notads.example.org`, allow-listed sites, first-party restrictions, main-frame and Mini App exclusions, bounded CSS and refresh rollback. Run translation regression and real Android API checks.

Native browser integration is scoped by its existing constructor flag:

```java
if (!bot) {
    WebResourceResponse blocked = nebulaAdBlock.intercept(request);
    if (blocked != null) return blocked;
}
```

## Project 4: Navigation, chat preferences and retention

**Files:** `NebulaTransitions.java`, `NebulaFeatureControls.java`, new focused preference helpers, `NebulaPrivacyFragment.java`, native patches for `ActionBarLayout.java`, chat/composer cells and notifications; shared presentation catalog and generated settings contract; targeted regression scripts.

- [x] Match reference AOSP/full-width navigation and spring navigation: foreground travel equals content width, background parallax is 0.35 of width, spring scale is 0.85→1, stiffness 900 and damping ratio 1. Preserve preview, swipe/predictive back, reduced motion and cancellation/reset behavior. Use actual `SpringAnimation` rather than an overshoot label on the old fade.
- [x] Compare release-note preferences against existing behavior. Keep already-implemented wide posts, Gemini Nano, Responses API, presentation controls and counters. Add missing executable options for inline arithmetic, sticker time, channel forward count, per-account notification reception and unmuted folder counts; add configurable swipe actions where the native gesture dispatch supports them. Document each connected hook instead of counting a displayed switch as implementation.
- [x] Make the existing expiring-message retention setting discoverable. Enabling it must also enable its required archive master setting; exercise timer and view-once deletion paths for already-received data. Preserve account boundaries, encrypted local index and explicit opt-in defaults.
- [x] Register new transferable presentation settings in `shared/settings/catalog.json` and regenerate contract files. Keep credentials, browsing state and account-specific retention out of transfer. Run `python scripts/generate-settings-contract.py --check`, relevant executable feature checks, complete patch reconstruction and all required CI regressions.

Spring parameters are taken from the inspected reference:

```java
new SpringAnimation(new FloatValueHolder(0f))
    .setSpring(new SpringForce(1000f).setStiffness(900f).setDampingRatio(1f));
```

## Publication

- [x] Update `docs/USER-CHANGES.md` with actual final behavior and platform differences. Preserve external asset licenses and source attribution.
- [ ] Publish only this task's files to the authorized branch/main, complete Android CI and verify its fresh APK source SHA, digest, package and release certificate. Keep vendor changes untouched.
- [ ] Provide the new APK, the exact settings locations and actual device-testing limits. Keep the already-built iOS IPA 75 available without labeling this later Android feature set as included in it.

Validation so far: JVM service/protocol/chat fixtures, archive and browser fixtures, 84 existing regressions (settings geometry repaired and retested; account-slot native syntax checked with NDK clang), 13 new helper classes typechecked against Android SDK 37 and cached native signatures. All 177 patches reconstruct against the pinned source. The fresh full Android CI compile and device visual testing remain separate checks; the attached phone is not visible to ADB.
