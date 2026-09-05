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

## Notification row background classes

| Class | DEX | Evidence / role | LiquidUI dependency |
| --- | --- | --- | --- |
| `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow` | `classes2.dex` | Declared as `extends ActivatableNotificationView`; standard notification row container | Establishes row coverage through shared base |
| `com.android.systemui.statusbar.notification.row.ActivatableNotificationView` | `classes2.dex` | Owns `mBackgroundNormal`; `updateBackgroundTint(boolean)` routes into `setBackgroundTintColor(int)` | Exact tint argument hook |
| `com.android.systemui.statusbar.notification.row.NotificationBackgroundView` | `classes2.dex` | Owns drawable; `setCustomBackground(int)` selects resource; `setTint(int)` applies `PorterDuff.Mode.SRC_ATOP` | Exact background-resource argument hook |
| `com.android.systemui.statusbar.notification.row.ActivatableNotificationViewInjector` | `classes2.dex` | Chooses `notification_heads_up_transparent_bg` when MIUI background blur is open, otherwise `notification_material_bg` | Explains why tint-only cannot guarantee opacity |
