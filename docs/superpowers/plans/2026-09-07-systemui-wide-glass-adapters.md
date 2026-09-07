# SystemUI-Wide Glass Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend LiquidUI's verified shared glass core from notifications to every reverse-engineered bounded SystemUI material host on `systemui-001`, without adding parallel render/capture pipelines.

**Architecture:** Domain hooks discover exact vendor material hosts and register them with one generic bounded-host scene controller. An Android-free host lifecycle state machine owns generation/authorization semantics; the Android controller owns View/session/geometry wiring. Domain adapters own only target-specific discovery and native-background mutation. All nodes flow through the process-global `SystemUiGlassCore` and existing CARD/TILE/SLIDER/PANEL/FLOATING style registry.

**Tech Stack:** Java 17, Kotlin/Compose + MIUIX settings, libxposed API 101, Android SDK 36, existing Prismal/PassBlur shared renderer, SDK-independent contract tests, Gradle 9.6.1 / AGP 9.3.0.

**Spec:** `docs/superpowers/specs/2026-09-07-systemui-wide-glass-adapters-design.md`

## Global Constraints

- Target only `com.android.systemui` profile `systemui-001` (`versionCode=202501210`, `versionName=16.03.251211.r`, SDK 36).
- Keep exactly one process-global `SystemUiGlassCore`, one shared render thread, and one renderer/PassBlur producer path per Window.
- Feature/domain adapters must never own EGL, OES, `SurfaceTexture`, `SetPassBlurSurface`, capture, `HandlerThread`, `PixelCopy`, `ScreenCapture`, or `glReadPixels`.
- Only reverse-engineered bounded material hosts are glass nodes; no fuzzy recursive View scanning.
- Full-screen notification/keyguard scrims, wallpaper dimming and root canvases are excluded.
- Native material is hidden only after exact presentation authorization and restored synchronously on revocation/failure/detach/rebind.
- Existing rotation-safe OES mapping and SurfaceTexture crop neutralization remain core-owned; adapters add no sampling offsets/matrices.
- Existing Global + CARD/TILE/SLIDER/PANEL/FLOATING optical settings remain the only optical schema.
- Every production target hook must have a written `docs/reverse-engineering/systemui-001/<domain>-glass-contract.md` derived from the verified APK/decompiled tree.
- Every task follows RED → GREEN and ends with `./scripts/test-contracts.sh` plus `gradle testDebugUnitTest assembleDebug --stacktrace` before it is considered complete.

---

## File Structure

Shared adapter layer:

- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassDomain.java` — stable domain identity.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialHostKind.java` — semantic host kinds only.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialClassifier.java` — exact host-kind → existing profile mapping.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/NativeMaterialController.java` — Android-free generic suppress/restore boundary: `NativeMaterialController<T>`.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/GlassHostGeometry.java` — Android-free radii/opacity/z metadata.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostState.java` — Android-free lifecycle/authorization state machine.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java` — Android View/session/publication bridge.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java` — attaches/reuses the one renderer host for a Window, defaulting to root index 0 when no host exists.

Domain files:

- `glass/controlcenter/ControlCenterGlassHook.java`, `ControlCenterGlassAdapter.java`, `ControlCenterNativeMaterialController.java`
- `glass/volume/VolumeGlassHook.java`, `VolumeGlassAdapter.java`, `VolumeNativeMaterialController.java`
- `glass/media/MediaGlassHook.java`, `MediaGlassAdapter.java`, `MediaNativeMaterialController.java`
- `glass/statusbar/StatusBarGlassHook.java`, `StatusBarGlassAdapter.java`, `StatusBarNativeMaterialController.java`
- `glass/keyguard/KeyguardGlassHook.java`, `KeyguardGlassAdapter.java`, `KeyguardNativeMaterialController.java`
- `glass/misc/MiscSystemUiGlassHook.java`, `MiscSystemUiGlassAdapter.java`, `MiscSystemUiNativeMaterialController.java`

Existing composition/config files modified as needed: `ModuleMain.java`, `ConfigSchema.java`, `LiquidUiConfig.java`, `LiquidUiApp.java`, `SettingsActivity.kt`, `SystemUi001Profile.java`, `scripts/test-contracts.sh`.

---

### Task 1: Shared semantic contracts

**Files:**
- Create: `glass/systemui/SystemUiGlassDomain.java`
- Create: `glass/systemui/SystemUiMaterialHostKind.java`
- Create: `glass/systemui/SystemUiMaterialClassifier.java`
- Create: `glass/systemui/NativeMaterialController.java`
- Create: `glass/systemui/GlassHostGeometry.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialClassifierTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java`
- Modify: `scripts/test-contracts.sh`

