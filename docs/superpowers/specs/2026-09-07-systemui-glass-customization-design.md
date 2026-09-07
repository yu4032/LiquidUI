# SystemUI Glass Customization Design

## Goal

Bring LiquidDock-grade Liquid Glass customization to LiquidUI while preserving the Phase 0 SystemUI ownership model: one process-global `SystemUiGlassCore`, one render thread, one PassBlur/OES producer endpoint per Window, and component adapters that only submit semantic nodes.

The user-visible model is:

- one **Global** glass style;
- optional independent overrides for `CARD`, `TILE`, `SLIDER`, `PANEL`, and `FLOATING`;
- a complete Miuix Compose GUI;
- live preview while sliders are dragged;
- no SystemUI restart, producer rebuild, root rebind, or EGL recreation for an optical-only change.

## Scope

### Included active optical controls

LiquidUI ports the controls that the current zero-copy Prismal path actually consumes:

- blur radius;
- glass thickness;
- IOR;
- normal strength;
- liquid dome;
- lens refraction scale and lens depth;
- chromatic aberration;
- refraction inset;
- displacement scale;
- height transition width;
- SMin smoothing;
- edge refraction falloff;
- Fresnel reflect;
- red/blue dispersion;
- vibrancy;
- plain highlight;
- brightness;
- highlight width;
- light direction X/Y;
- specular strength/sharpness;
- rim light;
- caustics;
- tint RGBA;
- inner-shadow RGBA and softness;
- transmittance;
- backdrop scale X/Y;
- parallax scale;
- show-normals debug mode.

Global pipeline controls also expose manual sampling extras for top/bottom/left/right.

### Explicitly excluded

Do not port LiquidDock legacy capture/readback controls such as capture FPS, capture scale, dynamic bitmap capture, black-frame filtering, capture stop delay, or SurfaceFlinger/readback tuning. LiquidUI's SystemUI core remains zero-copy PassBlur -> OES -> Prismal.

Do not add per-profile sampling-extra values. Sampling authority is Window/producer-wide. Automatic guard calculation may use the strongest active profile in the scene, while the four manual sampling extras remain one global pipeline setting.

## Configuration model

### Storage

Use typed SharedPreferences values mirrored through API101 Remote Preferences.

`ConfigKey<T>` gains a value kind and optional numeric bounds. Existing boolean keys keep their names and behavior.

Create a pure-Java `GlassParameter` enum. Each item defines:

- stable suffix;
- default numeric value;
- minimum/maximum;
- display unit/scale metadata needed by the GUI;
- category (`BASIC`, `REFRACTION`, `COLOR`, `LIGHTING`, `DEBUG`).

Global keys use `glass_global_<suffix>`.

Profile overrides use:

- `glass_card_override`, `glass_tile_override`, `glass_slider_override`, `glass_panel_override`, `glass_floating_override`;
- `glass_<profile>_<suffix>` for the overridden values.

Global sampling keys are `glass_sampling_extra_top`, `glass_sampling_extra_bottom`, `glass_sampling_extra_left`, and `glass_sampling_extra_right`.

### Immutable runtime state

Create `GlassStyleConfig`, an Android-free immutable snapshot containing:

- global parameter values;
- five profile override states/value maps;
- four sampling extras.

A profile with override disabled resolves to Global. A profile with override enabled resolves every optical parameter from that profile's complete value set. This deliberately avoids partially inherited profiles changing meaning when Global is edited.

`GlassStyleConfig` is the only object published from configuration code to the glass core.

## Prismal mapping

Create `GlassPrismalAdapter` in `glass/core`.

It maps one resolved parameter set plus display density into `PrismalParams`, using the same active unit semantics as LiquidDock's current `Miuix307PrismalMaterial.fromConfig()` path. The adapter must not contain PassBlur/OES geometry logic.

The initial Global defaults preserve the currently validated LiquidUI Phase 0 CARD rendering, not arbitrary Prismal library defaults. Therefore installing this feature without changing any slider must be visually equivalent to Phase 0.

`MaterialProfileRegistry` becomes updateable. It owns an `EnumMap<GlassMaterialProfile, PrismalParams>` plus a monotonically increasing `profileVersion` supplied by the core. Updating styles replaces only numeric profile state; it does not own or recreate EGL, SurfaceTexture, OES textures, PassBlur bindings, or producer recovery.

## Hot-update path

`ModuleMain` creates one `GlassConfigRuntime` after the exact SystemUI target resolves and after the process `SystemUiGlassCore` exists.

`GlassConfigRuntime`:

1. reads the current `GlassStyleConfig` from API101 Remote Preferences;
2. registers one `SharedPreferences.OnSharedPreferenceChangeListener`;
3. filters to glass-style keys;
4. coalesces bursts of slider changes so only the latest pending snapshot is published;
5. calls `SystemUiGlassCore.updateGlassStyles(snapshot)`.

