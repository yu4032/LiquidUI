# Notification Native Commit Authority Design

Date: 2026-09-08
Status: Approved for implementation planning
Branch: `fix/glass-blur-frame-latency`

## 1. Problem

LiquidUI currently renders notification glass in the shared `NotificationShadeWindowView` renderer while native notification rows live inside the notification stack subtree. Because the glass node is outside the row's native View hierarchy, row `translation`, `clip`, `alpha`, grouping and animation are not inherited automatically. The current experiment attempted to reconstruct that state from coarse callbacks (`updateExpandedHeight`, then `requestChildrenUpdate`) and caused several runtime failures: notification glass disappeared, appeared only after a material/style change, froze on screen, lagged native rows, and previously stacked at the top during Shade transitions.

HyperLight provides a useful architectural reference: it attaches its effects to native notification Views and anchors notification updates to the SystemUI ViewState commit path (`NotificationStackScrollLayout.applyCurrentState$1`) rather than to the outer Shade expansion fraction. LiquidUI must preserve its shared PassBlur/OES/Prismal renderer, but should adopt the same principle: native SystemUI commit points are authoritative; LiquidUI should synthesize only the cross-tree state that cannot be inherited automatically.

## 2. Goals

1. Notification glass appears without requiring Advanced Material or another style-triggered redraw.
2. Glass follows notification movement, scrolling, expansion, grouping, insertion/removal and Shade transitions without frozen overlays or one-frame geometry lag.
3. Stable notification state causes no permanent 120 Hz geometry polling.
4. Shared Shade renderer, PassBlur producer, Prismal shaders and Control Center behavior remain unchanged unless explicitly required by this design.
5. Native material remains visible until shared-renderer presentation is authorized; no red/opaque/empty intermediate background is introduced.
6. Changes are delivered in isolated, device-verifiable stages with explicit rollback points.

## 3. Non-goals

- Do not move notification rendering back to one renderer per row.
- Do not replace Prismal with `View.setRenderEffect()`.
- Do not reintroduce a permanent global `OnPreDrawListener`.
- Do not change visual glass parameters, Gaussian kernel quality, PassBlur source resolution, root Shade blur policy, or Control Center adapters as part of the timing refactor.
- Do not expand component coverage until notification timing is stable.

## 4. Authoritative SystemUI Signals

### 4.1 Stable notification state commit

The primary stable-state authority is:

`NotificationStackScrollLayout.applyCurrentState$1()`

LiquidUI hooks it **AFTER** the original method. At this point `ExpandableViewState.applyToView(...)` has already applied the native ViewState to rows. Geometry capture must happen only after native state has been committed.

`requestChildrenUpdate()` is a scheduling signal only. It must not be treated as a geometry commit point and must not directly publish a scene.

`NotificationPanelViewController.updateExpandedHeight()` and `mExpandedFraction` are not notification-node liveness authorities and are removed from notification geometry routing.

### 4.2 Native animation lifecycle

`NotificationStackScrollLayout.setAnimationRunning(boolean)` remains the animation lifecycle authority.

- `false -> true`: enter bounded animation tracking.
- While true: one NSSL-scoped pre-draw callback may refresh cross-tree geometry each display frame.
- `true -> false`: perform one final capture, remove the listener immediately, and return to idle.

This bounded tracking exists only because the shared root renderer cannot inherit per-row native transforms automatically.

### 4.3 Structural lifecycle

Row/material discovery continues to use existing row attach/detach, wrapper reinflation, child expansion and material-target hooks. These update membership/metadata but do not become independent frame clocks.

## 5. Notification Scene Tracker

Introduce a focused `NotificationSceneTracker` between notification runtime and shared session publication.

Responsibilities:

- Own the currently registered notification rows for a specific NSSL.
- Capture committed row state after native ViewState application.
- Build deterministic `NotificationNodeSnapshot` records.
- Diff current snapshots against the previous committed generation.
- Publish only when the effective notification scene changed.
- Remove stale rows atomically when detached or no longer visible.

A snapshot includes, at minimum:

- stable row/node identity;
- visible bounds in the shared renderer coordinate space;
- effective ancestor alpha excluding LiquidUI-suppressed material-target alpha;
- effective native clipping;
- corner radii after native clipping;
- expand-animation geometry when applicable;
- material/profile identity required by the renderer;
- attached/visible state.

Snapshot equality is semantic, not object-identity based. Repeated native commits that produce the same effective scene do not call `session.publish(...)` or `renderer.requestScene()`.

## 6. Domain Transform Separation

After stable commit authority is proven on device, extract page-level motion from per-row snapshots.

Introduce a notification-domain transform containing:

- page translation X/Y;
- page alpha;
- page/global clip rectangle.

Rows should use NSSL/domain-local coordinates where practical. Shade opening/closing and notification-page transitions then update one domain transform rather than rewriting every row's absolute screen coordinates.

