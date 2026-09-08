# systemui-001 Status/Floating Glass Contract

Target: `com.android.systemui` `versionCode=202501210`, `versionName=16.03.251211.r`, SDK 36.

This contract is intentionally narrow. It accepts only the bounded ongoing-activity chip material that is fully proven by the verified SystemUI decompilation. Dynamic Island, privacy prompts, full-width status-bar roots, icon containers, heads-up roots, and other floating surfaces remain excluded until their own exact material/lifecycle contracts are proven.

## Accepted host: Ongoing Activity Chip

### View hierarchy authority

`res/layout/status_bar.xml` uses `com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView` as the status-bar root and includes two bounded chips:

- `@id/ongoing_activity_chip_primary` -> `@layout/ongoing_activity_chip_primary`
- `@id/ongoing_activity_chip_secondary` -> `@layout/ongoing_activity_chip_secondary`

Both layouts wrap `@layout/ongoing_activity_chip_content`. The exact bounded material host inside that content is:

- class: `com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer`
- id: `@id/ongoing_activity_chip_background`
- background: `@drawable/ongoing_activity_chip_bg`
- corner radius resource: `@dimen/ongoing_activity_chip_corner_radius` (28dp in this target)

The drawable is a `GradientDrawable` shape. Icons, chronometer, text, date/time children are foreground content and are never registered as glass nodes.

### Final native-material authority

`com.android.systemui.statusbar.chips.ui.binder.OngoingActivityChipBinder.bind(...)` is the exact final material update authority for a live chip. Before returning it:

1. resolves `OngoingActivityChipViewBinding.backgroundView`;
2. updates chip foreground content;
3. obtains the background as `GradientDrawable`;
4. writes the final background color from `ColorsModel.background(context)`;
5. writes the final outline/stroke.

LiquidUI must run after this method so suppression is applied only after the vendor has written the current native material tuple.

### Membership/lifecycle authority

`OngoingActivityChipBinder.bind(...)` receives `OngoingActivityChipModel`:

- `OngoingActivityChipModel.Active` => the bounded chip is a live candidate;
- `OngoingActivityChipModel.Inactive` => unregister and restore synchronously.

`HomeStatusBarViewBinderImpl` creates bindings for both primary and secondary chip roots and calls `OngoingActivityChipBinder.bind(...)` whenever the model changes. The root status-bar binding itself is driven by `repeatWhenAttached`, so chip membership is not timer-based.

A host detach also revokes/restores the current chip lifecycle. No fixed delay is permitted.

## Window renderer authority

The status bar is a different Window/ViewRoot from `NotificationShadeWindowView`; it cannot reuse the Shade renderer.

The exact Window root is `com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView`. It inherits `PhoneStatusBarView.onAttachedToWindow()` / `onDetachedFromWindow()` and is the root inflated by `status_bar.xml`.

`StatusBarWindowGlassAuthority` is target-specific discovery/validation only. It may establish the status-bar Window host only by delegating to the shared core-owned `VerifiedWindowRendererAuthority`; the statusbar domain itself never creates or implements a renderer/producer pipeline.

The verified authority path must:

- accept only an exact attached `MiuiPhoneStatusBarView` root;
- use root index 0 as the safe renderer lane, below all native status-bar foreground content;
- delegate renderer establishment to the process-global `SystemUiGlassCore` / `WindowGlassSession` through the shared core helper;
- reuse an existing Window scene host if already present;
- never implement EGL/OES/SurfaceTexture/native PassBlur APIs in the statusbar domain;
- retire the target-specific authority object with the Window root lifecycle.

Component adapters never create a second renderer.

## Native material suppression

The accepted host is the `ChipBackgroundContainer` itself. Suppression is limited to its background drawable:

- capture the current background drawable identity and alpha before first suppression for a lifecycle;
- set only the drawable alpha to 0 after presentation authorization;
- if a later `bind(...)` updates/replaces the drawable, refresh the captured tuple and reapply suppression;
- restore the captured alpha synchronously on revoke/inactive/detach/terminal failure.

Do not hide the chip View, do not change child alpha, and do not mutate text/icon content.

## Glass mapping

- domain: `STATUS_BAR`
- host kind: `STATUS_CAPSULE`
- optical profile: existing `FLOATING`
- geometry: exact `ChipBackgroundContainer` bounds in Window coordinates
- radius: `@dimen/ongoing_activity_chip_corner_radius`
- opacity: 1.0

## Explicit exclusions

The following are not glass nodes in this task:

- `MiuiPhoneStatusBarView` / entire status bar;
- `status_bar_icons`, `status_bar_contents`, left/right icon containers;
- notification icon areas;
- chip icon/text/time/date children;
- `FocusedNotifPromptView`;
- Dynamic Island roots/content;
- privacy-dot/full-screen privacy roots;
- heads-up full-row/root surfaces.

Those surfaces require separate exact material and lifecycle proof before registration.