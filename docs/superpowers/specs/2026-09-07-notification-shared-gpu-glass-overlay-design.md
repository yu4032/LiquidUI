# Notification Shared GPU Glass Overlay Design

Date: 2026-09-07
Target: `systemui-001` (`com.android.systemui`, version `16.03.251211.r`, SDK 36 / API 101 profile)
Branch: `feature/notification-view-passblur-authority`
Status: approved architecture; implementation not started

## Summary

Implement notification liquid-glass refraction as one shared GPU scene per `NotificationStackScrollLayout` (NSSL): one HyperOS SurfaceFlinger PassBlur input, one external-OES consumer, one EGL thread/context, one transparent output `TextureView`, and one batched scene containing every visible notification background geometry.

The current `NotificationGpuPassBlurStreamProbe` has validated the missing source-side invariant: the PassBlur stream is live GPU data, survives ordinary observation without BufferQueue recreation, and recovers across `NotificationShade` root SurfaceControl rollover only when the old producer is retired and a fresh `SurfaceTexture`/`Surface` producer is created for the new root.

This document supersedes the producer/recovery assumptions in `2026-09-05-notification-shared-liquid-glass-design.md` and the "never use SetPassBlurSurface" boundary in `2026-09-06-notification-view-passblur-authority-design.md`. Those boundaries were useful experiments, but runtime evidence now proves that the supported zero-copy route on this build is SurfaceFlinger PassBlur -> BufferQueue -> SurfaceTexture/OES, provided producer lifetime follows the root PassBlur lifetime exactly.

## Confirmed runtime facts

The design is based on device evidence from the Dimensity 9400+ target and the supplied system graphics libraries:

- `View`/`RenderNode` expose native blur/mix APIs but no discoverable native refraction API (`explicitRefraction=0`).
- A `RuntimeShader`/`RenderEffect` applied to `NotificationBackgroundView` does not receive the compositor PassBlur texture as shader input.
- Xiaomi ships a native HyperSurface path that creates a PassBlur `SurfaceTexture`, and `SurfaceControl.Transaction` exposes `SetPassBlurSurface(...)` plus `setUpdateTextureFlag(...)` on this build.
- A 0.25-scale external-OES consumer receives live frames from SurfaceFlinger without Bitmap, PixelCopy, ImageReader, MediaProjection, or screenshot capture.
- Reusing an old PassBlur producer after `PassBlur.destruct` can abort SystemUI inside `libgui.so::SurfaceComposerClient::Transaction::setPassBlurSurface`.
- Correct recovery is: root `surfaceDestroyed` -> retire old producer -> new root `surfaceCreated`/`surfaceReplaced` -> create a fresh producer -> bind it -> wait for fresh frames.
- With that rule, the stream rebinds from one `NotificationShade` root layer to the next and frame count continues increasing after rollover without a native crash.

## Architectural correction relative to the dormant code

The dormant `NotificationPassBlurTextureView` is already a **shared** renderer, not a per-card renderer. `NotificationGlassSession` creates one `NotificationGlassHostView` and one renderer for one NSSL; `NotificationGlassCompositor` batches all visible `NotificationGlassNode` objects into the shared output.

Therefore the implementation should **reuse the existing shared host / scene / geometry / batching architecture** and replace the invalid producer/recovery behavior. It must not create a second overlay architecture and must not reactivate old recovery logic wholesale.

Reusable pieces:

- `NotificationGlassHostView`: one transparent sibling inserted immediately before NSSL in the NSSL parent.
- `NotificationGlassNodeCollector`: actual width/height, clipping, expansion geometry, RTL placement, and row-owned top/bottom corner radii.
- `NotificationGlassNode` / `NotificationGlassSceneSnapshot` / `NotificationGlassSceneState`: immutable UI-thread -> GL-thread scene publication.
- `NotificationGlassCompositor`: one scene, many row geometries, one output frame.
- `NotificationGlassSession`: one NSSL owner, row registration, pre-draw scene refresh, fail-closed material handoff.
- The Stage-A OES normalization and Stage-B/Stage-C GPU rendering concepts in `NotificationPassBlurTextureView`.

Must be replaced or constrained:

