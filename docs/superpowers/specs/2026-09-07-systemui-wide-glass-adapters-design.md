# SystemUI-Wide Glass Adapters Design

## Status

Approved architectural direction for extending LiquidUI from notification-only shared glass to SystemUI-wide component glass on target profile `systemui-001`.

This specification intentionally keeps the existing process-global / per-window shared rendering ownership model. New feature areas may register glass nodes, but they must not create independent EGL, OES, PassBlur, SurfaceTexture, producer-recovery, or renderer pipelines.

## Goal

Apply LiquidUI glass to every SystemUI component that owns an independent visual material/background on the verified HyperOS SystemUI target, while preserving:

- one process-global `SystemUiGlassCore`;
- one shared render thread;
- one `WindowGlassSession` per live Window identity;
- one renderer / PassBlur producer path per Window;
- exact lifecycle authorization before hiding native material;
- immediate native fallback on authority loss, root replacement, producer loss, rotation invalidation, or terminal renderer failure;
- the existing Global + CARD/TILE/SLIDER/PANEL/FLOATING style configuration and live-update pipeline.

The change is not a generic recursive View decorator. Only reverse-engineered SystemUI material hosts are eligible.

## Non-goals

- Do not glassify arbitrary text, icons, labels, transparent containers, or purely structural layouts.
- Do not treat the full-screen notification shade scrim, keyguard scrim, or root canvas as a glass node.
- Do not replace SystemUI's global dimming, scrim, wallpaper, or scene-composition semantics.
- Do not create a second capture or PassBlur producer for QS, Control Center, Volume, Media, Status Bar, or Keyguard.
- Do not use class-name heuristics across unsupported SystemUI versions.
- Do not install a hook when the target-profile structural contract for that feature cannot be proven.
- Do not add fixed timing delays where an authoritative lifecycle callback exists.

## Coverage definition

"All SystemUI components" means all components on `systemui-001` that visibly render their own bounded background/material and whose lifecycle/geometry host can be identified from the decompiled target.

The initial coverage matrix is:

| Domain | Component family | Glass profile |
| --- | --- | --- |
| Notification shade | notification rows, grouped cards, media/information cards | `CARD` |
| Control Center / QS | quick settings tiles, device-control tiles, toggles, bounded action cells | `TILE` |
| Control Center / QS | brightness and other continuous bounded controls | `SLIDER` |
| Control Center / QS | bounded section containers / major card-like panels | `PANEL` or `CARD` depending on host semantics |
| Volume | per-stream columns/sliders | `SLIDER` |
| Volume | volume dialog / expanded bounded panel background | `PANEL` |
| Media | media player / media recommendation bounded surfaces | `CARD` |
| Status bar / transient UI | bounded capsules, chip-like transient surfaces, floating status affordances | `FLOATING` |
| Dynamic island / heads-up-like floating SystemUI surfaces | independently bounded floating surfaces | `FLOATING` |
| Keyguard | bounded cards / affordance panels that own a material background | `CARD`, `TILE`, or `PANEL` according to semantics |
| Misc SystemUI | dialogs, bounded system cards, shell-owned bounded overlays proven to live in the same process/window model | `PANEL` or `FLOATING` |

A component that has no independent background is not a glass node even if it is a child of a glass host.

## Architectural model

```text
SystemUIApplication / ModuleMain
        |
        v
SystemUiGlassCore                       (process-global)
        |
        +-- WindowGlassRegistry
        |       |
        |       +-- WindowGlassSession  (one per WindowKey)
        |               |
        |               +-- GlassHostView
        |               +-- WindowGlassRenderer
        |               +-- shared PassBlur/OES producer
        |
        +-- current GlassStyleConfig snapshot

Feature adapters
        |
        +-- NotificationGlassAdapter
        +-- ControlCenterGlassAdapter
        +-- VolumeGlassAdapter
        +-- MediaGlassAdapter
        +-- StatusBarGlassAdapter
        +-- KeyguardGlassAdapter
        +-- MiscSystemUiGlassAdapter
                |
                v
          GlassNode submissions only
```

