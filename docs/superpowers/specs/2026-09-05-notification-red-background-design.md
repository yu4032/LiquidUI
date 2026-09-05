# SystemUI Notification Red Background Design

## Goal

On exact target `systemui-001`, force the visible background of notification rows to fully opaque pure red `#FFFF0000`, for both HyperOS/MIUI-style and Google/native-style notifications.

## Reverse-engineered architecture

`NotificationViewWrapper.wrap(...)` proves two content presentation stacks selected around `NotificationSettingsHelper.showMiuiStyle()`. HyperOS uses `MiuiNotification*ViewWrapper` classes while native notifications use the standard `Notification*TemplateViewWrapper` family.

Both stacks converge on `ExpandableNotificationRow`. The final outer background pixel authority is `NotificationBackgroundView#onDraw(Canvas)`, which draws the current private `mBackground` drawable directly to the Canvas after bounds/clipping setup.

The content root is above that background. `NotificationViewWrapper#onReinflated()` and three HyperOS overrides are the exact reinflation declarations in this build. They are therefore the second authority needed to prevent an opaque/colorized content-root background from covering the red row.

## Chosen implementation

Install exact highest-priority before-method interceptors for:

1. `NotificationBackgroundView#onDraw(Canvas)`
2. `NotificationViewWrapper#onReinflated()`
3. `MiuiNotificationTemplateViewWrapper#onReinflated()`
4. `MiuiNotificationBigTextViewWrapper#onReinflated()`
5. `MiuiNotificationCustomViewWrapper#onReinflated()`

At `onDraw`, only when the parent is an `ExpandableNotificationRow`, disable view blur/clear MIUI blend state and recolor the current actual `mBackground` drawable to opaque red. Handle `GradientDrawable` and `LayerDrawable` explicitly and use generic drawable tint as the fallback.

At every reinflation declaration, only when `mRow` is an `ExpandableNotificationRow`, clear `mView`'s background resource before the vendor code continues.

## Why v1/v2 are removed

The previous implementation intercepted `setCustomBackground(int)`, `setBackgroundTintColor(int)`, and `updateBlurBg(..., boolean)`. Those methods describe intermediate state but do not own the final pixels; they are also short enough that ART inlining can bypass a method-entry hook even though registration succeeds.

The final-render design does not depend on those setters or a particular selected drawable resource.

## Constraints

- exact build gate only (`systemui-001`)
- target process ClassLoader for private/vendor classes
- no fuzzy fallback or resource-name scan
- no fixed delays
- no whole-row View replacement
- transactional hook installation
- diagnostics may log the first runtime hit of each exact method when enabled
- Dynamic Island, guts/detail UI, and full NotificationShade panel background are out of scope