**Interfaces:**

```java
public interface NativeMaterialController<T> {
    void suppress(T host, long lifecycleGeneration);
    void restore(T host, long lifecycleGeneration);
    void restoreAll();
}

public record GlassHostGeometry(
        float topLeftRadius, float topRightRadius,
        float bottomRightRadius, float bottomLeftRadius,
        float opacity, int zOrder) {}
```

`SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind)` returns an existing `GlassMaterialProfile`.

- [ ] Write RED classifier tests for `MEDIA_CARD→CARD`, `QS_TILE→TILE`, `BRIGHTNESS_SLIDER→SLIDER`, `VOLUME_PANEL→PANEL`, `STATUS_CAPSULE→FLOATING`.
- [ ] Add architecture RED forbidding `EGL14`, `GL_TEXTURE_EXTERNAL_OES`, `SurfaceTexture`, `SetPassBlurSurface`, `HandlerThread`, `PixelCopy`, `ScreenCapture`, `glReadPixels` in domain packages.
- [ ] Run `./scripts/test-contracts.sh`; expect missing semantic types.
- [ ] Implement host kinds exactly: `NOTIFICATION_CARD`, `MEDIA_CARD`, `QS_TILE`, `DEVICE_CONTROL_TILE`, `KEYGUARD_TILE`, `BRIGHTNESS_SLIDER`, `VOLUME_SLIDER`, `CONTROL_CENTER_PANEL`, `VOLUME_PANEL`, `KEYGUARD_PANEL`, `SYSTEM_DIALOG`, `STATUS_CAPSULE`, `TRANSIENT_FLOATING_PANEL`. Use exhaustive switch only; no class-name logic.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: add SystemUI material host contracts`.

---

### Task 2: Android-free host lifecycle state

**Files:**
- Create: `glass/systemui/SystemUiGlassHostState.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostStateTest.java`
- Modify: `scripts/test-contracts.sh`

**Interfaces:**

```java
public final class SystemUiGlassHostState {
    public long register(String nodeId);
    public boolean isCurrent(String nodeId, long generation);
    public boolean authorize(String nodeId, long generation);
    public boolean revoke(String nodeId, long generation);
    public boolean unregister(String nodeId, long generation);
    public boolean failTerminal();
    public boolean failed();
    public long generation();
    public boolean authorized();
}
```

- [ ] RED tests prove generation strictly increases on re-register, stale `(nodeId,generation)` cannot authorize/revoke a new lifecycle, authorization is idempotent, unregister clears authorization, terminal failure prevents later authorization.
- [ ] Run RED; expect missing state class.
- [ ] Implement minimal synchronized/immutable-state logic with no Android imports.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: add glass host lifecycle state`.

---

### Task 3: Generic Android SystemUiGlassHostController

**Files:**
- Create: `glass/systemui/SystemUiGlassWindowHost.java`
- Create: `glass/systemui/SystemUiGlassHostController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassHostControllerArchitectureTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java`

**Interfaces:**

```java
public final class SystemUiGlassHostController implements AutoCloseable {
    public SystemUiGlassHostController(SystemUiGlassCore core,
            SystemUiGlassDomain domain, String adapterId);
    public long register(android.view.View host, SystemUiMaterialHostKind kind,
            GlassHostGeometry geometry,
            NativeMaterialController<android.view.View> material);
    public void update(android.view.View host, GlassHostGeometry geometry);
    public void unregister(android.view.View host, String reason);
    public void refresh();
    public void close();
}
```

`SystemUiGlassWindowHost.ensureSessionHost(core, anchor)` resolves `core.sessionFor(anchor)`, reuses `session.sceneHost()` when present, otherwise requires `anchor.getRootView()` to be a `ViewGroup` and calls `session.attachRenderer(anchor, rootGroup, 0, true)`.