This is a second-stage optimization and must not be introduced in the same device-validation step as the initial authority replacement.

## 7. Material Transaction Handoff

HyperLight's useful material principle is to synchronize with the native material transaction instead of posting a late replacement. LiquidUI should progressively move notification material ownership toward the `NotificationUtil.applyElementViewBlend(...)` transaction scope.

Design:

- BEFORE `applyElementViewBlend`: open a notification material transaction scope for the target row/view.
- Let SystemUI execute its native material setup.
- AFTER `applyElementViewBlend`: observe the final material target and ensure row registration/snapshot metadata are current.
- Keep existing `updateBackground$1` logic as a fallback target-discovery path until device validation proves the transaction route is complete.
- Suppress native background only after shared renderer presentation authorization, as today.

This stage is intentionally separated from the geometry-authority change.

## 8. Renderer and Blur Generations

Preserve separate generations:

- `SourceGeneration`: PassBlur/OES backdrop content changed.
- `NodeGeneration`: one or more notification snapshots changed.
- `DomainGeneration`: notification page transform/clip/alpha changed.

Rules:

- New source generation may rebuild cached backdrop blur.
- Node-only or domain-only changes must reuse the current backdrop blur texture.
- No effective generation change means no draw request.
- Scene requests remain coalesced by the shared renderer coordinator; draw frequency must never self-loop above display refresh rate.

## 9. Implementation Stages

### Stage 1 — Native commit authority

Replace notification geometry routing with `applyCurrentState$1 AFTER` plus bounded `setAnimationRunning` tracking. Remove `requestChildrenUpdate` as a publication trigger and remove residual panel-expansion routing.

Device gate:

- notification glass appears without Advanced Material;
- opening/closing does not stack glass at the top;
- scrolling and expansion do not freeze glass;
- Control Center remains unchanged.

### Stage 2 — Snapshot/diff publication

Add `NotificationSceneTracker` and semantic snapshot equality. Same committed state must not republish.

Device/perf gate:

- stable Shade produces no continuous notification scene publishes;
- no increase in `drawFps` above display refresh;
- no new skipped-frame burst attributable to notification aggregation.

### Stage 3 — Domain transform

Move page-level translation/alpha/clip into one domain transform while preserving per-row local state.

Device gate:

- Shade open/close and notification/control-center page transition remain visually synchronized;
- transition cost scales approximately with one domain update rather than N row rebuilds.

### Stage 4 — Material transaction synchronization

Add `applyElementViewBlend` transaction scope and reduce dependence on late background hooks.

Device gate:

- no native-background flash;
- no red/opaque fallback flash;
- style changes do not create or freeze geometry;
- presentation revocation restores native material correctly.

## 10. Tests

Each stage follows RED -> minimal production fix -> GREEN.

Required SDK-independent contracts include:

- `applyCurrentState$1` is the stable commit authority;
- `requestChildrenUpdate` cannot directly publish notification scene;
- `updateExpandedHeight`/fraction cannot gate notification node liveness;
- animation tracking is installed only while `setAnimationRunning(true)`;
- animation end removes tracking and forces a final capture;
- identical snapshots cannot publish a new scene generation;
- native material-target suppression cannot affect node visibility calculation;
- Control Center/shared renderer ownership remains unchanged.

Android build verification remains `testDebugUnitTest assembleDebug` before every device APK.

## 11. Diagnostics

Add bounded structured diagnostics during development, removable or rate-limited after validation:

- commit authority hit count;
- animation tracking start/end count;
- registered rows;
- captured nodes;
- dropped node reason (`detached`, `not-shown`, `empty-visible-rect`, `ancestor-alpha`, `outside-viewport`);
- snapshot changed/unchanged counts;
- scene publish count;
- material authorization/revocation count.

A single device log session must be able to distinguish registration failure, capture failure, scene publication failure and presentation failure without guessing from visuals.

## 12. Rollback Boundaries

Every implementation stage is independently revertible. Stage 1 must not depend on Stage 2-4. Stage 2 must not change visual material semantics. Stage 3 must not change source/backdrop generation. Stage 4 must not alter geometry authority.

Do not merge to `main` until Stage 1 is device-validated. Later stages each require separate device validation before merge or continuation.

## 13. Acceptance Criteria

The notification path is considered timing-correct only when all of the following hold on the target HyperOS build:

- notification glass is visible on first normal Shade open;
- no Advanced Material toggle is required to make it appear;
- no frozen glass remains during/after scroll, expansion or close;
- no top-of-screen stacking during open/close;
- native row and glass movement are visually synchronous;
- page switch preserves correct ownership and clipping;
- stable state has no permanent notification pre-draw polling;
- draw scheduling remains bounded by display refresh;
- source-poor periods do not rerun Gaussian blur solely because geometry changed;
- Control Center behavior remains device-equivalent to the validated shared-renderer baseline.
