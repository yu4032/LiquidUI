# systemui-001 Notification Liquid Glass Contract

Exact target evidence used by the shared notification glass feature:

- `ExpandableNotificationRow#onAttachedToWindow()` / `#onDetachedFromWindow()` are exact row lifetime hooks.
- `NotificationStackScrollLayout#dispatchDraw(Canvas)` treats stack children as `ExpandableView`; the injected glass output must therefore be a sibling, not a stack child.
- `ActivatableNotificationView#onFinishInflate()` resolves `R.id.backgroundNormal` into `mBackgroundNormal` and initializes its material.
- `NotificationBackgroundView#onDraw(Canvas)` is the row-background pixel authority. The glass node adapter mirrors its actual width/height, RTL/expand positioning, clip-empty test, bottom clip, and corner radii.
- HyperOS/MIUI and Google/native notification content wrappers diverge above the row, but both ultimately occupy the same `ExpandableNotificationRow`; the shared glass pipeline intentionally does not inspect style selection.

The feature uses the exact SystemUI target ClassLoader for these private classes and fails unsupported if any required member is absent.

## Global shade blur authority (device correction)

Device validation proved the shared notification PassBlur/Prismal compositor is producing frames, but HyperOS still applies a separate whole-shade blur behind it. Exact `systemui-001` decompilation shows two independent authorities that must be neutralized while a real notification glass scene is presented:

- `com.miui.systemui.shade.blur.ShadeBlendBlurController$BlurProvider#setBlurRatio(float)` owns MIUI background blur radius/scale for the combined `NotificationShadeWindowView` and `NotificationPanelView` providers.
- `com.miui.systemui.shade.blur.ShadeBlendBlurController$BlendBackground#setEnabled(boolean)` owns MIUI view-blur mode and blend colors for the direct `ShadeBackgroundView` children of those two containers.
- `android.view.View#setPassWindowBlurEnabled(boolean)` is driven independently by HyperOS pass-blur flows; it must be forced off only when the receiver is `NotificationShadeWindowView` or `NotificationPanelView`.
- `com.android.systemui.statusbar.BlurUtils#applyBlur(ViewRootImpl,int,boolean)` is the window-level background blur endpoint used by `ShadeWindowBlurController`; only the `NotificationShadeWindowView` root radius is rewritten to zero.

Control Center is intentionally not matched. Blur suppression becomes authoritative only after the notification compositor has successfully swapped its first GPU frame with at least one notification node, and is released when the owning session has no rows, shuts down, or fails.

Wallpaper source is a separate concern: while keyguard is showing, the shade window is expected to expose the lock-screen wallpaper below it. An unlocked reproduction that still samples the lock-screen wallpaper must be investigated as a distinct producer/source-authority bug rather than conflated with the global shade blur above.