Feature adapters never own rendering infrastructure. Their responsibilities are limited to:

1. discovering the exact material host through a proven SystemUI lifecycle callback;
2. tracking host attach/detach and visibility;
3. collecting geometry, radius, alpha/opacity, z-order and profile;
4. publishing/updating/removing `GlassNode`s in the correct `WindowGlassSession`;
5. suppressing the native material only after an exact glass presentation authorization is received;
6. restoring the native material immediately when authorization is revoked.

## SystemUiMaterialClassifier

Add a small shared `SystemUiMaterialClassifier` that maps an already-proven host kind to one of the five existing material profiles.

It must not discover components by fuzzy class-name matching. Discovery remains feature-adapter-specific and target-profile-specific.

Example host kinds:

```text
NOTIFICATION_CARD        -> CARD
MEDIA_CARD               -> CARD
QS_TILE                  -> TILE
DEVICE_CONTROL_TILE      -> TILE
BRIGHTNESS_SLIDER        -> SLIDER
VOLUME_SLIDER            -> SLIDER
CONTROL_CENTER_PANEL     -> PANEL
VOLUME_PANEL             -> PANEL
STATUS_CAPSULE            -> FLOATING
TRANSIENT_FLOATING_PANEL -> FLOATING
```

This keeps profile choice centralized while keeping reverse-engineering authority inside each domain adapter.

## Node identity and lifecycle

Each adapter uses stable logical node IDs plus lifecycle generations. A recycled/rebound SystemUI View must not inherit authorization from a previous occupant.

A node token continues to include the existing exactness dimensions used by the shared core, including the relevant root / producer / scene / profile / lifecycle generations.

Rules:

- attach or semantic bind starts a new node lifecycle generation;
- detach, recycle, host replacement, or semantic unbind revokes the generation;
- stale callbacks cannot suppress a new host's native material;
- profile hot-update revokes presentation for the old profile version but does not rebind the producer;
- root or producer rollover revokes all presentation under that Window until a fresh frame is authorized.

## Native material suppression

Native background suppression is feature-specific because different SystemUI components use different background APIs and state holders.

Each adapter must implement a small `NativeMaterialController`-style boundary with two operations:

```text
suppress(host, lifecycleGeneration)
restore(host, lifecycleGeneration)
```

Requirements:

- suppression is idempotent;
- restoration is idempotent;
- original drawable / tint / alpha / vendor blur state is captured before first suppression for that lifecycle;
- restoration may only affect the same live lifecycle generation;
- suppression is permitted only while the exact node is present in the current authorized presentation set;
- any loss of vendor PassBlur authority, source freshness, surface/root identity, renderer health, or node authorization immediately restores native material;
- adapter failure must fail closed: keep/restore native material and remove the glass node.

The project must not use one global "make every background transparent" hook.

## Geometry and clipping

Geometry is collected in root/window coordinates through the existing shared geometry path. Each adapter supplies:

- root-space bounds or quad;
- corner radius / shape radius;
- effective opacity;
- z-order;
- material profile;
- stable node identity / lifecycle generation.

The renderer remains responsible for mapping Window geometry into PassBlur/OES sampling coordinates, including the existing rotation-safe SurfaceTexture crop neutralization.

Adapters must not introduce per-domain sampling matrices or offsets.

### Shape policy

Phase 1 uses rounded-rect-compatible host geometry only. Components with materially non-rounded-rect masks must remain native until the shared renderer has an explicit shape contract.

Do not approximate arbitrary shapes with oversized rounded rectangles if that changes visible semantics.

## Domain adapters

### 1. Control Center / Quick Settings

Create `ControlCenterGlassAdapter` around reverse-engineered host lifecycles for:

- QS tile cells;
- major toggles;
- device-control cells;
- brightness / continuous sliders;
- bounded Control Center section panels where the target actually owns an independent background.