- [ ] RED architecture tests require use of `SystemUiGlassHostState`, `SystemUiGlassCore.sessionFor`, `WindowGlassSession.bindAdapter`, existing `GlassNode`, and forbid direct renderer/producer construction.
- [ ] Implement `WeakHashMap<View,HostRecord>` and per-Window/domain bindings. Node IDs include domain + monotonic identity sequence. Geometry uses `getLocationInWindow`; no OES mapping or fixed offsets.
- [ ] Presentation callbacks suppress only the current state generation. Revoke/detach/unregister/close restore before state removal. Terminal Window failure calls `restoreAll` and stops publication.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: add shared bounded SystemUI glass host controller`.

---

### Task 4: Control Center / QS TILE coverage

**Files:**
- Create: `docs/reverse-engineering/systemui-001/control-center-glass-contract.md`
- Create: `glass/controlcenter/ControlCenterGlassHook.java`
- Create: `glass/controlcenter/ControlCenterGlassAdapter.java`
- Create: `glass/controlcenter/ControlCenterNativeMaterialController.java`
- Create: `architecture/ControlCenterGlassArchitectureTest.java`
- Modify: `ModuleMain.java`
- Modify `SystemUi001Profile.java` only for probes proven by the contract.

- [ ] Reverse-engineer the verified target with exact anchors `MiuiQSHostAdapter`, `onTilesChanged`, `ControlCenter`, `QSTileView`, `MiuiQSTile`, `setBackground`, `updateBackground`. Record exact class/method/host/background mutation/recycle authority and accepted/excluded hosts before code.
- [ ] RED test exact hook constants from the contract, `QS_TILE`/`DEVICE_CONTROL_TILE` use, shared controller use and no GPU/capture ownership.
- [ ] Implement target-ClassLoader-only hooks. Bind/attach registers bounded tile material host; recycle/unbind/detach unregisters. Native controller snapshots original drawable/tint/alpha/vendor blur once per lifecycle and restores exactly.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified Control Center tiles`.

---

### Task 5: Control Center SLIDER coverage

**Files:** modify the Task 4 contract/hook/adapter/controller/test.

- [ ] Reverse-engineer exact anchors `Brightness`, `BrightnessSlider`, `ControlCenterBrightness`, `ToggleSlider`, `MiuiBrightness`; document the bounded track/container material host, excluding thumb/icon/text children.
- [ ] RED exact verified hook point and `BRIGHTNESS_SLIDER`; prove same shared session/controller as tiles.
- [ ] Implement register/update/unregister through actual layout/pre-draw changes; no dedicated animation thread.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified Control Center sliders`.

---

### Task 6: Volume PANEL + SLIDER coverage

**Files:**
- Create: `docs/reverse-engineering/systemui-001/volume-glass-contract.md`
- Create: `glass/volume/VolumeGlassHook.java`, `VolumeGlassAdapter.java`, `VolumeNativeMaterialController.java`
- Create: `architecture/VolumeGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

- [ ] Start reverse engineering from observed `VolumePanelViewController` and `MIUI_VolumeColumn`; locate exact collapsed/expanded panel lifecycle, stream bind/update, background/blur state and teardown.
- [ ] RED exact contract constants, `VOLUME_PANEL→PANEL`, `VOLUME_SLIDER→SLIDER`, no per-volume renderer/producer.
- [ ] Implement one panel node per live volume Window and only stream controls with independent bounded materials. Expanded/collapsed transitions update membership/geometry without renderer recreation.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified volume surfaces`.

---

### Task 7: Media CARD coverage

**Files:**
- Create: `docs/reverse-engineering/systemui-001/media-glass-contract.md`
- Create: `glass/media/MediaGlassHook.java`, `MediaGlassAdapter.java`, `MediaNativeMaterialController.java`
- Create: `architecture/MediaGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

- [ ] Reverse-engineer `MediaControlPanel`, `MediaViewController`, `MediaHost` and MIUI media classes/background methods. Identify card material host separately from artwork/image children.
- [ ] RED exact hooks, `MEDIA_CARD→CARD`, recycle/rebind generation safety.
- [ ] Implement; media artwork remains normal content and never becomes capture source. Rebind revokes/restores old lifecycle before new suppression.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified media cards`.

---

### Task 8: Status/FLOATING coverage

**Files:**
- Create: `docs/reverse-engineering/systemui-001/statusbar-glass-contract.md`
- Create: `glass/statusbar/StatusBarGlassHook.java`, `StatusBarGlassAdapter.java`, `StatusBarNativeMaterialController.java`
- Create: `architecture/StatusBarGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

- [ ] Reverse-engineer DynamicIsland/dynamic capsule, ongoing activity chip, heads-up bounded material and transient status/floating overlays. Explicitly exclude full-width status-bar roots and transparent icon containers.
- [ ] RED one assertion per accepted host and explicit absence of excluded roots.
- [ ] Implement short-lived hosts using authoritative hide/remove callbacks, no fixed delay. Separate overlay Window resolves its own session automatically.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified floating SystemUI surfaces`.

---

### Task 9: Keyguard bounded coverage

**Files:**
- Create: `docs/reverse-engineering/systemui-001/keyguard-glass-contract.md`
- Create: `glass/keyguard/KeyguardGlassHook.java`, `KeyguardGlassAdapter.java`, `KeyguardNativeMaterialController.java`
- Create: `architecture/KeyguardGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