- `ZeroCopyProducerRecoveryState` decisions that do not encode the newly validated root-destruction boundary.
- any producer reuse after a real ViewRoot surface destruction / SurfaceFlinger `PassBlur.destruct`.
- retry semantics that infer success from framework binding state without a fresh-frame barrier.
- any path that keeps a stale glass frame visible while the source generation is invalid.

## Ownership model

One NSSL owns exactly one `NotificationGlassSession`.

That session owns exactly:

- one `NotificationGlassHostView`;
- one shared `NotificationPassBlurTextureView` (or a renamed equivalent retaining the same role);
- one EGL render thread and EGL context;
- one output `TextureView` surface;
- one current input external-OES texture binding;
- one current input `SurfaceTexture`/`Surface` producer generation;
- one `NotificationGlassSceneState` containing all visible row geometry.

Row count must not increase producer, EGL context, render-thread, or output-surface count.

The GL thread owns all GL object creation/deletion and all `SurfaceTexture.updateTexImage()` calls. The UI thread never retains or manipulates GL texture ids.

## Pixel pipeline

```text
NotificationShade ViewRoot SurfaceControl
  -> SurfaceControl.Transaction.SetPassBlurSurface(root, producerSurface)
  -> setUpdateTextureFlag(root, enabled=true, scale=0.25)
  -> BufferQueue
  -> SurfaceTexture bound to GL_TEXTURE_EXTERNAL_OES
  -> updateTexImage() + SurfaceTexture transform matrix
  -> Stage A: normalize rotation/crop/transform into one quarter-scale RGBA 2D texture
  -> optional small GPU blur / Prismal backdrop preparation
  -> shared optical renderer
       -> rounded-rect SDF per NotificationGlassNode
       -> edge-localized spatial displacement/refraction
       -> depth modulation
       -> R/G/B offset sampling for chromatic aberration
       -> saturation/tint
  -> draw all visible nodes into one transparent output frame
  -> one eglSwapBuffers() into the shared TextureView
```

No pixel data crosses to CPU memory.

Forbidden in the production path:

- `Bitmap` capture/readback;
- PixelCopy;
- ImageReader;
- MediaProjection;
- ScreenCapture / screenshot APIs;
- `SurfaceControl.capture*`;
- `glReadPixels`;
- per-row `TextureView` or per-row PassBlur producer.

## Input producer lifecycle

Producer lifecycle follows **PassBlur endpoint lifetime**, not Java `Surface.isValid()` alone.

### Ordinary observe / refresh

If the ViewRoot identity and current root SurfaceControl are unchanged and the current binding is alive, reuse the current producer and call the update-enable path only. Do not recreate the BufferQueue on row attach, background refresh, pre-draw, or repeated `updateBackground$1` calls.

### Root surface destroyed

`ViewRootImpl.SurfaceChangedCallback#surfaceDestroyed` is the hard teardown boundary.

Immediately:

1. invalidate the published binding;
2. mark the current source generation non-presentable;
3. hide/fail closed from shared glass presentation;
4. restore the native 2dp notification material before a stale shared frame can remain authoritative;
5. detach the frame listener;
6. release the old `Surface` and old `SurfaceTexture` on the GL thread;
7. never pass that producer to `SetPassBlurSurface` again.

The output TextureView/EGL context may remain alive; only the disconnected **input producer generation** must be retired.

### Root surface created / replaced

`surfaceCreated` / `surfaceReplaced` are authority signals only. Do not invoke Xiaomi PassBlur setters inside ViewRoot's own callback transaction.

After the callback transaction has completed:

1. resolve the current valid ViewRoot SurfaceControl;
2. create a fresh `SurfaceTexture` + `Surface` producer on the EGL thread using the current OES texture/context;
3. create an independent `SurfaceControl.Transaction`;
4. bind the fresh producer to the new root;
5. enable updates at scale 0.25;
6. wait for `OnFrameAvailable` and a successful `updateTexImage()`;
7. render at least one scene frame from that new source generation;
8. require successful `eglSwapBuffers()` before presentation authority returns.

No fixed delay, polling loop, or timer is part of rollover correctness.

## Fresh-frame and presentation authority

