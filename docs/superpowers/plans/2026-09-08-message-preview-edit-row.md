# Message preview and media edit row implementation plan

**Goal:** Render long message content after lifting it out of the list and keep media edit actions inside the composer.
**Architecture:** Draw selected cells with a temporary full-text-block scope, restore culling state afterward, map viewport clipping to the lifted coordinate space. Apply the composer inset to both reply and media-edit rows. Package native changes as 0067.

- [x] Add regression tests for scoped text-block rendering/restoration and lifted clipping.
- [x] Implement preview drawing and improve available preview space for long messages.
- [x] Inset and constrain media edit controls; retain native layout when the glass composer is off.
- [x] Compile, apply full patch series and run regression suite.
- [ ] Validate on connected phone if available; report missing device coverage explicitly.

Verification: Java compilation succeeded; bytecode contains the new preview scope, clip helper and edit-row methods. All 21 workflow regression checks passed. Patch 0067 applies after the verified 61-patch baseline and reconstructs all three modified native files. Device acceptance remains pending because ADB lists no device.
