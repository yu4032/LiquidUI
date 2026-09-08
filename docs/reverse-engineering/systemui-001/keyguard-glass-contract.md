# systemui-001 Keyguard Bounded Glass Contract

Target: `com.android.systemui` `versionCode=202501210`, `versionName=16.03.251211.r`, SDK 36.

Reverse-engineering authority: `yu4032/hyperos-analysis`, input set `systemui-16.03.251211.r`, analysis identity `c22ec79c33e691d3b82c2563ef302209c670d82a0c99115ddc09d57decf1ca0d`, canonical JADX 1.5.6 analysis.

This contract is intentionally narrow. It accepts only the two bounded Keyguard quick-affordance buttons. Keyguard roots, wallpaper/dim layers, scrims, bouncer/security roots, clocks, notification roots, AOD canvases, and full-screen surfaces remain excluded.

## Exact host creation authority

`com.android.systemui.keyguard.ui.view.layout.sections.DefaultShortcutsSection.addViews(ConstraintLayout)` creates the two accepted hosts directly:

- `R.id.start_button`
- `R.id.end_button`

Both are `com.android.systemui.animation.view.LaunchableImageView` instances. During creation the method assigns:

- background: `R.drawable.keyguard_bottom_affordance_bg`
- foreground: `R.drawable.keyguard_bottom_affordance_selected_border`
- fixed padding from `R.dimen.keyguard_affordance_fixed_padding`

`applyConstraints(...)` gives both buttons their exact fixed dimensions and bottom/start/end placement.

## Exact material geometry

`res/drawable/keyguard_bottom_affordance_bg.xml` is a bounded rectangle shape with:

- width: `keyguard_affordance_fixed_width`
- height: `keyguard_affordance_fixed_height`
- radius: `keyguard_affordance_fixed_radius`

On this target `keyguard_affordance_fixed_radius` is `24dp`.

LiquidUI maps each button to:

- domain: `KEYGUARD`
- host kind: `KEYGUARD_TILE`
- geometry: exact button View bounds in Window coordinates
- radius: current `keyguard_affordance_fixed_radius`
- opacity: 1.0

## Runtime material updates

`KeyguardQuickAffordanceViewBinder` does not replace the button background drawable during normal state updates. Its collected `KeyguardQuickAffordanceViewModel` updates:

- visibility;
- foreground icon/tint;
- activation/selection/click state;
- `setBackgroundTintList(...)` on the existing background;
- View scale/alpha.

Therefore LiquidUI does not hook coroutine collectors or synthetic `emit()` implementations. The vendor remains authoritative for tint and foreground content.

After exact presentation authorization LiquidUI suppresses only the current background drawable alpha. It never changes button visibility, child/icon alpha, foreground, background tint, click handling, scale, or translation. Restore returns only the captured drawable alpha, preserving any vendor tint changes made while the glass node was active.

## Lifecycle authority

`DefaultShortcutsSection.removeViews(ConstraintLayout)`:

1. disposes left/right binder handles;
2. clears the start-button inset listener;
3. removes `start_button`;
4. removes `end_button`.

LiquidUI discovers the hosts after `addViews()` and clears its section membership after `removeViews()`. Actual Window registration is deferred until each button is attached; detach synchronously unregisters the node and restores native material. No fixed delay or polling is used.

## Window renderer authority

`super_notification_shade.xml` contains the Keyguard root (`keyguard_root_view`) inside the same `NotificationShadeWindowView` used by the verified notification/control-center glass path.

Therefore Keyguard quick affordances reuse the already-established Shade Window renderer from `ShadeWindowGlassAuthority`. The Keyguard domain must never call `attachRenderer`, open a PassBlur producer, own EGL/OES/SurfaceTexture state, or create a second Window renderer.

If the shared Shade renderer is unavailable, registration fails closed and the native button material remains/restores visible.

## Explicit exclusions

The following must never become Keyguard glass nodes in this task:

- `KeyguardRootView` / `HyperOSKeyguardRootView`;
- wallpaper or wallpaper dim layers;
- all scrims;
- bouncer and security container roots;
- password/PIN/pattern surfaces;
- full-screen AOD/root canvases;
- Keyguard notification stack/root;
- clock/Smartspace roots;
- camera preview/root surfaces.

Any future Keyguard surface requires its own bounded host, material authority, lifecycle authority, and renderer ownership proof before implementation.