`SystemUiGlassCore` owns the current snapshot and a monotonically increasing style version. It propagates updates to every live `WindowGlassSession`; newly created sessions immediately receive the latest snapshot.

`WindowGlassSession` posts the update to the already shared render Handler. `WindowGlassRenderer` updates `MaterialProfileRegistry`, applies global sampling extras, and requests a scene-only redraw. If a prepared backdrop is valid, the update reuses it. Optical updates must not call PassBlur bind/unbind, create a SurfaceTexture, reset producer recovery, or create another thread.

Repeated updates within one render-frame window are last-write-wins. No stale intermediate style must be presented after a newer version is accepted.

## Activation-token semantics

`GlassActivationToken.profileVersion` continues to represent the exact style version used for the successful swap. A style change makes the old visual token stale for presentation purposes until a successful swap using the new profile version occurs.

Native fallback suppression therefore remains tied to a successfully presented node generation and style version. Changing a slider must not authorize an unrendered style.

## GUI

Replace the current single-page settings body with a small page navigator using the same Miuix Compose components already present in LiquidDock/LiquidUI.

Home contains:

- master LiquidUI switch;
- notification glass switch;
- `全局玻璃样式` entry;
- `组件玻璃样式` entry;
- diagnostics switch.

`全局玻璃样式` contains grouped cards:

1. 基础：blur, thickness, IOR, normal strength, dome;
2. 折射：lens refraction/depth, chromatic, inset, displacement, transition width, smoothing, edge falloff, Fresnel, dispersion R/B, backdrop scale X/Y, parallax;
3. 颜色：vibrancy, brightness, tint RGBA, transmittance;
4. 光照：highlight width/plain highlight, specular strength/sharpness, rim, caustics, light direction, shadow RGBA/softness;
5. 采样：four global sampling extras;
6. 调试：show normals.

`组件玻璃样式` lists CARD/TILE/SLIDER/PANEL/FLOATING. Each profile page has an `独立覆盖` switch. When disabled it shows that Global is inherited. When enabled it exposes the same five optical groups as Global, excluding Sampling.

Slider `onValueChange` immediately writes local SharedPreferences. `LiquidUiApp` mirrors each write to API101 Remote Preferences. This gives live preview during dragging; SystemUI-side coalescing prevents redundant render work.

Each page provides reset behavior:

- Global reset restores Phase 0 visual defaults and zero manual sampling extras;
- profile reset restores its stored values to Global defaults and disables override.

## Remote preference bridge

Refactor `LiquidUiApp` from three hard-coded booleans to schema-driven synchronization. It must reconcile and mirror every `ConfigSchema.all()` key according to `ConfigKey.Kind` (`BOOLEAN`, `INT`, `FLOAT` if needed by implementation).

The implementation should prefer integer-backed GUI values with explicit scale metadata where that makes stable ranges easier. Runtime state always exposes normalized floats.

## Failure behavior

- Invalid/corrupt numeric values are clamped at config-read time.
- Missing profile values resolve to schema defaults, never zero by accident.
- A preference callback error keeps the last valid snapshot.
- A renderer style-update error fails closed for that Window and does not create a second producer.
- Core close unregisters/stops runtime preference observation through the composition root.
- Existing source-loss/root-loss/vendor-authority fail-closed behavior remains unchanged.

## Architectural invariants

The final implementation must prove:

- exactly one `new SystemUiGlassCore(` in production;
- exactly one production `new HandlerThread(` in `glass/core`;
- notification package still contains no EGL/OES/PassBlur producer ownership;
- style updates contain no `new SurfaceTexture(`, PassBlur bind, or EGL initialization;
- no `PixelCopy`, `ImageReader`, `MediaProjection`, or `glReadPixels` path is introduced;
- Global defaults map byte-for-behavior to current Phase 0 optics;
- all five profiles can resolve distinct `PrismalParams` while sharing one Window renderer;
- style versions increase monotonically and stale versions cannot overwrite newer ones;
- slider changes are mirrored to remote prefs and can produce a scene-only redraw without producer recovery.

## Verification

Required automated gates:

- SDK-independent config/model contracts;
- Prismal mapping parity tests against LiquidDock active semantics and current LiquidUI Phase 0 defaults;
- profile inheritance/override tests;
- config bridge tests for boolean and numeric values;
- source architecture tests for zero-copy/single-owner invariants;
- full `testDebugUnitTest assembleDebug`.

Device validation should verify live slider preview on an expanded notification, rapid slider dragging without flicker/rebind, reset to Phase 0 appearance, orientation changes, notification add/remove/recycle, source loss/recovery, and SystemUI restart persistence.