Tiles map to `TILE`, continuous controls to `SLIDER`, and section surfaces to `PANEL` or `CARD` according to the decompiled material owner.

The adapter must handle tile reuse and rebind without leaking lifecycle authorization.

### 2. Volume

Create `VolumeGlassAdapter` around the target's `VolumePanelViewController` / `MIUI_VolumeColumn` lifecycle authority.

- the bounded volume dialog / expanded container maps to `PANEL`;
- individual stream controls map to `SLIDER` when they own a separate bounded material;
- icons/text remain children above the glass node and are not independently glassified.

Collapsed/expanded transitions must update geometry and node membership without creating another renderer.

### 3. Media

Create `MediaGlassAdapter` for bounded media surfaces on shade / Control Center / keyguard where the verified target exposes a material host.

Use `CARD` by default. Media artwork/content is not itself used as a new capture source; glass remains based on the existing Window backdrop producer.

### 4. Status bar and floating surfaces

Create `StatusBarGlassAdapter` for bounded capsule/chip/floating SystemUI materials.

Use `FLOATING`. Full-width status bar roots and transparent icon containers are explicitly excluded.

### 5. Keyguard

Create `KeyguardGlassAdapter` only for bounded keyguard components with an independently owned material host.

Keyguard root scrims, wallpaper dimming layers and full-screen security surfaces remain outside the glass-node set unless a later separately approved design explicitly changes scene composition.

### 6. Misc bounded SystemUI overlays

Create `MiscSystemUiGlassAdapter` only for individually proven bounded material hosts not belonging to the domains above. This is not a wildcard View scanner.

Each accepted misc host must be documented in the target contract and assigned a stable semantic host kind.

## Window/session ownership

A feature adapter resolves its current Window session through the existing `SystemUiGlassCore.sessionFor(...)` API.

If several domains share the same SystemUI Window, their nodes coexist in the same session and renderer.

If SystemUI opens a genuinely separate Window (for example a separate volume or floating overlay window), it receives a separate `WindowGlassSession` because the ownership rule is one session per Window identity, not one session for the whole process.

The process still owns only one shared core and one render thread.

## Style configuration and GUI

No new glass parameter schema is required for this migration. All new component families use the existing:

- Global defaults;
- CARD override;
- TILE override;
- SLIDER override;
- PANEL override;
- FLOATING override;
- nine highlight gates;
- global sampling controls;
- live style update pipeline.

The settings GUI may add a coverage section with per-domain enable toggles and diagnostic node counts, but it must not duplicate optical parameters per SystemUI domain. Domain-to-profile mapping is semantic and central.

Suggested feature toggles:

```text
systemui_glass_notifications
systemui_glass_control_center
systemui_glass_volume
systemui_glass_media
systemui_glass_statusbar
systemui_glass_keyguard
systemui_glass_misc
```

Notifications remain enabled by the existing notification feature contract; schema migration must preserve the user's existing setting.

## Reverse-engineering contract requirements

Before a production adapter hook is added for a domain, document:

1. exact target class(es);
2. authoritative lifecycle method(s);
3. exact material host View / field / return value;
4. native material owner and mutation path;
5. attach/recycle/rebind semantics;
6. geometry authority;
7. structural runtime probes;
8. failure/fallback behavior.

Derived contracts live under:

```text
docs/reverse-engineering/systemui-001/
```

The proprietary APK and full decompiled tree remain untracked.

## Failure handling

SystemUI-wide coverage must be additive and isolated.

- One failed domain adapter does not disable proven adapters in other domains.
- A domain whose structural probe fails reports `UNSUPPORTED` and installs no speculative hooks.
- An unexpected adapter failure reports `FAILED`, restores all native materials controlled by that adapter, removes its nodes, and leaves the shared core alive for other domains.
- Renderer / producer failure remains a Window-level fail-closed event and revokes every node in that Window.
- No adapter may busy-loop or repeatedly re-hook after failure.

## Diagnostics

Add structured diagnostics at the adapter/session boundary, rate-limited where events can be frequent.

