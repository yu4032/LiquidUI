# SystemUI Notification Red Background Design

## Purpose

Add the first user-visible LiquidUI SystemUI hook for exact target `systemui-001`: render notification card backgrounds as fully opaque pure red (`#FFFF0000`).

## Scope

The feature covers the outer background of notification rows rendered through `ExpandableNotificationRow -> ActivatableNotificationView -> NotificationBackgroundView`, including ordinary shade notifications, lock-screen notification rows, heads-up notification rows, grouped child rows, and media notifications when they use the standard notification-row container.

The feature does **not** modify Dynamic Island / live-activity surfaces, notification guts/detail panels, the overall notification shade/panel background, app-provided RemoteViews content backgrounds, or unrelated SystemUI cards.

## Reverse-engineering authority

Target profile: `systemui-001` (`com.android.systemui`, versionCode `202501210`, versionName `16.03.251211.r`, SDK 36).

The supplied SystemUI APK shows:

- `ExpandableNotificationRow` inherits `ActivatableNotificationView`.
- `ActivatableNotificationView` owns `mBackgroundNormal: NotificationBackgroundView`.
- `onFinishInflate()` resolves `R.id.backgroundNormal`, lets the MIUI injector initialize the drawable, then calls `updateBackgroundTint()`.
- `updateBackgroundTint(boolean)` computes the current color and routes non-animated updates through `setBackgroundTintColor(int)`.
- `setBackgroundTintColor(int)` stores `mCurrentBackgroundTint` and forwards to `mBackgroundNormal.setTint(int)`.
- MIUI can replace the row background drawable depending on blur/style state, so the hook must operate at the tint authority rather than one resource name.

## Chosen behavior

Install two exact argument-rewrite interceptors. First, force every invocation of `NotificationBackgroundView#setCustomBackground(int)` to use `R.drawable.notification_material_bg`, preventing HyperOS blur mode from substituting `notification_heads_up_transparent_bg`. Second, force every invocation of `ActivatableNotificationView#setBackgroundTintColor(int)` to receive `0xFFFF0000` instead of the framework-computed tint. Use the libxposed API 101 interceptor chain with rewritten argument arrays so normal SystemUI state bookkeeping, `NotificationBackgroundView#setTint`, roundness, Ripple, clipping, and lifecycle logic continue to execute.

This two-point hook is required because decompiled `NotificationBackgroundView#setTint(int)` calls `Drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)`, which preserves source drawable transparency and therefore cannot make the transparent blur drawable fully opaque. Do not replace the whole row background with a generic `ColorDrawable`.

## Hook boundary

Create `NotificationRedBackgroundHook` under `hook/systemui/notification/` implementing `SystemUiHook`.

Installation contract:

1. Resolve `ActivatableNotificationView`, `NotificationBackgroundView`, and `com.android.systemui.R$drawable` with the target process `ClassLoader`.
2. Resolve exact methods `ActivatableNotificationView#setBackgroundTintColor(int)` and `NotificationBackgroundView#setCustomBackground(int)`.
3. Resolve exact static field `R.drawable.notification_material_bg`.
4. Register two highest-priority API101 argument-rewrite interceptors: material-background resource rewrite and opaque-red tint rewrite.
5. Return `INSTALLED` only after both hook registrations succeed.
6. Return `UNSUPPORTED` when any exact class/method/resource contract is absent.
7. Return `FAILED` for access/framework hook-registration failures.

The feature is registered explicitly in `ModuleMain`'s `SystemUiHookRegistry`. No fuzzy class-name fallback is allowed.

## Safety and failure semantics

The exact build gate remains authoritative. The hook is never attempted on unsupported SystemUI builds. The interceptor changes only the single integer argument and otherwise proceeds normally. It must not swallow exceptions from SystemUI's original method.

## Testing

Add SDK-independent contract tests for:

- red color constant is exactly `0xFFFF0000`;
- argument rewrite always produces opaque red regardless of original tint;
- hook ID is stable and notification-specific;
- `ModuleMain` registers the notification hook instead of an empty registry;
- target private class resolution uses the supplied target `ClassLoader`;
- no resource-name or broad package scan fallback is introduced.

Then run the full contract suite and GitHub Actions `testDebugUnitTest assembleDebug`. A successful build is required before considering the change ready for device testing.
