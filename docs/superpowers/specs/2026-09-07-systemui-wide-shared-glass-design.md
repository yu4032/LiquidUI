# SystemUI-wide Shared Glass Design

Date: 2026-09-07
Target: `systemui-001` (`com.android.systemui`, version `16.03.251211.r`, SDK 36 / API 101 profile)
Baseline: `c89ed42a1c383e793edfe1547c7deb958ef59db6`
Branch: `feature/systemui-wide-shared-glass`
Status: user-approved architecture; pending written-spec review

## Decision

LiquidUI will implement SystemUI-wide glass as **one shared session per Window plus one
process-global shared core**.

The process-global core owns the implementation that must be identical everywhere:

- display and `SurfaceTexture` coordinate transforms;
- the Prismal optical pipeline and shader/program cache;
- frame-request coalescing and render scheduling;
- material profiles and their validated optical parameters;
- common lifecycle, freshness, diagnostics, and failure policy.

Each Window owns one session. A session owns that Window's source and output resources and batches
all glass backgrounds in the Window into one scene. Individual SystemUI components contribute only
immutable geometry and material metadata. They never create or retain a PassBlur producer,
`SurfaceTexture`, OES texture, EGL context, renderer, render thread, or output `TextureView`.

SystemUI continues to draw text, icons, clocks, controls, and other foreground content natively.
LiquidUI replaces only background/container material.

## Goals

1. Extend real PassBlur -> OES -> Prismal refraction from notifications to every audited SystemUI
   background/container surface.
2. Make rotation and sampling correctness a single `DisplayTransform` responsibility so reverse
   portrait, ordinary portrait, and both landscape rotations cannot diverge by component.
3. Keep producer, EGL, renderer, and render-thread counts proportional to active Windows, not to
   component count.
4. Preserve native SystemUI layout, input, animation, text, icon, clock, and accessibility behavior.
5. Fail closed to the exact native/fallback material whenever the current Window session cannot
   prove a fresh, successfully swapped glass frame.
6. Onboard component families incrementally behind exact-version reverse-engineered adapters.

## Non-goals

- No glass effect on glyphs, text, icons, clock faces, or semantic foreground content.
- No per-component or per-card GPU pipeline.
- No CPU capture/readback, screenshot, PixelCopy, ImageReader, Bitmap, MediaProjection, or
  `glReadPixels` path.
- No guessed vendor callbacks, class names, resource ids, or fixed rotation offsets.
- No layout replacement, notification/control behavior rewrite, or input interception.
- No cross-process sharing with Launcher. Launcher glass remains a separate subsystem.
- No simultaneous activation of every component adapter before each target has an audited draw and
  material authority.

## Alternatives considered

### Selected: per-Window session plus process-global core

This preserves the true ownership boundary: a PassBlur source is attached to a concrete ViewRoot /
root `SurfaceControl`, while transforms, optical behavior, scheduling, and profiles are global
policy. It removes duplicated renderer logic without pretending that unrelated Windows share one
BufferQueue lifetime.

### Rejected: one full pipeline per component

This would multiply OES consumers, EGL work, output surfaces, lifecycle races, frame callbacks, and
rotation fixes with component count. It also recreates the notification prototype's current
coupling in every subsystem.

### Rejected: one producer and one session for the entire SystemUI process

Notification shade, control center, volume, keyguard/status bar, and floating/misc surfaces may use
different ViewRoots and root `SurfaceControl` generations. A single producer cannot correctly
represent all those endpoint lifetimes or z-order domains. Process-global sharing therefore stops
at the core; source/output ownership remains per Window.

## Scope and Window ownership

The first complete SystemUI-wide architecture supports these session groups. Exact target classes
and insertion points are admitted only after reverse-engineering evidence is committed for the
target build.

| Window/session group | Components batched into the session |
| --- | --- |
| Notification shade | notification rows, grouped notification backgrounds, shade media backgrounds, other audited shade container materials |
| Control center | tiles, tile groups, brightness sliders, control-center media, audited panel/container materials |
| Volume | volume panel, stream rows, sliders, buttons, expanded subpanels |
| Keyguard | lock-screen cards, affordance/container backgrounds, audited keyguard floating surfaces |
| Status bar | status-bar-owned transient backgrounds whose ViewRoot is distinct from keyguard |
| Dynamic island / misc | each distinct floating Window gets one session; all audited backgrounds in that Window share it |

If two named feature groups resolve to the same live ViewRoot, `WindowGlassRegistry` returns the same
session. If they resolve to different ViewRoots, they cannot share a session even if they appear in
the same visual area.

## Architecture

