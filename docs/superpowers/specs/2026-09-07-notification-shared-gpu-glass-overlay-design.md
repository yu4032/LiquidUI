# Notification Shared GPU Glass Overlay Design

Date: 2026-09-07
Target: `systemui-001` (`com.android.systemui`, version `16.03.251211.r`, SDK 36 / API 101 profile)
Branch: `feature/notification-view-passblur-authority`
Status: approved architecture; implementation not started

## Summary

Implement notification liquid-glass refraction as one shared GPU scene per `NotificationStackScrollLayout` (NSSL): one HyperOS SurfaceFlinger PassBlur input, one external-OES consumer, one EGL thread/context, one transparent output `TextureView`, and one batched scene containing every visible notification background geometry.

The current `NotificationGpuPassBlurStreamProbe` has validated the missing source-side invariant: the PassBlur stream is live GPU data, ordinary observation does not require BufferQueue recreation, and `NotificationShade` root SurfaceControl rollover recovers only when the old producer is retired and a fresh `SurfaceTexture`/`Surface` producer is created for the new root.

This document supersedes the producer/recovery assumptions in `2026-09-05-notification-shared-liquid-glass-design.md` and the "never use SetPassBlurSurface" experiment boundary in `2026-09-06-notification-view-passblur-authority-design.md`.

## Confirmed runtime facts

The design is based on device evidence from the Dimensity 9400+ target and the supplied system graphics libraries:

- `View`/`RenderNode` expose native blur/mix APIs but no discoverable native refraction API (`explicitRefraction=0`).
- A `RuntimeShader`/`RenderEffect` applied to `NotificationBackgroundView` does not receive the compositor PassBlur texture as shader input.
- Xiaomi ships a HyperSurface path that creates a PassBlur `SurfaceTexture`, and `SurfaceControl.Transaction` exposes `SetPassBlurSurface(...)` plus `setUpdateTextureFlag(...)` on this build.
- A 0.25-scale external-OES consumer receives live SurfaceFlinger frames without Bitmap, PixelCopy, ImageReader, MediaProjection, or screenshot capture.
- Reusing an old PassBlur producer after `PassBlur.destruct` can abort SystemUI inside `libgui.so::SurfaceComposerClient::Transaction::setPassBlurSurface`.
- Correct recovery is: root `surfaceDestroyed` -> retire old producer -> new root `surfaceCreated`/`surfaceReplaced` -> create a fresh producer -> bind it -> wait for fresh frames.
- With that rule, the stream rebinds to the next `NotificationShade` root and frame count continues increasing after rollover without a native crash.

## Architectural correction relative to dormant code

The dormant `NotificationPassBlurTextureView` is already a **shared** renderer, not a per-card renderer. `NotificationGlassSession` creates one `NotificationGlassHostView` and one renderer for one NSSL; `NotificationGlassCompositor` batches all visible `NotificationGlassNode` objects into the shared output.

Therefore implementation reuses the existing shared host / scene / geometry / batching architecture and replaces the invalid producer/recovery behavior. It must not create a second overlay architecture and must not reactivate old recovery logic wholesale.

Reusable pieces:

- `NotificationGlassHostView`: one transparent sibling inserted immediately before NSSL in the NSSL parent.
- `NotificationGlassNodeCollector`: actual width/height, clipping, expansion geometry, RTL placement, and row-owned top/bottom corner radii.
- `NotificationGlassNode` / `NotificationGlassSceneSnapshot` / `NotificationGlassSceneState`: immutable UI-thread -> GL-thread scene publication.
- `NotificationGlassCompositor`: one scene, many row geometries, one output frame.
- `NotificationGlassSession`: one NSSL owner, row registration, pre-draw scene refresh, fail-closed material handoff.
- `PrismalRenderer`: existing shared liquid-glass optical renderer.
- Stage-A OES normalization and GPU-only output concepts in `NotificationPassBlurTextureView`.

Must be replaced or constrained:

- `ZeroCopyProducerRecoveryState` decisions that do not encode the validated root-destruction boundary.
- any producer reuse after real ViewRoot surface destruction / SurfaceFlinger `PassBlur.destruct`.
- retry semantics that infer success from framework binding state without a fresh-frame barrier.
- any path that keeps a stale glass frame visible while the source generation is invalid.

## Ownership model

One NSSL owns exactly one `NotificationGlassSession`.

That session owns exactly:

- one `NotificationGlassHostView`;
- one shared `NotificationPassBlurTextureView` (or renamed equivalent with the same role);
- one EGL render thread and EGL context;
- one output `TextureView` surface;
- one current external-OES input binding;
- one current input `SurfaceTexture`/`Surface` producer generation;
- one `NotificationGlassSceneState` containing all visible row geometry;
- one `PrismalRenderer` instance.

Row count must not increase producer, EGL context, render-thread, renderer, or output-surface count.

The GL thread owns all GL object creation/deletion and every `SurfaceTexture.updateTexImage()` call. The UI thread never manipulates GL texture ids.