Minimum fields:

```text
domain
hostKind
nodeId
lifecycleGeneration
window/root identity
profile
geometry generation
presentation authorized/revoked reason
native material state
```

Expose aggregate counts in diagnostics:

```text
live windows
live sessions
live nodes per profile
live nodes per domain
suppressed native materials
renderer count
producer count
```

Acceptance requires ownership counts to remain stable while tiles, notifications, media cards, volume panels, and floating surfaces appear/disappear.

## Testing strategy

### Pure contract tests

Add tests for:

- `SystemUiMaterialClassifier` exact host-kind mapping;
- stable node lifecycle generation on recycle/rebind;
- duplicate adapter updates coalescing into one scene update;
- removal/restoration on detach;
- native material cannot be suppressed before authorization;
- stale authorization cannot suppress a new lifecycle;
- style rollover revokes old presentation without producer rebind;
- domain failure removes only that domain's nodes;
- no adapter source owns `EGL14`, `GL_TEXTURE_EXTERNAL_OES`, `SurfaceTexture`, `SetPassBlurSurface`, `HandlerThread`, `PixelCopy`, `ScreenCapture`, or `glReadPixels`.

### Architecture invariants

The final source tree must satisfy:

```text
exactly one process construction of SystemUiGlassCore
exactly one shared render HandlerThread
no feature adapter creates WindowGlassRenderer directly
no feature adapter creates PassBlur/OES producers
all feature adapters resolve WindowGlassSession through SystemUiGlassCore
full-screen scrim/root classes are not registered as glass nodes
all five profiles remain supported by the existing GUI/config schema
```

### Android/CI verification

For every implementation checkpoint:

```bash
./scripts/test-contracts.sh
gradle testDebugUnitTest assembleDebug --stacktrace
```

The final clean branch must upload a directly installable debug APK through the existing API101 workflow.

## Device-validation matrix

Test the verified target in portrait, landscape 90°, reverse portrait and landscape 270°.

At minimum validate:

- notification cards;
- collapsed and expanded Control Center;
- several QS tile states and tile reuse;
- brightness slider interaction;
- media card appearance/disappearance;
- collapsed and expanded volume panel;
- multiple volume streams;
- status/floating capsule surfaces when available;
- keyguard bounded material surfaces identified by the adapter;
- lock/unlock and shade open/close;
- rotation while each major domain is visible;
- root/producer rollover and recovery;
- live GUI style changes while multiple domains are visible.

For every scenario verify:

- backdrop alignment;
- radius/shape parity;
- no stale sampling frame;
- no duplicate glass layer;
- no persistent native background underneath glass;
- immediate native fallback during unauthorized states;
- no SystemUI crash;
- stable one-core / one-thread / per-window-session ownership counts.

## Implementation order

1. shared adapter contracts + classifier + domain registry;
2. Control Center / QS tile hosts;
3. Control Center sliders;
4. Volume panel / volume sliders;
5. media cards;
6. status bar / floating bounded materials;
7. keyguard bounded materials;
8. misc proven bounded overlays;
9. GUI domain toggles + diagnostics;
10. final ownership audit, four-rotation device build and cleanup.

Each domain is implemented test-first and must reach GREEN independently before the next domain begins.

## Acceptance criteria

This migration is complete when:

1. every proven bounded SystemUI material host on `systemui-001` is either represented by a shared-core glass node or explicitly documented as excluded with a technical reason;
2. notifications, QS/Control Center, sliders, volume, media, floating/status surfaces, bounded keyguard surfaces, and approved misc overlays all share the existing glass core;
3. no domain owns a parallel renderer or backdrop producer;
4. original material is hidden only under exact current presentation authorization and restores on every failure/revocation path;
5. four-rotation sampling remains aligned;
6. existing Global + five-profile GUI controls apply live across all newly covered domains;
7. fast contracts and full Gradle build are GREEN;
8. the final APK passes device validation without SystemUI crashes or persistent fallback/suppression errors.