A successful `SetPassBlurSurface` call is **not** equivalent to a fresh backdrop.

Glass becomes presentation-authoritative only when all of the following refer to the current generation:

- current root SurfaceControl generation is valid;
- current producer generation is bound;
- at least one `SurfaceTexture` frame has arrived;
- `updateTexImage()` succeeded for that producer generation;
- current scene snapshot contains at least one drawable row node;
- the shared optical frame was rendered from that fresh source generation;
- `eglSwapBuffers()` succeeded for that rendered frame.

The renderer posts an activation token to the UI thread containing at minimum source generation, scene generation, and swap sequence. The UI thread re-checks that the token still matches the current session before suppressing native material.

Any source-loss event immediately revokes presentation authority. The design must never intentionally display a frozen last-good backdrop while the source generation is invalid.

## Layer placement and material handoff

`NotificationGlassHostView` remains a sibling inserted immediately before NSSL in NSSL's parent. It must not become an NSSL child because NSSL drawing logic assumes `ExpandableView` children.

NSSL remains visually above the shared glass layer and continues to draw notification text, icons, controls, and interaction surfaces.

Before the shared renderer owns presentation:

- keep the current verified 2dp native card PassBlur fallback on `NotificationBackgroundView`;
- keep LiquidUI bloom disabled;
- keep the large shade backdrop blur disabled as today.

After the first valid shared glass frame:

- make the notification background material transparent/suppressed so the shared glass scene below NSSL becomes visible;
- preserve text/icon/content drawing above the shared glass;
- suppress only vendor material that would visually cover the shared glass; do not mutate content semantics or notification layout.

On source loss, GL failure, renderer detach, or terminal bind failure:

- hide the shared output immediately;
- restore the 2dp native card fallback/material state;
- resume shared presentation only after a new valid activation token.

## Geometry authority

`ExpandableNotificationRow#mBackgroundNormal` remains the geometry target.

The UI thread uses `NotificationGlassNodeCollector` to publish immutable nodes with:

- screen-relative left/top;
- actual width/visible height;
- clip top/bottom effects;
- expansion width/height;
- RTL placement;
- top-left/top-right radius from row top-corner magnitude;
- bottom-left/bottom-right radius from row bottom-corner magnitude;
- row opacity.

The two booleans passed to `NotificationUtil#setRoundRect` are **not** interpreted as top/bottom corner-presence flags on this SystemUI build. Per-edge radius magnitude remains row authority, as already established by decompilation and runtime behavior.

The GL thread receives only immutable scene snapshots and never retains SystemUI `View` objects.

## Optical model

The first production-visible refraction pass must prove real spatial distortion before visual polish.

The shader uses rounded-rect signed distance and its gradient/normal. Refraction is strongest in a configurable edge band and decays toward the card interior. Sample coordinates are displaced along the local SDF normal; depth adds a center-relative component. R/G/B sample positions diverge slightly to create chromatic aberration.

Initial behavior should track the already reverse-engineered HyperLight optics rather than calling ordinary blur "refraction". Starting logical targets:

- refraction-height band: approximately 20 logical units before density/source-scale conversion;
- refraction amount: approximately 48 logical units;
- depth effect: approximately 1.0;
- chromatic aberration: approximately 0.04 relative strength;
- saturation: approximately 1.3;
- backdrop blur baseline: current 2dp-equivalent behavior.

These are tuning seeds, not API contracts. The acceptance criterion is visible background **position displacement** near glass edges, not increased blur, tint, bloom, or stroke.

Bloom/stroke stays disabled in the first shared-refraction build so border rendering cannot be mistaken for refraction.

## Rendering strategy and performance

Use one EGL context/thread for both input OES consumption and output rendering so no cross-context texture ownership is required.

Stage A runs only when a new source frame is consumed. Scene-only changes may reuse the latest valid normalized backdrop texture.

The source remains quarter-scale (0.25), matching the validated HyperOS path. The output surface matches the shade host size, but fragment work should be limited to notification card bounds/scissors; no full-screen per-card intermediate textures are allowed.

There is one normalized source texture/FBO and one shared optical scene, not one FBO per notification.

When there are no drawable rows, pause producer updates where safe and keep presentation in the native fallback state until rows return.