```text
SystemUiGlassCore (one per SystemUI process)
  |- DisplayTransformEngine
  |- PrismalEngine + shader/program cache
  |- FrameCoordinator
  |- MaterialProfileRegistry
  |- WindowGlassRegistry
  |
  `- WindowGlassSession (one per live ViewRoot)
       |- PassBlurEndpoint (one producer generation at a time)
       |- OES input + session-local normalized backdrop resources
       |- output surface/host for this Window
       |- GlassSceneState (all component nodes in this Window)
       `- ComponentAdapter registrations (geometry/material only)
```

`SystemUiGlassCore` is created once in the SystemUI composition root after exact target resolution.
All feature hooks receive the same core instance. The core must not discover or hook vendor classes;
adapters resolve their exact target contracts and register with the core.

### Global core responsibilities

`SystemUiGlassCore` owns:

- one GL render thread and EGL display/context for SystemUI-wide glass where the device driver and
  output surface model permit it;
- shader compilation, program caching, immutable mesh data, and Prismal material definitions;
- a stateless or explicitly session-bound `PrismalEngine` API so mutable FBO/texture state never
  leaks between Window sessions;
- `DisplayTransformEngine`, the only code allowed to interpret rotation, root/window geometry,
  producer buffer geometry, crop, or the `SurfaceTexture` transform matrix;
- `FrameCoordinator`, which fairly coalesces source-frame and scene changes across active sessions;
- the authoritative `MaterialProfileRegistry`;
- process-wide ownership diagnostics and invariant checks.

If a single EGL context cannot safely render every output Window on the target driver, the fallback
is shared EGL context group plus one context per active Window on the same core render thread. This
is an implementation fallback, not permission to create per-component contexts.

### Per-Window session responsibilities

`WindowGlassSession` is keyed by stable ViewRoot identity plus display id and owns:

- one output host/surface placed inside that Window's verified background-to-foreground layer gap;
- one current `PassBlurEndpoint`, including producer generation, OES `SurfaceTexture`/`Surface`,
  root `SurfaceControl` identity, and fresh-frame state;
- session-local normalized backdrop and scene render targets;
- one immutable `GlassSceneSnapshot` containing every visible glass node in that Window;
- adapter registrations and per-node material handoff state;
- presentation activation/revocation for that Window;
- Window-local lifecycle and periodic performance diagnostics.

A session never owns a private render thread, material profile definition, rotation policy, or
shader implementation.

### Component adapter responsibilities

An adapter is the only feature-specific layer. It may publish:

- stable node id;
- bounds in Window coordinates;
- clip rectangle;
- four independent corner radii;
- opacity;
- z-order within the shared glass scene;
- material profile id;
- visibility and lifecycle generation;
- references needed solely to suppress and restore that component's native background material.

An adapter may not publish raw optical uniforms or orientation offsets. It may not retain or call
GL objects. It may not create output layers or producers. It must publish immutable snapshots; the
render thread never retains SystemUI `View` objects.

## Scene and z-order model

All visible component backgrounds in one Window are rendered in one ordered scene. Nodes are sorted
by the native visual order captured by the adapter, with stable node id as a deterministic tie
breaker. The shared output layer is inserted below native foreground content but above the Window's
captured backdrop.

Where native backgrounds and foreground content are drawn by the same `View`, the adapter must use
an audited material-suppression hook that removes only the background pixels. It must not hide the
View, reduce foreground alpha, or replace the content hierarchy.

Overlapping translucent nodes are composited in z-order inside the shared scene. There is one final
output composite and one swap per coalesced Window render.

## Pixel pipeline

```text
Window root SurfaceControl
  -> SetPassBlurSurface(root, session producer)
  -> BufferQueue / SurfaceTexture external OES
  -> DisplayTransformEngine(root UV, Window geometry, config/display rotation,
                            producer geometry, SurfaceTexture matrix)
  -> one normalized 2D backdrop for the Window
  -> PrismalEngine.prepareBackdrop() once for a newly consumed source frame
  -> draw every GlassNode in z-order using its material profile
  -> one transparent optical scene
  -> one composite into the Window output host
  -> one successful EGL swap
```

No pixel data crosses into CPU memory.

The PassBlur source scale is session policy, never component policy. Initial migration preserves the
currently requested full-resolution scale (`1.0`). The actual producer buffer dimensions and
`SurfaceTexture` matrix remain authoritative: the transform engine must not infer scale from a
requested value and must never invert valid crop/scale merely to make the matrix look normalized.

## DisplayTransform contract

`DisplayTransformEngine` replaces notification-specific `Miuix307BackdropMapping` ownership. Every
session supplies one immutable `DisplayTransformSnapshot` containing:

- display id and current `Display.getRotation()`;
- configuration rotation when exposed by the target;
- natural display size and current logical display bounds;
- Window bounds, surface insets, ViewRoot buffer size, and output-host geometry;
- producer buffer width/height and source crop metadata when available;
- the current 4x4 `SurfaceTexture` transform matrix;
- root-surface generation and producer generation.