## Pixel pipeline

```text
NotificationShade ViewRoot SurfaceControl
  -> SurfaceControl.Transaction.SetPassBlurSurface(root, producerSurface)
  -> setUpdateTextureFlag(root, enabled=true, scale=0.25)
  -> BufferQueue
  -> SurfaceTexture bound to GL_TEXTURE_EXTERNAL_OES
  -> updateTexImage() + SurfaceTexture transform matrix
  -> Stage A: normalize rotation/crop/transform into one quarter-scale RGBA 2D texture
  -> PrismalRenderer.prepareBackdrop() once per consumed source frame
  -> PrismalRenderer.beginGlassFrame()
  -> NotificationGlassCompositor.drawFrame(row_1 ... row_N)
  -> one transparent optical scene texture
  -> one composite into the shared TextureView
  -> one eglSwapBuffers()
```

No pixel data crosses to CPU memory.

Forbidden in the production path:

- Bitmap capture/readback;
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

If the ViewRoot identity and current root SurfaceControl are unchanged and the binding is alive, reuse the current producer and update-enable state only. Do not recreate the BufferQueue on row attach, material refresh, pre-draw, or repeated `updateBackground$1` calls.

### Root surface destroyed

`ViewRootImpl.SurfaceChangedCallback#surfaceDestroyed` is the hard teardown boundary.

Immediately:

1. invalidate the published binding;
2. mark the source generation non-presentable;
3. revoke shared-glass presentation authority;
4. hide the shared output and restore the native 2dp notification fallback before a stale glass frame can remain authoritative;
5. detach the frame listener;
6. release the old `Surface` and old `SurfaceTexture` on the GL thread;
7. never pass that producer to `SetPassBlurSurface` again.

The output TextureView/EGL context may remain alive. Only the disconnected **input producer generation** must be retired.

### Root surface created / replaced

`surfaceCreated` / `surfaceReplaced` are authority signals only. Never invoke Xiaomi PassBlur setters inside ViewRoot's own callback transaction.

After that callback transaction completes:

1. resolve the current valid ViewRoot SurfaceControl;
2. create a fresh `SurfaceTexture` + `Surface` producer on the EGL thread using the current EGL context;
3. create an independent `SurfaceControl.Transaction`;
4. bind the fresh producer to the new root;
5. enable updates at scale 0.25;
6. wait for `OnFrameAvailable` and successful `updateTexImage()`;
7. render at least one scene frame from the new source generation;
8. require successful `eglSwapBuffers()` before presentation authority returns.

No fixed delay, polling loop, or timer is part of rollover correctness.

## Fresh-frame and presentation authority

A successful `SetPassBlurSurface` call is **not** equivalent to a fresh backdrop.

Glass becomes presentation-authoritative only when all of these refer to the current generation:

- root SurfaceControl generation is valid;
- producer generation is bound;
- at least one `SurfaceTexture` frame arrived;
- `updateTexImage()` succeeded;
- current scene contains at least one drawable node;
- `PrismalRenderer.prepareBackdrop()` used the fresh normalized texture;
- the shared optical frame rendered from that source + scene generation;
- `eglSwapBuffers()` succeeded.

The renderer posts an activation token to the UI thread containing at minimum source generation, scene generation, and swap sequence. The UI thread re-checks the token against current session state before suppressing native material.

Any source-loss event immediately revokes presentation authority. The design must never intentionally display a frozen last-good backdrop while the source generation is invalid.

## Layer placement and material handoff

`NotificationGlassHostView` remains a sibling inserted immediately before NSSL in NSSL's parent. It must not become an NSSL child because NSSL drawing assumes `ExpandableView` children.

NSSL remains visually above the shared glass layer and continues drawing notification text, icons, controls, and interaction surfaces.

Before the shared renderer owns presentation:

- keep the verified 2dp native card PassBlur fallback on `NotificationBackgroundView`;
- keep LiquidUI bloom disabled;
- keep the large shade backdrop blur disabled as today.

After the first valid shared glass frame:

- make only the notification background/vendor material that covers the shared scene transparent/suppressed;
- preserve text/icon/content drawing above the shared glass;
- do not mutate notification layout or content semantics.

On source loss, GL failure, renderer detach, or terminal bind failure:

- hide shared output immediately;
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

The two booleans passed to `NotificationUtil#setRoundRect` are **not** top/bottom corner-presence flags on this SystemUI build. Per-edge radius magnitude remains row authority.

The GL thread receives immutable scene snapshots only and never retains SystemUI `View` objects.

## Optical implementation decision

The first shared-refraction build uses the existing **`PrismalRenderer`**. Do not implement a second HyperLight-style shader in parallel.

Repository inspection confirms Prismal already contains the required real optical behavior:

- rounded-rect SDF and SDF gradient;
- `u_ior` and `u_glassThickness`;
- `u_normalStrength` and `u_displacementScale`;
- `u_lensRefractionPx` and `u_lensDepthEffect`;
- `u_chromaticAberration`, `u_dispersionR`, and `u_dispersionB`;
- separate background and blurred backdrop sampling;
- batched multi-glass drawing over one prepared backdrop.

