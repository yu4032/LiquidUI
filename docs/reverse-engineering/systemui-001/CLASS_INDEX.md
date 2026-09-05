# systemui-001 class index

This index records only classes actually inspected for LiquidUI. It is intentionally not a dump of the vendor source tree.

| Class | DEX | Evidence / role | LiquidUI dependency |
| --- | --- | --- | --- |
| `com.android.systemui.SystemUIApplication` | `classes.dex` | SystemUI `Application`; targeted JADX 1.5.6 decompilation completed | Exact bootstrap structural probe: declared `onCreate()` |

## SystemUIApplication

Verified declaration-level contract:

```text
com.android.systemui.SystemUIApplication
  extends android.app.Application
  declared method: onCreate(): void
```

Observed startup responsibilities include dependency injection, boot-complete receiver setup, configuration initialization, secondary-user handling, and publication of application context. LiquidUI currently depends only on the declaration of `onCreate()` for target fingerprinting; it does not hook or alter this method in the bootstrap milestone.

## Expansion policy

Add a class here only when a concrete feature investigation has read its decompiled implementation or bytecode context. For each actual feature, add a separate `<feature>-contract.md` containing the authoritative call path, fields/methods relied upon, failure semantics, structural probes, and validation evidence.

## Notification row final-render classes

| Class | DEX | Evidence / role | LiquidUI dependency |
| --- | --- | --- | --- |
| `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow` | `classes2.dex` | Shared outer row for both notification styles | Parent guard for final draw |
| `com.android.systemui.statusbar.notification.row.NotificationBackgroundView` | `classes2.dex` | `onDraw(Canvas)` ends in current `mBackground.draw(canvas)` | Final pixel hook + exact `mBackground` field |
| `com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper` | `classes2.dex` | `wrap(...)` contains MIUI/native style split; base `onReinflated()` manages content background | Native/inherited reinflate hook |
| `...wrapper.MiuiNotificationTemplateViewWrapper` | `classes2.dex` | HyperOS `onReinflated()` override; calls super | Direct HyperOS reinflate hook |
| `...wrapper.MiuiNotificationBigTextViewWrapper` | `classes2.dex` | HyperOS big-text `onReinflated()` override; calls super | Direct HyperOS reinflate hook |
| `...wrapper.MiuiNotificationCustomViewWrapper` | `classes2.dex` | HyperOS custom `onReinflated()` override; calls super | Direct HyperOS reinflate hook |
| `com.miui.systemui.util.MiBlurCompat` | target SystemUI | Exact vendor helpers to disable view blur and clear background blend colors | Final-draw blur cleanup |