## State model

At minimum, the session/renderer behavior must distinguish:

- `FALLBACK_NATIVE`: native 2dp card material visible, shared glass not authoritative;
- `SOURCE_BOUND`: producer bound but no fresh frame yet;
- `SOURCE_FRESH`: fresh current-generation OES frame consumed;
- `GLASS_ACTIVE`: current source + current scene rendered and swapped successfully; native material suppressed;
- `SOURCE_LOST`: root/producer generation invalid; shared output hidden and native fallback restored;
- `TERMINAL_FAILURE`: EGL/GL/contract failure; shared path disabled until session recreation.

State transitions should be implemented as pure/testable policy where practical rather than spread across callback side effects.

## Diagnostics

Keep structured logs with enough information to distinguish bind success from presentation success:

- root layer id / ViewRoot identity;
- root surface epoch;
- producer generation;
- binding endpoint generation / surface sequence;
- source frame count and timestamp;
- scene generation and drawable node count;
- rendered frame sequence;
- EGL swap sequence;
- activation/revocation reason;
- `producerPreserved=false` on real root teardown recovery.

Do not log every frame indefinitely; keep first-frame, rollover, failure, and periodic rate diagnostics.

## Testing requirements

Implementation proceeds TDD-first.

Required source/architecture contracts:

- active notification path owns exactly one shared renderer per NSSL;
- no per-row TextureView or producer creation;
- no CPU capture/readback APIs;
- 0.25 PassBlur source scale;
- ViewRoot `SurfaceChangedCallback` is used as a lifecycle signal only;
- no `SetPassBlurSurface` invocation through ViewRoot's callback transaction;
- `surfaceDestroyed` retires the producer and prevents old-producer rebind;
- ordinary observe does not recreate the producer;
- activation requires a fresh-frame + successful-swap barrier;
- source loss restores native fallback;
- bloom remains disabled in the first refraction build.

Required pure-state/unit coverage:

- initial bind -> fresh frame -> active handoff;
- repeated observe -> same producer generation;
- surface destroy -> producer retired, presentation revoked;
- new surface -> fresh producer -> fresh frame -> reactivation;
- stale activation token rejected after generation rollover;
- bind failure does not advance successful endpoint generation;
- no-row scene pauses/does not present glass;
- terminal GL failure restores fallback.

CI must run SDK-independent contracts plus `testDebugUnitTest assembleDebug` before any APK is offered for runtime testing.

## Runtime acceptance criteria

A build is not considered successful only because it compiles or logs a bind.

The runtime target must satisfy all of the following:

1. Entering and leaving notification center repeatedly does not crash SystemUI.
2. GPU source frame count increases while the shade backdrop changes.
3. After NotificationShade SurfaceControl rollover, the old producer is retired, a fresh producer binds to the new root, and frame count continues.
4. Notification edge regions show clear spatial displacement of background features, not only blur/tint.
5. RGB edge dispersion is visible in the exaggerated validation build.
6. Scrolling/expanding notifications keeps refraction geometry aligned with each row.
7. Native 2dp fallback is restored immediately while the source is invalid; no frozen glass frame remains authoritative.
8. No Bitmap/screenshot/CPU capture path is active.
9. Producer/EGL/output surface count remains constant with notification row count.

## Migration strategy

Do not turn every dormant subsystem on at once.

Implementation should proceed in this order after the implementation plan is approved:

1. Extract the validated producer lifecycle from `NotificationGpuPassBlurStreamProbe` into a production state/endpoint component, preserving the probe as diagnostics or removing it after equivalent logging exists.
2. Integrate that endpoint lifecycle into the existing single shared `NotificationPassBlurTextureView` / `NotificationGlassSession` ownership model.
3. Add fresh-generation activation/revocation state and fail-closed 2dp fallback handoff.
4. Run the shared renderer first with an exaggerated refraction/dispersion profile and bloom disabled.
5. Validate rollover, scrolling, expansion, and background freshness on-device.
6. Only after spatial refraction is proven, tune material parameters and reduce diagnostic verbosity.

The previous active per-view 2dp PassBlur implementation remains the fallback until step 4 proves a valid first shared GPU glass frame.