- [ ] Reverse-engineer bounded keyguard cards/affordance panels. Explicitly document/exclude keyguard scrims, wallpaper/dim layers, bouncer/security roots and full-screen canvases.
- [ ] RED accepted host kinds plus forbidden excluded-root registration.
- [ ] Implement only accepted bounded hosts. Lock/unlock/root replacement revokes/restores synchronously; no keyguard-specific capture source.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: glassify verified keyguard surfaces`.

---

### Task 10: Misc bounded overlays + exhaustive coverage audit

**Files:**
- Create: `docs/reverse-engineering/systemui-001/misc-glass-contract.md`
- Create: `docs/reverse-engineering/systemui-001/GLASS_COVERAGE.md`
- Create: `glass/misc/MiscSystemUiGlassHook.java`, `MiscSystemUiGlassAdapter.java`, `MiscSystemUiNativeMaterialController.java`
- Create: `architecture/SystemUiWideGlassCoverageTest.java`
- Modify: `ModuleMain.java`

- [ ] Audit remaining independent materials from decompiled resources/classes and known SystemUI overlay/layer names. Every candidate gets one coverage row: `domain | exact class/host | accepted/excluded | technical reason | profile`.
- [ ] RED requires every accepted row to have a production adapter constant and every excluded full-screen/root row to be absent from registration code.
- [ ] Implement only accepted leftovers: bounded dialogs/panels→PANEL, small floating overlays→FLOATING. No wildcard scanner/base-View hook.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: complete bounded SystemUI glass coverage`.

---

### Task 11: Domain config + complete GUI coverage controls

**Files:** modify `ConfigSchema.java`, `LiquidUiConfig.java`, `LiquidUiApp.java`, `SettingsActivity.kt`, `ModuleMain.java`, `ConfigSchemaContractTest.java`, `SettingsShellArchitectureTest.java`.

Add exact default-true keys:

```text
systemui_glass_control_center
systemui_glass_volume
systemui_glass_media
systemui_glass_statusbar
systemui_glass_keyguard
systemui_glass_misc
```

Keep `notification_glass_enabled` for migration compatibility.

- [ ] RED requires all keys in schema/typed config/remote mirroring and GUI switches for Notification, Control Center, Volume, Media, Status/Floating, Keyguard, Misc.
- [ ] Implement. Coverage controls only enable/disable domain hooks; do not duplicate optical sliders. Display which existing profile families each domain consumes.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `feat: add SystemUI glass coverage controls`.

---

### Task 12: Diagnostics + final ownership invariants

**Files:**
- Create: `glass/systemui/SystemUiGlassDiagnostics.java`
- Modify: `SystemUiGlassHostController.java` and domain adapters for structured diagnostics only.
- Create: `architecture/SystemUiWideGlassOwnershipTest.java`
- Modify: `scripts/test-contracts.sh`

Counters/log dimensions: `liveWindows`, `liveSessions`, `liveNodesByProfile`, `liveNodesByDomain`, `suppressedNativeMaterials`, `rendererCount`, `producerCount`; plus domain, host kind, node ID, lifecycle generation and revoke/failure reason.

- [ ] RED ownership tests assert exactly one process construction of `SystemUiGlassCore`, one core `HandlerThread`, no domain renderer/producer/capture primitives, all domains route through shared host controller/session.
- [ ] Implement rate-limited observational counters; they never become rendering authority.
- [ ] Run both required test commands; expect GREEN.
- [ ] Commit `test: lock SystemUI-wide glass ownership invariants`.

---

### Task 13: Final clean build, artifact and device checklist

**Files:** update `GLASS_COVERAGE.md`; create `docs/superpowers/plans/2026-09-07-systemui-wide-glass-device-checklist.md`; update README only if stale.

- [ ] Audit/remove only obsolete migration/probe scaffolding; keep specs/contracts/regression tests.
- [ ] Fresh run `./scripts/test-contracts.sh` and `gradle testDebugUnitTest assembleDebug --stacktrace`; require all GREEN.
- [ ] Require normal API101 workflow to upload directly installable APK; verify ZIP integrity, APK Signing Block and SHA-256.
- [ ] Device checklist covers portrait, landscape 90°, reverse portrait, landscape 270° for notifications, collapsed/expanded Control Center, tile reuse, brightness, media, collapsed/expanded volume/multiple streams, floating/status, accepted keyguard, lock/unlock, shade open/close, rotation while visible, root/producer rollover and live GUI style updates. Record alignment, radius parity, stale frame, duplicate layer, native overlap, fallback restoration, crash, renderer/producer counts.
- [ ] Commit `docs: finalize SystemUI-wide glass validation`.
