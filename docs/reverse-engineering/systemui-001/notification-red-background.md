# Notification red background contract

## Target

This contract applies only to `systemui-001`:

- package `com.android.systemui`
- versionCode `202501210`
- versionName `16.03.251211.r`
- SDK 36
- APK SHA-256 `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`

The implementation was derived from targeted JADX 1.5.6 decompilation of the supplied APK. No vendor source is committed.

## Authoritative row path

Targeted JADX also confirms `ExpandableNotificationRow extends ActivatableNotificationView`, so the standard row hierarchy shares this background authority.

`ActivatableNotificationView` owns the outer notification-row background:

```text
ActivatableNotificationView.onFinishInflate()
  -> mBackgroundNormal = findViewById(R.id.backgroundNormal)
  -> getInjector().initBackground()
  -> updateBackgroundTint()

ActivatableNotificationView.updateBackgroundTint(boolean)
  -> calculateBgColor(true)
  -> setBackgroundTintColor(int)
  -> mBackgroundNormal.setTint(int)
```

The exact tint method is:

```text
ActivatableNotificationView#setBackgroundTintColor(int)
```

and `NotificationBackgroundView#setTint(int)` applies a color filter with `PorterDuff.Mode.SRC_ATOP`.

## Why tint-only is insufficient

The MIUI injector changes the source drawable according to blur state:

```text
ActivatableNotificationViewInjector.initBackground()
  blur opened  -> NotificationBackgroundView.setCustomBackground(
                      R.drawable.notification_heads_up_transparent_bg)
  blur closed  -> NotificationBackgroundView.setCustomBackground(
                      R.drawable.notification_material_bg)
```

`SRC_ATOP` preserves source drawable transparency. Therefore forcing only the tint argument to red can leave the blur-mode notification background transparent. That does not satisfy the requested fully opaque `#FFFF0000` behavior.

## LiquidUI hook contract

`NotificationRedBackgroundHook` installs two exact, highest-priority int-argument interceptors:

1. `NotificationBackgroundView#setCustomBackground(int)`
   - rewrite argument 0 to the exact runtime value of `R.drawable.notification_material_bg`;
   - resolve the resource ID from target `com.android.systemui.R$drawable`, never from a hard-coded integer.
2. `ActivatableNotificationView#setBackgroundTintColor(int)`
   - rewrite argument 0 to `0xFFFF0000`.

Both target classes and `R$drawable` are resolved through the target SystemUI `ClassLoader`. If any exact class, method, or resource field is absent, installation is `UNSUPPORTED`. If hook registration fails after the first interceptor is installed, the first registration is rolled back and the aggregate result is `FAILED`.

## Preserved SystemUI behavior

The hook does not replace `NotificationBackgroundView`, its drawable drawing code, or the row container. SystemUI remains authoritative for:

- notification corner radii;
- Ripple state;
- clip top/bottom amounts;
- actual row width/height;
- expansion animation size;
- notification lifecycle and row reuse.

## Explicit exclusions

This first feature does not claim to recolor:

- Dynamic Island / live-activity surfaces;
- notification guts/detail controls;
- the overall notification shade/panel background;
- arbitrary backgrounds drawn inside app-provided RemoteViews;
- unrelated SystemUI cards.

It targets the standard outer notification-row background shared by the normal notification row hierarchy.
