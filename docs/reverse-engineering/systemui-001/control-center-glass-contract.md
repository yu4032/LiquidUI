# systemui-001 Control Center / Quick Settings Glass Contract

Target provenance:

- package: `com.android.systemui`
- profile: `systemui-001`
- versionCode: `202501210`
- versionName: `16.03.251211.r`
- APK size: `52843421`
- APK SHA-256: `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`
- decompiler: JADX 1.5.6

## Scope split discovered during reverse engineering

HyperOS has two different paths and they must not be conflated.

### Built-in classic Quick Settings path — verified in this APK

`com.android.systemui.qs.MiuiQSPanel#setTiles(Collection, boolean)` is the authoritative tile-set reconstruction point.

The method:

1. iterates every existing `MiuiQSPanel.TileRecord`;
2. calls `mTileLayout.removeTile(record)` and removes its tile callback;
3. clears `mRecords`;
4. creates a new `MiuiQSTileView(context, new MiuiQSIconViewImpl(context), collapsed)` for each `QSTile`;
5. initializes the tile view, adds it to `mRecords`, then adds it to the current tile layout.

`MiuiQSPanel#setTiles(Collection)` explicitly returns early when `ControlCenterSettingsRepository.useControlCenter == true`, therefore this built-in path is classic QS/shade and is **not** the new HyperOS Control Center implementation.

Verified bounded material host:

- tile view: `com.android.systemui.qs.tileimpl.MiuiQSTileView`
- base: `com.android.systemui.qs.tileimpl.MiuiQSTileBaseView`
- bounded host field: `MiuiQSTileBaseView.mIconFrame`
- host type: `com.miui.systemui.animation.view.MiuiLaunchableFrameLayout`
- host size authority: `R.dimen.qs_tile_icon_bg_size`
- semantic material: `TILE`
- geometry: circle; runtime radius is half the bounded host size

Native material authority:

- icon wrapper: `com.android.systemui.qs.tileimpl.MiuiQSIconViewImpl`
- image field: `MiuiQSIconViewImpl.mIcon`
- state update authority: `MiuiQSIconViewImpl#updateIcon(ImageView, QSTile.State, boolean, boolean)`
- normal output: a two-layer `LayerDrawable` containing one `ic_qs_bg_*` background plus the foreground icon
- animated active/inactive transition: a three-layer `LayerDrawable`; background layers are indices `0..last-1`, foreground icon is the final layer
- background animator field: `MiuiQSIconViewImpl.mAnimator`; it animates only the enabled background drawable alpha

LiquidUI must preserve the foreground icon and LayerDrawable sizing/gravity. Suppression therefore sets only native background-layer alpha to zero; it never replaces/stretches the foreground icon. While shared glass is authorized, a post-`updateIcon` callback re-applies suppression to each newly rebuilt LayerDrawable and cancels only the background animator. On revocation/failure/detach, current background layers are restored to their native final state.

Lifecycle contract:

- after `MiuiQSPanel#setTiles(Collection, boolean)`: reconcile exact current `mRecords` set;
- any previously registered host absent from the new set: unregister/restore immediately;
- any new record: register `mIconFrame` with shared `SystemUiGlassHostController`;
- after `MiuiQSIconViewImpl#updateIcon(...)`: refresh native-material suppression for that exact icon wrapper only;
- no recursive View search, no delayed cleanup, no component-owned renderer/source.

### HyperOS new Control Center path — external plugin, not yet safe to hook

This APK contains only the plugin API:

- interface: `com.android.systemui.plugins.miui.controlcenter.ControlCenterPlugin`
- plugin action: `com.android.systemui.action.PLUGIN_MIUI_CONTROL_CENTER`
- plugin class-loader allowlist/root: `com.miui.systemui.plugin`

The actual new Control Center material Views are implemented in the separate `com.miui.systemui.plugin` APK, not in this `MiuiSystemUI.apk`. That plugin APK is not present in the currently verified reverse-engineering inputs. Production hooks for the new Control Center remain deliberately absent until the exact plugin APK/version is available and separately pinned.

## Explicit exclusions

- no full QS panel/root glass node;
- no status/shade scrim or wallpaper layer;
- no text/icon-only child as a glass host;
- no `Class.forName` vendor lookup;
- no fuzzy name matching or recursive View-tree scanning;
- no EGL/OES/PassBlur/SurfaceTexture/PixelCopy/ScreenCapture ownership outside shared core.
