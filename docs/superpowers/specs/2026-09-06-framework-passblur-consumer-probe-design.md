# Framework PassBlur Consumer Probe Design

## Problem

The current notification liquid-glass path can successfully produce one real `NotificationShade` PassBlur frame and hand off native notification backgrounds, but it does not remain live. Device evidence on `systemui-001` shows the caller-owned `NotificationShade` PassBlur endpoint is destructed roughly 43 ms after LiquidUI binds it. Prismal then keeps drawing the last consumed OES buffer, so the visible glass becomes a snapshot rather than a live backdrop.

This is an ownership conflict, not a content-authority failure. The unlocked content authority already transitions to `keyguardShowing=false`, adds the exact `Wallpaper BBQ wrapper-lock` exclusion, increments content generation, recreates the producer, and receives a fresh unlocked OES frame.

## Architectural decision

LiquidUI must stop treating the `NotificationShade` root PassBlur endpoint as application-owned infrastructure. The target architecture is a non-owning consumer of HyperOS/framework-owned PassBlur output:

```text
HyperOS owns NotificationShade PassBlur producer
              |
              v
      framework PassBlur consumer path
              |
              v
       LiquidUI texture consumer
              |
              v
       normalize -> Prismal
              |
              v
  shared notification glass output
```

The first implementation step is a strictly read-only diagnostic probe. It must identify the exact runtime owner and lifecycle of the framework PassBlur object before any production consumer registration is attempted.

## Scope

This probe must answer four questions on the exact `systemui-001` target:

1. Which code path owns `NotificationShade` calls to `SurfaceControl.Transaction.SetPassBlurSurface(...)` and `setUpdateTextureFlag(...)`?
2. What exact runtime object exposes or owns `addTextureView(...)` for framework PassBlur consumers?
3. What are the exact method signatures and live argument identities used by the framework when a PassBlur consumer is added or removed?
4. Which row/background/parent clipping authority produces the user-visible extra rounded outline around LiquidUI notification glass?

## Non-goals

- Do not call `SetPassBlurSurface` from the new probe.
- Do not call `setUpdateTextureFlag` from the new probe.
- Do not invoke `addTextureView` from the new probe.
- Do not replace, detach, release, or mutate any framework `TextureView`, `SurfaceTexture`, `Surface`, or PassBlur object.
- Do not change Prismal optical parameters, notification corner radii, overscan values, or native material suppression in this diagnostic build.
- Do not merge the experiment branch into `main` or the existing notification-glass branch.

## Probe architecture

### 1. NotificationShade PassBlur transaction observer

Install read-only before-method hooks on the framework transaction methods:

- `SurfaceControl.Transaction.SetPassBlurSurface(SurfaceControl, Surface)`
- `SurfaceControl.Transaction.setUpdateTextureFlag(SurfaceControl, boolean, float)`

Filter observations to the exact live `NotificationShade` root `SurfaceControl` discovered through the current notification material host `ViewRootImpl` rather than by broad process-wide logging.

For matching calls, log:

- root `SurfaceControl` identity, name, and layer id;
- producer `Surface` identity for `SetPassBlurSurface`;
- enable/scale values for `setUpdateTextureFlag`;
- calling thread name/id;
- a bounded Java stack trace sufficient to identify the framework/SystemUI owner;
- a monotonic observation sequence number so bind/destruct replacement ordering is reconstructable.

The observer must never rewrite arguments or results.

### 2. Framework PassBlur object-graph probe

Extend the existing `[NotifGlass][FrameworkPB]` probe so it can be triggered from an authoritative Shade-root lifecycle event instead of relying only on `NotificationPanelView#setPassWindowBlurEnabled(true)`.

Trigger points may observe:

- `NotificationPanelView` attach / PassBlur-enable lifecycle;
- the first matching `NotificationShade` PassBlur transaction;
- an already-attached live notification material host.

The object-graph walk remains bounded and read-only. It should inspect `ViewRootImpl`, renderer-related holders, runtime fields with pass/blur/texture/surface semantics, and method signatures containing `addTextureView`, `removeTextureView`, `passBlur`, `texture`, `surface`, or `blur`.

For any concrete candidate object, log its runtime class, identity, path from the root, method signatures, and any existing `TextureView`/`SurfaceTexture` identities.

### 3. Notification corner-authority probe

When a live notification scene is available, emit one bounded diagnostic snapshot for the first drawable `ExpandableNotificationRow`:

- row screen bounds and alpha;
- `mBackgroundNormal` screen bounds;
- `actualWidth`, `actualHeight`, top/bottom clipping, expansion state;
- `getTopCornerRadius()` / `getBottomCornerRadius()`;
- parent hierarchy through the shared notification container, including `clipChildren`, `clipToPadding`, `clipToOutline`, and outline-provider class;
- final LiquidUI `NotificationGlassNode` x/y/width/height/four radii;
- current Stage-B sample `backdropRect`, valid dock rect, overscan insets, and coverage.

This diagnostic must not modify any geometry or outline property.

## Logging

Use distinct markers:

- `[NotifGlass][FrameworkPB][TX]` for PassBlur transaction ownership observations.
- `[NotifGlass][FrameworkPB]` for object graph / consumer discovery.
- `[NotifGlass][CornerProbe]` for row/background/parent/Prismal geometry.

Logs must be bounded: no unbounded per-frame stack traces and no per-frame hierarchy dumps. The transaction probe may log the first small number of matching events plus materially changed producer/enable state.

## Failure behavior

The probe is diagnostic-only. Any reflection or hook failure must fail closed for that diagnostic component while preserving the existing notification glass behavior. It must never disable native SystemUI PassBlur or notification rendering.

## Validation

Static contract tests must verify:

- transaction observer hooks only before methods and never rewrites arguments;
- probe sources contain no invocation of `SetPassBlurSurface`, `setUpdateTextureFlag`, `addTextureView`, release/detach APIs, or transaction `apply()`;
- Shade-root filtering is required before stack logging;
- corner probe is read-only and bounded;
- existing notification glass source/content-authority implementation remains byte-for-byte untouched by the diagnostic change where practical.

CI must pass both `./scripts/test-contracts.sh` and `gradle testDebugUnitTest assembleDebug` before the APK is offered for device testing.

## Device success criteria

One device run should provide enough evidence to determine:

1. who clears/replaces the LiquidUI-owned `NotificationShade` endpoint after the first frame;
2. the exact runtime framework PassBlur consumer object and `addTextureView`/related signatures, or a defensible conclusion that no reusable registration API is exposed in the reachable object graph;
3. whether the extra rounded outline comes from native row/parent clipping or from LiquidUI node/Stage-B geometry.

Only after those three questions are answered may a production non-owning consumer integration be designed.