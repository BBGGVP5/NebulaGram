# Responsive menus, compact introductions and native AI editor control

**Goal:** Restore the preferred lively menu motion, make settings introductions more compact, resolve the remaining General emoji problem, and use one native-position AI control for both composing and editing.

**Architecture:** Keep the circle seed and stable popup geometry, but restore the earlier FlClash spring response with faster timing. Retain Telegram emoji receivers and select animated documents rather than static candidates. Keep the native AIEditorAlert entry point, generation routing, edit/apply/send callbacks and rich drafts; use the existing explicit `replace_editor` preference to switch its icon/provider. Remove the duplicate inline composer shortcut while retaining tools through the native control's long press. Work inline; preserve the modified vendor checkout.

- [x] Inspect the user's 14-second video at 20 fps; compare with the earlier spring implementation and tune opening/closing without reintroducing final shifts or source-shape morphs.
- [x] Compact `NebulaSettingsHero` emoji/spacing/title, remove terminal dots from descriptions only, and preserve title collapse and accessibility.
- [x] Make General resolve animated emoji candidates before stock fallback; cover static-before-animated pack candidates and selector aliases.
- [x] Add a scoped native composer patch: one AI control at Telegram's position, availability for editing and Nebula mode, icon/provider swap via `replace_editor`, automatic native fallback when disabled, and cancellation-safe visibility.
- [x] Retain native apply/send/edit and rich-draft behavior; retain live translation pause/resume and access to tools/settings without inline duplicate controls.
- [ ] Run behavior/state/layout/API checks, reconstruct the pinned patch series, publish and verify a fresh APK. Physical-device validation remains dependent on an attached device.

Verification before publication: all 87 required checks pass; 179 patches reconstruct against the pinned Telegram 12.10.6 source. The original independent FlClash fixture matches 864 growth/travel/focus frames; timings are 460ms open and 160–260ms close, with a fixed circular seed. Native-control fixtures cover compose/edit/rich/secret/pause states, legacy opt-in and explicit-off provider precedence, nested modal translation suspension, description punctuation and caption spacing. Emoji fixtures cover static-before-animated documents, alternative codes, cache loading and page return. Android SDK checks and title-collapse/layout checks pass. No ADB device is attached. Existing Nebula generation support for rich articles remains limited as documented; native rich-draft callbacks are retained.
