# Notification red background contract

## Exact target

This investigation applies only to `systemui-001`:

- package `com.android.systemui`
- versionCode `202501210`
- versionName `16.03.251211.r`
- SDK 36
- supplied APK SHA-256 `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`

Evidence comes from targeted/full `classes2.dex` JADX 1.5.6 decompilation plus decoded APK resources. No vendor source or APK is committed.

## Two notification presentation stacks

`NotificationViewWrapper.wrap(...)` is the concrete content-style split. It checks `NotificationSettingsHelper.showMiuiStyle()` and can construct either HyperOS/MIUI wrappers (`MiuiNotification*ViewWrapper`) or the framework/native `Notification*TemplateViewWrapper` family.

For the exact build, all declared `onReinflated()` implementations under the notification wrapper package are:

1. `NotificationViewWrapper#onReinflated()`
2. `MiuiNotificationTemplateViewWrapper#onReinflated()`
3. `MiuiNotificationBigTextViewWrapper#onReinflated()`
4. `MiuiNotificationCustomViewWrapper#onReinflated()`

The three HyperOS overrides all call `super.onReinflated()`, but LiquidUI hooks every declaration directly so correctness does not depend on ART preserving those `super` calls as non-inlined calls.

## Shared outer row authority

Both content stacks are attached to the same `ExpandableNotificationRow` hierarchy. `ActivatableNotificationView` resolves `mBackgroundNormal` as a `NotificationBackgroundView`, and the decoded resources used by the HyperOS row state machine include:

- `notification_item_bg.xml`: rounded opaque shape
- `notification_heads_up_bg.xml`: rounded shape plus stroke
- `notification_heads_up_transparent_bg.xml`: rounded transparent shape
- `notification_material_bg.xml`: layer-list used by the AOSP/material path

The earlier v1/v2 implementation hooked intermediate setters/resource selection. That was insufficient as a runtime correctness contract because those are not the final pixel authority and short setters/callers may be inlined by ART.

## Final pixel authority

`NotificationBackgroundView#onDraw(Canvas)` is the final outer-row background draw path. Decompiled code performs clipping/bounds bookkeeping and then, if `mBackground != null`, ends with:

```text
mBackground.setBounds(...)
mBackground.draw(canvas)
```

There is no later SystemUI notification-card color computation after this draw call. Therefore the v3 hook intercepts this exact final-render method. It acts only when `NotificationBackgroundView#getParent()` is an `ExpandableNotificationRow` so unrelated SystemUI uses of the class are not recolored.

Immediately before the original `onDraw` proceeds, LiquidUI:

1. calls `MiBlurCompat.setMiViewBlurModeCompat(0, backgroundView)`;
2. calls `MiBlurCompat.clearMiBackgroundBlendColorCompat(backgroundView)`;
3. reads the exact private `mBackground` field;
4. forces its rendered fill to opaque `0xFFFF0000` while preserving its existing geometry:
   - `GradientDrawable`: `setColor(0xFFFF0000)`;
   - `LayerDrawable`: recursively recolor every child;
   - other `Drawable`: `setTint(0xFFFF0000)`;
   - every drawable receives `setAlpha(255)`.

This preserves the vendor-selected rounded drawable, dimensions, clipping, expansion animation bounds, and ripple/container lifecycle while taking authority over the final color pixels.

## Content-root coverage for HyperOS and native notifications

A notification RemoteViews/content root is drawn above the outer `NotificationBackgroundView`. `NotificationViewWrapper#onReinflated()` normally records an opaque `ColorDrawable` as `mBackgroundColor` and clears the content background only when `getBackgroundColor()` returns non-zero. In this build, `getBackgroundColor()` deliberately returns zero while MIUI background blur is enabled, so a content-root background can otherwise remain above the red outer row (notably colorized/custom notifications).

LiquidUI therefore also intercepts every exact `onReinflated()` declaration listed above and, before the vendor implementation proceeds, calls `mView.setBackgroundResource(0)` when `mRow` is an `ExpandableNotificationRow`.

This gives direct coverage to both presentation stacks:

```text
HyperOS/MIUI wrappers ─┐
                      ├─ content root -> transparent
Native/Google wrappers┘
                              +
ExpandableNotificationRow -> NotificationBackgroundView.onDraw()
                              -> opaque red final drawable
```

## Rejected intermediate approaches

The following v1/v2 hook points are intentionally removed from production code:

- `NotificationBackgroundView#setCustomBackground(int)`
- `ActivatableNotificationView#setBackgroundTintColor(int)`
- `ExpandableNotificationRowInjector#updateBlurBg(int,int,boolean)`

They remain useful reverse-engineering evidence but are not final-render authority. No int/boolean argument-hook backend remains in the production tree.

## Failure semantics

The feature remains fail-closed:

- any missing exact target class/method/field -> `UNSUPPORTED`;
- hook registration/reflection failure -> `FAILED`;
- all hook registrations are transactional and rolled back if a later registration fails;
- all SystemUI-private classes are resolved through the target process ClassLoader;
- no fuzzy fallback, resource scan, or fixed delay is used.

## Explicit exclusions

This feature targets notification rows. It does not claim to recolor:

- Dynamic Island/live-activity surfaces;
- notification guts/detail controls;
- the overall NotificationShade panel/background blur;
- unrelated SystemUI cards or controls.
