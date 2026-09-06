# iOS interface refinement implementation plan

**Goal:** Fix the reported Android selection, menu, profile, navigation and search defects, with glass menus gated by the existing iOS presentation preferences.

**Architecture:** Add shared menu presentation helpers to the Java overlay; preserve Telegram's blur pipeline and popup actions. Patch native interaction/layout code in the existing compiled tree and export a new incremental patch against a saved baseline. Preserve default styles and reduced effects.

**Tech Stack:** Android Java, VectorDrawable, Telegram blur3, Gradle, Python patch verification.

- [x] Add `NebulaMenuStyle.java`: `enabled()` follows iOS composer or floating header, `animated()` also requires liquid animations; provide coherent menu background/foreground and 24 dp radius.
- [x] Update `ActionBarPopupWindow.java` with one scale/fade animator on a fully sized background, instead of simultaneously expanding the background from zero and stretching the whole window. Keep all child rows visible at final state.
- [x] Update `ActionBarMenuItem.java`, `ItemOptions.java` and blur3 menu providers to share radii and theme-aware surface/text. Keep native blur and low-effects fallback.
- [x] In `NebulaDesignFragment.java`, inset the selection surface 6 dp with 24 dp corners and a visible accent stroke. Update common Cupertino vectors with consistent silhouettes.
- [x] In `NebulaControlsPreview.java`, show profile pattern only when explicitly selected; do not derive a default pattern from collectible status.
- [x] In `NebulaTabLens.java`, increase drag compression and travel stretch; use raw animation time for the stretch envelope so easing does not squash the entire deformation into the first frames.
- [x] In `DialogsActivity.java`, use `SEARCH_FIELD_HEIGHT` for the expanded search translation even when the resting search slot is hidden.
- [x] In `ChatAvatarContainer.java`, use ordinary profile navigation from the detached title capsule; retain avatar-specific animation for avatar taps. Check profile banner alpha continuity during native transitions.
- [x] In `ChatActivity.java`, calculate grouped message menu bounds from the actual bubble bounds rather than cell heights and their invisible padding.
- [x] Sync the overlay to `build/final-verify-0904/tree`, run the existing Java compile task and related layout checks, and verify the new patch applies after the complete existing series.

Physical device visual verification remains necessary for blur rendering and gesture feel; report this explicitly instead of treating compilation as visual validation.
