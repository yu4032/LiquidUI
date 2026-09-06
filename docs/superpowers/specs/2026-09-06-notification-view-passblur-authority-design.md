# Notification View-owned PassBlur Authority Design

## Scope

Target only `systemui-001` (HyperOS SystemUI 16.03.251211.r / API 101). This experiment replaces the notification material authority and PassBlur ownership path; it does not delete Prismal/OES code and does not introduce ScreenCapture.

## Evidence

HyperLight 1.1.7-2 was re-decompiled with the repository/library JADX CLI and verified as JADX 1.5.6. Its `NotificationBlurHook` uses:

- `NotificationUtil#applyElementViewBlend(...)` before/after as a ThreadLocal notification-material scope;
- `MiBlurCompat#setMiBackgroundBlendColors(View, ...)` as the exact material target dispatch;
- the exact target `View` for its GPU material application;
- `NotificationChildrenContainer#setChildrenExpanded(...)` plus `NotificationUtil#setRoundRect(...)` for final round-state handling;
- View/HWUI hidden material APIs rather than a caller-owned NotificationShade `SurfaceControl` endpoint.

Its hidden-View blur utility resolves `clearMiBackgroundBlendColor`, `setMiBackgroundBlurMode`, `setMiBackgroundBlurRadius`, `setMiViewBlurMode`, and `setPassWindowBlurEnabled` directly on `View`.

## Authority model

`setMiBackgroundBlendColors()` target is the notification material owner. `ExpandableNotificationRow` is discovered only by walking the target View's parents and supplies geometry/radius magnitude. Row attach, NSSL bootstrap scanning and wrapper reinflation are not material-target discovery authority.

`NotificationUtil#setRoundRect(...)` is the final top/bottom round-state authority. Row corner-radius getters provide radius magnitude only; the observed round state gates whether each radius participates.

## Material takeover sequence

For a target observed inside the `applyElementViewBlend` scope:

1. register the exact target View and owning row;
2. after the vendor blend setter returns, clear all five existing native blur states on that same View;
3. establish View-owned PassBlur/material on that same View;
4. keep the target's outline synchronized with SystemUI final round state.

The five-state clear is:

- `clearMiBackgroundBlendColor()`;
- `setMiBackgroundBlurMode(0)`;
- `setMiViewBlurMode(0)`;
- `setMiBackgroundBlurRadius(0)`;
- `setPassWindowBlurEnabled(false)`.

The experiment then enables PassBlur through View/HWUI (`setPassWindowBlurEnabled(true)`) and applies per-View material state. It must never bind LiquidUI's producer Surface to NotificationShade using `SetPassBlurSurface`.

## Prismal boundary

The existing OES/Prismal renderer remains in the tree for a later framework pass-texture integration, but it is not activated by this notification experiment. We do not claim the system-owned material path is already feeding Prismal. Future Prismal integration must start from framework pass texture owned by the exact notification target View, not the NotificationShade SurfaceControl.

## Failure semantics

Missing exact SystemUI classes/methods or required hidden View methods makes the hook `UNSUPPORTED`. Registration failures roll back transactionally. Runtime material application failure is fail-closed for that target and is logged; no screenshot or global shade-blur fallback is used.