The engine returns one composed matrix from Window-space UV to OES sample UV. Transform composition
must be expressed as matrix operations over declared coordinate spaces. Hard-coded per-orientation
pixel offsets and per-component corrections are forbidden.

Tests cover all four display rotations, including ordinary and reverse portrait. Each case maps the
four Window corners and center through a synthetic `SurfaceTexture` crop/flip matrix. A production
transform snapshot is valid only when its display, root, and producer generations match the session.

The currently observed behavior—two portrait directions producing different lateral offsets—is a
shared-core defect and remains a release blocker for the transform engine. No adapter may compensate
for it locally.

## Material profiles

Components select a semantic profile, not numeric uniforms. Initial profiles are:

- `CARD`: notifications, media cards, lock-screen cards;
- `TILE`: control-center tiles and compact buttons;
- `SLIDER`: brightness and volume tracks/thumb containers;
- `PANEL`: large sheet/panel backgrounds;
- `FLOATING`: dynamic-island and transient floating containers.

`MaterialProfileRegistry` owns tint, thickness, index of refraction, displacement, chromatic
dispersion, blur, highlights, and press-state variants. Profiles are immutable for a rendered frame
and versioned so profile changes invalidate affected sessions once, not once per node.

The initial extraction preserves the notification-validated refraction profile, zero extra sampling
inset, zero parallax, and disabled LiquidUI bloom. New visual tuning is deferred until ownership,
transform, and performance invariants pass on device.

## Frame scheduling and performance

`FrameCoordinator` uses latest-state coalescing:

1. a producer callback marks a source frame pending;
2. adapter/pre-draw publication marks a scene generation pending;
3. at most one render drain is queued per Window session;
4. the drain consumes the newest available source frame and newest scene snapshot;
5. additional events arriving during rendering set pending bits and cause at most one subsequent
   drain;
6. there is no adapter-owned `postOnAnimation()` delay and no unbounded render-handler queue.

For a scene-only update, a session reuses the current-generation prepared backdrop. For a new source
frame, normalization and `prepareBackdrop()` run once regardless of node count. Component count may
increase draw instances, but never producer callbacks, normalization passes, backdrop-preparation
passes, EGL contexts, render threads, or output swaps per visual frame.

Periodic diagnostics report per session: producer fps, consumed-source fps, scene-publication fps,
draw fps, swap fps, coalesced request count, node count, and longest pending age. Logs are rate
limited and never emitted once per node per frame.

## Lifecycle and freshness

PassBlur endpoint lifetime follows the Window root `SurfaceControl`, not Java `Surface.isValid()`
alone.

### Initial activation

A Window remains on native material until the current producer generation has:

1. bound to the current root `SurfaceControl` generation;
2. delivered a frame;
3. completed `updateTexImage()`;
4. produced a valid transform snapshot;
5. prepared the current backdrop;
6. rendered a scene containing the target node;
7. completed a successful output swap.

The activation token contains session id, root generation, producer generation, scene generation,
profile version, rendered node ids, and swap sequence.

### Per-node material handoff

Window-level activation does not automatically suppress every native background. A newly attached or
changed node retains native material until an accepted activation token proves that exact node id and
its current lifecycle generation were included in a successful swap. This prevents a newly visible
tile/card from becoming transparent one frame before its glass geometry exists.

Removal restores or releases only the removed node's material state. Session failure restores all
nodes owned by that session.

### Root rollover and teardown

Root `surfaceDestroyed` immediately invalidates the endpoint, revokes presentation, hides the output,
and restores native material before retiring the old producer on the GL thread. The old producer is
never rebound. A new root creates a new endpoint generation and must pass the full freshness barrier.

When the last adapter unregisters, the session pauses source updates and becomes eligible for
deterministic teardown. Process-global core resources remain alive while SystemUI is alive or until
the module explicitly shuts them down.

## Failure handling

Failures are isolated per Window session whenever possible.

- Adapter contract failure: reject that adapter/node and retain its native material.
- Invalid geometry/transform snapshot: skip presentation for the affected generation; do not sample
  outside validated OES coordinates.
- Bind or fresh-frame failure: keep the Window in native fallback.
- EGL output or session-local GL resource failure: revoke and recreate only that Window session.
- Shared context/program corruption: revoke every session, restore all native materials, rebuild the
  core once, and fail closed if rebuilding fails.
- Target-profile mismatch or missing reverse-engineered contract: do not install that adapter.

No fixed delay, retry sleep, stale-frame presentation, or magic offset is a correctness mechanism.

## State model

Each Window session distinguishes at least:

