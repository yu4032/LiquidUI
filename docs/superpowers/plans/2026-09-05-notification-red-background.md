# Notification Red Background Implementation Plan

**Goal:** Make both HyperOS-style and native-style notification row backgrounds fully opaque red on exact target `systemui-001`.

## Phase 1 — Reverse-engineer both notification stacks

- [x] Decompile `NotificationViewWrapper.wrap(...)` and establish the HyperOS/native wrapper split.
- [x] Enumerate every `onReinflated()` declaration in the exact notification wrapper package.
- [x] Trace both stacks to the shared `ExpandableNotificationRow` outer container.
- [x] Decompile `NotificationBackgroundView#onDraw(Canvas)` to the final `mBackground.draw(canvas)` pixel authority.
- [x] Decode `notification_item_bg`, `notification_heads_up_bg`, `notification_heads_up_transparent_bg`, and `notification_material_bg` resources.

## Phase 2 — Retire intermediate hook architecture

- [x] Remove v1/v2 `setCustomBackground`, tint, and `updateBlurBg` production hooks.
- [x] Remove now-unused int/boolean argument-hook backends and their architecture tests.
- [x] Keep prior runtime evidence in the reverse-engineering record only.

## Phase 3 — Final-render implementation under TDD

- [x] Add `BeforeMethodHookBackend` and API101 adapter with first-hit diagnostics support.
- [x] Add failing contracts for final background draw and content-root clearing.
- [x] Hook `NotificationBackgroundView#onDraw(Canvas)` and recolor the actual current drawable immediately before draw.
- [x] Restrict final-draw mutation to a parent `ExpandableNotificationRow`.
- [x] Hook base + all three HyperOS `onReinflated()` declarations directly so correctness does not depend on a non-inlined `super` call.
- [x] Clear notification content-root background for both presentation stacks.
- [x] Preserve transactional registration/rollback.

## Phase 4 — Verification and device test

- [x] Run SDK-independent contract suite locally.
- [ ] Run `git diff --check` and architecture/static scans.
- [ ] Push a separate v3 correction commit to PR #2.
- [ ] Require GitHub Actions `testDebugUnitTest assembleDebug` success.
- [ ] Download the v3 APK artifact and perform device visual verification before merge.
