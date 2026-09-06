# Notification Shared Liquid Glass Design

Date: 2026-09-05
Target: `systemui-001` (`com.android.systemui`, version `16.03.251211.r`, SDK 36)
Source pipeline: `yu4032/LiquidDock@2195546df3f56952f14f3110ae1443feeefb145e`

## Goal

Replace the visible background material of every currently visible `ExpandableNotificationRow`
with one shared HyperOS PassBlur -> OES -> Prismal liquid-glass scene, without branching on
HyperOS/MIUI versus Google/native notification content style.

## Ownership

One `NotificationStackScrollLayout` (NSSL) root owns exactly one `NotificationGlassSession`.
The session owns exactly one PassBlur producer endpoint, one `SurfaceTexture`/external-OES input,
one EGL context/thread, one `PrismalRenderer`, and one `TextureView` output.
Notification rows are immutable geometry nodes only; row count must not create producer/EGL/Surface
count growth.

The output host is a sibling inserted immediately before NSSL in NSSL's parent. It is not an NSSL
child: the exact target NSSL `dispatchDraw()` assumes its children are `ExpandableView` instances.
NSSL therefore remains above the shared glass output and continues drawing notification content.

## Pixel pipeline

```
NotificationShade ViewRoot SurfaceControl
  -> SetPassBlurSurface(root, producerSurface)
  -> SurfaceTexture external OES
  -> Stage-A normalize/crop/rotation into one 2D FBO
  -> PrismalRenderer.prepareBackdrop() once per source frame
  -> PrismalRenderer.beginGlassFrame()
  -> drawGlass(row_1 ... row_N)
  -> one transparent Prismal scene texture
  -> one composite into the shared TextureView
  -> one EGL swap
```

The GPU path is zero-copy with respect to application code. PixelCopy, ImageReader, Bitmap capture,
`glReadPixels`, MediaProjection, screenshot/screen-recording fallbacks are forbidden.

## Notification geometry authority

`ExpandableNotificationRow#mBackgroundNormal` is the exact target geometry authority.
`NotificationBackgroundView.onDraw()` proves the relevant state: actual width/height,
`mClipTopAmount`, `mClipBottomAmount`, `mCornerRadii`, expansion width/height, RTL placement.
The UI thread converts those fields and screen coordinates into immutable `NotificationGlassNode`
snapshots. The GL thread never retains SystemUI `View` objects.

`systemui-001` `ActivatableNotificationView` initializes `mBackgroundNormal`; no speculative
`mBackgroundDimmed` field is added to this exact profile.

## Fail-closed material handoff

Vendor notification material remains visible while PassBlur is binding and until a successful frame
has completed OES consumption, Prismal drawing, and EGL swap with at least one drawable row node.
Only that first valid material frame grants `NotificationGlassSession` presentation authority.
Then the session disables MIUI blur/blend on `mBackgroundNormal`, sets that background alpha to zero,
and clears wrapper content-root backgrounds that would cover the shared glass.

Any terminal bind/EGL/GL failure restores every material owned by that session and removes the shared
host. Material-restore maps are session-local so one display/root cannot restore another root's state.

## Endpoint correctness

Every input BufferQueue endpoint has a monotonically increasing generation. Async bind completion is
accepted only if its captured endpoint generation still equals the published current generation and
the ViewRoot SurfaceControl identity has not changed. A retired producer may never recommit.

## Explicit non-scope

No Launcher HOME/Recents/Workstation/unlock lifecycle is copied. No per-row producer or TextureView.
No notification-style branch. No user-tunable optical parameters in this phase; use LiquidDock's
validated default Prismal material to isolate pipeline correctness.