- `NATIVE_FALLBACK`;
- `SOURCE_BOUND`;
- `SOURCE_FRESH`;
- `GLASS_ACTIVE`;
- `SOURCE_LOST`;
- `RECOVERING`;
- `TERMINAL_FAILURE`;
- `DETACHED`.

Each registered node also distinguishes `NATIVE`, `PENDING_GLASS`, `GLASS_PRESENTED`, and
`RESTORING`. Pure policy objects own transitions; hooks and GL callbacks submit events rather than
mutating presentation state ad hoc.

## Reverse-engineering admission gate

Before a new component adapter is enabled for `systemui-001`, its evidence document must identify:

1. exact Window/ViewRoot and root `SurfaceControl` authority;
2. background draw/material authority;
3. foreground draw order and a safe output insertion point;
4. bounds, clipping, four-corner radius, opacity, visibility, and z-order sources;
5. attach/detach/reinflate/configuration lifecycle;
6. native fallback suppression and exact restoration path;
7. structural probes that fail closed on a mismatched build.

An adapter with incomplete evidence remains disabled. Similar names or behavior across HyperOS builds
do not satisfy this gate.

## Migration phases

### Phase 0: core extraction with no visual expansion

- introduce `SystemUiGlassCore`, `WindowGlassRegistry`, `WindowGlassSession`, generic scene/node
  contracts, `DisplayTransformEngine`, `FrameCoordinator`, and `MaterialProfileRegistry`;
- move generic producer/render/lifecycle behavior out of the notification package;
- adapt notifications to the new APIs while preserving current visible behavior and fallback;
- delete notification-specific ownership only after parity tests and runtime validation.

### Phase 1: notification-shade completion

- keep notification rows on the generic adapter;
- audit and onboard shade media and other background/container surfaces in the same ViewRoot;
- validate one producer/session/output despite mixed component families.

### Phase 2: control center

- reverse engineer its Window boundary and background authorities;
- onboard tiles, brightness, media, and panel containers through one control-center session.

### Phase 3: volume

- onboard all audited volume panel backgrounds through one volume Window session.

### Phase 4: keyguard and status bar

- resolve whether target surfaces share or split ViewRoots at runtime;
- create one session per actual Window and onboard audited containers without modifying clock/text/icon
  foregrounds.

### Phase 5: dynamic island and misc floating Windows

- group components strictly by live ViewRoot;
- create one session for each distinct Window, never one per floating child.

Each phase ends with contracts, unit tests, Android build, runtime logs, and visual acceptance before
the next family is enabled.

## Testing requirements

Implementation is TDD-first.

### Source and architecture contracts

- exactly one process-global core is constructed and injected into every adapter;
- exactly one session exists per live ViewRoot/display identity;
- component packages contain no OES/EGL/PassBlur producer or renderer creation;
- no CPU capture/readback APIs exist in the production path;
- notification migration does not create a second renderer or session;
- material profiles, transform policy, and frame coalescing have one global authority;
- foreground Views remain visible and interactive;
- no hard-coded orientation offsets or adapter-owned sampling transforms exist.

### Pure unit tests

- registry reuse for two adapters in one ViewRoot and separation for two ViewRoots;
- all four display rotations with crop, scale, and Y-flip matrices;
- stale transform snapshot rejection after root/producer rollover;
- producer and scene events coalesce to one queued drain;
- events arriving during draw schedule at most one follow-up drain;
- source-only, scene-only, and source-plus-scene update behavior;
- node-level activation token acceptance/rejection;
- session failure restores every owned node without touching another session;
- shared-core failure revokes all sessions;
- profile-version invalidation happens once per affected session.

### Runtime acceptance

1. Notification behavior and real refraction remain intact after migration.
2. Ordinary and reverse portrait map the same physical backdrop feature to the same component-local
   position; both landscape rotations also pass.
3. Dragging, scrolling, expanding, and rotating does not visibly trail native component geometry.
4. There is no sampling outside the validated source domain; extra sampling inset remains zero.
5. Adding components in one Window does not increase producer, output-surface, render-thread, EGL
   context, normalization, backdrop-preparation, or swap counts per visual frame.
6. Mixed components in one Window preserve native z-order and foreground drawing.
7. Every root rollover restores native material immediately and resumes glass only after a fresh
   current-generation swap.
8. A failure in volume or a floating Window cannot blank notification/control-center material.
9. `./scripts/test-contracts.sh` and `gradle testDebugUnitTest assembleDebug` pass before any APK is
   offered for device testing.

## First implementation boundary

The first implementation plan covers **Phase 0 only**. It creates the generic core/session APIs,
migrates notifications without expanding visible scope, centralizes DisplayTransform and frame
coalescing, and proves ownership/performance/fallback parity. Component expansion begins only after
the migrated notification path passes runtime validation. This prevents SystemUI-wide rollout from
multiplying an unresolved transform or lifecycle defect.