Therefore the implementation problem is source/lifecycle/presentation correctness plus parameter tuning, not shader invention.

HyperLight's reverse-engineered values remain a **visual target**, not a direct numeric mapping because Prismal uniforms use different units. The first validation profile should deliberately exaggerate Prismal refraction/displacement and chromatic aberration enough to make background position displacement obvious while retaining a 2dp blur baseline.

To prevent highlights from being mistaken for refraction, the first validation build uses a `PrismalHighlightProfile` with every highlight component disabled (`skyHaze`, `specular`, `litRim`, `oppositeRim`, `cornerRim`, `faceSheen`, `plainHighlight`, `caustics`, `pressGlow` all false). LiquidUI bloom remains disabled too.

Acceptance is visible **spatial displacement of backdrop features near the rounded edges** plus RGB dispersion. Blur, tint, glow, rim, or stroke alone does not count as refraction.

After displacement is proven, highlights may be reintroduced and material parameters tuned toward HyperLight's appearance.

## Rendering strategy and performance

Use one EGL context/thread for input OES consumption, Prismal rendering, and output. No cross-context texture ownership is needed.

Stage A and `prepareBackdrop()` run only when a new source frame is consumed. Scene-only changes may reuse the latest current-generation prepared backdrop.

The source remains quarter-scale (0.25), matching the validated HyperOS path. The output matches the shade host size. No full-screen per-card intermediate textures are allowed.

There is one normalized source texture/FBO and one shared Prismal scene, not one FBO per notification.

When there are no drawable rows, pause producer updates where safe and keep presentation in native fallback until rows return.

## State model

At minimum distinguish:

- `FALLBACK_NATIVE`: native 2dp card material visible, shared glass not authoritative;
- `SOURCE_BOUND`: producer bound but no fresh frame yet;
- `SOURCE_FRESH`: fresh current-generation OES frame consumed;
- `GLASS_ACTIVE`: current source + current scene rendered and swapped successfully; native material suppressed;
- `SOURCE_LOST`: root/producer generation invalid; shared output hidden and native fallback restored;
- `TERMINAL_FAILURE`: EGL/GL/contract failure; shared path disabled until session recreation.

State transitions should be pure/testable policy where practical instead of being spread across callbacks.

## Diagnostics

Keep structured logs sufficient to distinguish bind success from presentation success:

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

Do not log every frame indefinitely; retain first-frame, rollover, failure, and periodic rate diagnostics.

## Testing requirements

Implementation proceeds TDD-first.

Required source/architecture contracts:

- active path owns exactly one shared renderer per NSSL;
- no per-row TextureView or producer creation;
- no CPU capture/readback APIs;
- 0.25 PassBlur source scale;
- ViewRoot `SurfaceChangedCallback` is a lifecycle signal only;
- no `SetPassBlurSurface` through ViewRoot's callback transaction;
- `surfaceDestroyed` retires the producer and prevents old-producer rebind;
- ordinary observe does not recreate the producer;
- activation requires fresh-frame + prepared-backdrop + successful-swap barriers;
- source loss restores native fallback;
- first refraction build disables all Prismal highlight components and LiquidUI bloom.

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

A build is not successful merely because it compiles or logs a bind.

It must satisfy all of the following:

1. Entering/leaving notification center repeatedly does not crash SystemUI.
2. GPU source frame count increases while the shade backdrop changes.
3. After NotificationShade SurfaceControl rollover, the old producer retires, a fresh producer binds to the new root, and frame count continues.
4. Notification edge regions show clear spatial displacement of backdrop features, not only blur/tint.
5. RGB edge dispersion is visible in the exaggerated validation build.
6. Scrolling/expanding notifications keeps refraction geometry aligned with each row.
7. Native 2dp fallback restores immediately while source is invalid; no frozen glass frame remains authoritative.
8. No Bitmap/screenshot/CPU capture path is active.
9. Producer/EGL/renderer/output-surface count remains constant with notification row count.

## Migration strategy

Do not activate every dormant subsystem at once.

Implementation order after the implementation plan is approved:

1. Extract the validated producer lifecycle from `NotificationGpuPassBlurStreamProbe` into production state/endpoint logic.
2. Integrate that lifecycle into the existing single shared `NotificationPassBlurTextureView` / `NotificationGlassSession` ownership model.
3. Add fresh-generation activation/revocation and fail-closed 2dp material handoff.
4. Activate existing Prismal rendering with an exaggerated refraction/dispersion profile and all highlights disabled.
5. Validate rollover, scrolling, expansion, and backdrop freshness on-device.
6. Only after spatial refraction is proven, tune material/highlight parameters and reduce diagnostic verbosity.

The active per-view 2dp PassBlur path remains the fallback until step 4 proves a valid first shared GPU glass frame.