# systemui-001 notification PassBlur authority

Exact target: `com.android.systemui` `16.03.251211.r` / SDK 36.

## Decompiled authority

HyperOS owns notification PassBlur state in
`com.miui.systemui.shade.blur.ShadeBlendBlurController`.
The controller exposes `notifPassBlur: ReadonlyStateFlow`.
`ShadeBlendBlurController$start$6$6` collects that flow and the collector writes the
same boolean to `notificationBlurProvider.passBlur`, then calls
`notificationBlurProvider.view.setPassWindowBlurEnabled(value)`.

The notification authority is distinct from `ctrlPassBlur`, which drives the Control Center
provider. LiquidUI therefore observes only the `NotificationPanelView` consumer of
`setPassWindowBlurEnabled(boolean)` and never branches on notification content style.

## LiquidUI contract

- Unknown vendor authority is fail-closed: no PassBlur producer binding.
- `setPassWindowBlurEnabled(requested)` is observed before proceeding and is not rewritten.
- LiquidUI does not modify `BlurProvider.passBlur` or `BlurProvider.enabled`.
- Strong shade blur can still be neutralized through blur radius/mode/blend authorities without
  changing the vendor PassBlur boolean.
- `SetPassBlurSurface` and `setUpdateTextureFlag` are allowed only while vendor `notifPassBlur`
  is true.
- When vendor authority becomes false, the current binding is unbound immediately, its producer
  endpoint is retired, and a fresh generation is used on the next true transition.
- LiquidUI does not set custom `setMiBlurWinExc` exclusions for NotificationShade; source
  selection stays with HyperOS.

## Content source authority correction

Device testing showed that using the NotificationShade window's own ViewRoot SurfaceControl as the
`SetPassBlurSurface` source returns the wallpaper layer even when `notifPassBlur` is correct. The
boolean flow is therefore only an enable/disable authority; it is not a source SurfaceControl.

The exact SystemUI build embeds `com.android.wm.shell.RootTaskDisplayAreaOrganizer`. Its feature-1
organizer owns `mLeashes[displayId]` for the Root Task Display Area. That leash contains Home/app
tasks and is structurally above wallpaper content. LiquidUI observes
`onDisplayAreaAppeared(DisplayAreaInfo, SurfaceControl)` / `onDisplayAreaVanished(DisplayAreaInfo)`
and uses the matching display leash as the PassBlur source. The NotificationShade ViewRoot remains
only the geometry/output-host authority.

Source leases are generation-stamped. A vanish/change invalidates the old frame and binding; a new
leash requires a fresh producer generation before sampling resumes.

## Native notification roundness

`ActivatableNotificationView.applyRoundnessAndInvalidate()` calls
`mBackgroundNormal.setRadius(getTopCornerRadius(), getBottomCornerRadius())`. Therefore the Row's
`RoundableState` is the live radius authority. LiquidUI reads `getTopCornerRadius()` and
`getBottomCornerRadius()` directly for every scene snapshot; it no longer reads
`NotificationBackgroundView.mCornerRadii`, which can represent stale/intermediate background state.
