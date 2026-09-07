# SystemUI-Wide Glass Adapters Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend LiquidUI's verified shared glass core from notifications to every reverse-engineered bounded SystemUI material host on `systemui-001`, without adding parallel render/capture pipelines.

**Architecture:** Domain hooks discover exact vendor material hosts and register them with one generic bounded-host scene controller. The controller owns lifecycle generation, Window/session binding, geometry publication, presentation authorization and fail-closed native-material restoration; domain adapters own only target-specific discovery and native-background mutation. All nodes still flow through the process-global `SystemUiGlassCore` and the existing CARD/TILE/SLIDER/PANEL/FLOATING style registry.

**Tech Stack:** Java 17, Kotlin/Compose + MIUIX settings, libxposed API 101, Android SDK 36, existing Prismal/PassBlur shared renderer, SDK-independent contract tests, Gradle 9.6.1 / AGP 9.3.0.

**Spec:** `docs/superpowers/specs/2026-09-07-systemui-wide-glass-adapters-design.md`

## Global Constraints

- Target only `com.android.systemui` profile `systemui-001` (`versionCode=202501210`, `versionName=16.03.251211.r`, SDK 36).
- Keep exactly one process-global `SystemUiGlassCore`, one shared render thread, and one renderer/PassBlur producer path per Window.
- Feature/domain adapters must never own EGL, OES, `SurfaceTexture`, `SetPassBlurSurface`, capture, `HandlerThread`, `PixelCopy`, `ScreenCapture`, or `glReadPixels`.
- Only reverse-engineered bounded material hosts are glass nodes; no fuzzy recursive View scanning.
- Full-screen notification/keyguard scrims, wallpaper dimming and root canvases are excluded.
- Native material is hidden only after exact presentation authorization and is restored synchronously on revocation/failure/detach/rebind.
- Existing rotation-safe OES mapping and SurfaceTexture crop neutralization remain core-owned; adapters add no sampling offsets/matrices.
- Existing Global + CARD/TILE/SLIDER/PANEL/FLOATING optical settings remain the only optical schema.
- Every production target hook must have a written `docs/reverse-engineering/systemui-001/<domain>-glass-contract.md` derived from the verified APK/decompiled tree.
- Every task follows RED → GREEN and ends with `./scripts/test-contracts.sh` plus `gradle testDebugUnitTest assembleDebug --stacktrace` before the task is considered complete.

---

## File Structure

New generic shared-adapter files:

- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassDomain.java` — stable domain identity.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialHostKind.java` — semantic host kinds only.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialClassifier.java` — exact host-kind → existing profile mapping.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/NativeMaterialController.java` — per-host suppress/restore boundary.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/GlassHostGeometry.java` — current radii/opacity/z metadata.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java` — common lifecycle/session/publication/authorization state machine.
- `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java` — attaches/reuses the one renderer host for a Window, defaulting to root index 0 when no existing host exists.

Domain files:

- `glass/controlcenter/ControlCenterGlassHook.java`, `ControlCenterGlassAdapter.java`, `ControlCenterNativeMaterialController.java`
- `glass/volume/VolumeGlassHook.java`, `VolumeGlassAdapter.java`, `VolumeNativeMaterialController.java`
- `glass/media/MediaGlassHook.java`, `MediaGlassAdapter.java`, `MediaNativeMaterialController.java`
- `glass/statusbar/StatusBarGlassHook.java`, `StatusBarGlassAdapter.java`, `StatusBarNativeMaterialController.java`
- `glass/keyguard/KeyguardGlassHook.java`, `KeyguardGlassAdapter.java`, `KeyguardNativeMaterialController.java`
- `glass/misc/MiscSystemUiGlassHook.java`, `MiscSystemUiGlassAdapter.java`, `MiscSystemUiNativeMaterialController.java`

Existing files modified as needed:

- `ModuleMain.java` — compose domain hooks only.
- `ConfigSchema.java`, `LiquidUiConfig.java`, `LiquidUiApp.java`, `SettingsActivity.kt` — per-domain enable toggles and diagnostics-facing UI.
- `SystemUi001Profile.java` — only structural probes proven by reverse-engineering contracts.
- `scripts/test-contracts.sh` — compile new pure Java tests/classes; never compile Android-backed hook implementations into the pure layer.

---

### Task 1: Shared bounded-host adapter contract

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassDomain.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialHostKind.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialClassifier.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/NativeMaterialController.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/GlassHostGeometry.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/systemui/SystemUiMaterialClassifierTest.java`
- Test/modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java`
- Modify: `scripts/test-contracts.sh`

**Interfaces:**
- Produces `GlassMaterialProfile SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind kind)`.
- Produces `NativeMaterialController` with `suppress(View host,long generation)`, `restore(View host,long generation)`, `restoreAll()`.
- Produces `GlassHostGeometry` with four radii, opacity and z-order.

- [ ] **Step 1: Write classifier RED tests**

```java
assertEquals(GlassMaterialProfile.CARD,
        SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.MEDIA_CARD));
assertEquals(GlassMaterialProfile.TILE,
        SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.QS_TILE));
assertEquals(GlassMaterialProfile.SLIDER,
        SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.BRIGHTNESS_SLIDER));
assertEquals(GlassMaterialProfile.PANEL,
        SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.VOLUME_PANEL));
assertEquals(GlassMaterialProfile.FLOATING,
        SystemUiMaterialClassifier.profileFor(SystemUiMaterialHostKind.STATUS_CAPSULE));
```

Also add an architecture assertion that source under `glass/controlcenter`, `glass/volume`, `glass/media`, `glass/statusbar`, `glass/keyguard`, `glass/misc` contains none of `EGL14`, `GL_TEXTURE_EXTERNAL_OES`, `SurfaceTexture`, `SetPassBlurSurface`, `HandlerThread`, `PixelCopy`, `ScreenCapture`, `glReadPixels`.

- [ ] **Step 2: Run RED**

Run: `./scripts/test-contracts.sh`
Expected: FAIL because the new semantic types do not exist.

- [ ] **Step 3: Implement semantic types**

Use these host kinds exactly:

```java
NOTIFICATION_CARD, MEDIA_CARD,
QS_TILE, DEVICE_CONTROL_TILE, KEYGUARD_TILE,
BRIGHTNESS_SLIDER, VOLUME_SLIDER,
CONTROL_CENTER_PANEL, VOLUME_PANEL, KEYGUARD_PANEL, SYSTEM_DIALOG,
STATUS_CAPSULE, TRANSIENT_FLOATING_PANEL
```

Classifier uses an exhaustive `switch` and returns only the existing five `GlassMaterialProfile` values. No reflection or class-name logic belongs here.

- [ ] **Step 4: Run GREEN**

Run both required test commands. Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add SystemUI material host contracts`

---

### Task 2: Generic SystemUiGlassHostController

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassWindowHost.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostController.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassHostControllerContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java`
- Modify: `scripts/test-contracts.sh`

**Interfaces:**

```java
public final class SystemUiGlassHostController implements
        WindowGlassSession.AdapterPresentationListener, AutoCloseable {
    public SystemUiGlassHostController(SystemUiGlassCore core,
            SystemUiGlassDomain domain, String adapterId);
    public long register(View host, SystemUiMaterialHostKind kind,
            GlassHostGeometry geometry, NativeMaterialController material);
    public void update(View host, GlassHostGeometry geometry);
    public void unregister(View host, String reason);
    public void refresh();
    public void close();
}
```

`SystemUiGlassWindowHost.ensureSessionHost(SystemUiGlassCore core, View anchor)` must:
1. resolve `core.sessionFor(anchor)`;
2. reuse `session.sceneHost()` when present;
3. otherwise require `anchor.getRootView()` to be a `ViewGroup` and call `session.attachRenderer(anchor, rootGroup, 0, true)`;
4. fail closed if root is not a `ViewGroup`.

- [ ] **Step 1: Write RED lifecycle/authorization tests**

Pure state tests use a fake material controller and fake binding seam to prove:

```text
register -> native remains visible
publish without authorization -> native remains visible
authorize current node -> suppress exactly once
revoke -> restore exactly once
unregister -> restore and remove
register same recycled View again -> lifecycleGeneration strictly increases
stale authorization from old node/generation -> cannot suppress current host
terminal failure -> restoreAll + no further publication
```

- [ ] **Step 2: Run RED**

Expected: missing controller/window-host symbols.

- [ ] **Step 3: Implement controller**

Internally keep `WeakHashMap<View, HostState>`. Node IDs must include domain + identity sequence, not just `View.hashCode()`. Each live Window gets one domain-local `AdapterBinding`, but all domain bindings on that Window share the same `WindowGlassSession` and renderer.

Geometry publication constructs existing `GlassNode` records using classifier output. Use root/window coordinates from `getLocationInWindow`; never apply OES transforms here.

Presentation callback suppresses only matching current node IDs; revoked IDs restore synchronously. Detach/unregister/close always restore before dropping state.

- [ ] **Step 4: Run GREEN and verify ownership invariant**

Architecture test must prove no second `new SystemUiGlassCore(` and no domain source creates `WindowGlassRenderer`.

- [ ] **Step 5: Commit**

Commit message: `feat: add shared bounded SystemUI glass host controller`

---

### Task 3: Control Center / QS reverse-engineering contract and TILE adapter

**Files:**
- Create: `docs/reverse-engineering/systemui-001/control-center-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/controlcenter/ControlCenterNativeMaterialController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/ControlCenterGlassArchitectureTest.java`
- Modify: `src/main/java/com/hellovoid/liquidui/ModuleMain.java`
- Modify only if proven: `src/main/java/com/hellovoid/liquidui/target/profiles/SystemUi001Profile.java`

**Interfaces:**
- Consumes Task 2 controller.
- Produces one hook registered in `SystemUiHookRegistry`, submitting `QS_TILE` and `DEVICE_CONTROL_TILE` hosts.

- [ ] **Step 1: Reverse-engineer exact target contract**

Using the verified `MiuiSystemUI.apk` + JADX 1.5.6, search these concrete anchors first:

```text
MiuiQSHostAdapter
onTilesChanged
ControlCenter
QSTileView
MiuiQSTile
setBackground
updateBackground
```

For every accepted tile host record exact class, lifecycle/bind method, field/return holding the material View, background mutation path, recycle/unbind authority and geometry owner. Reject roots/scrims/transparent icon-only children. Write all accepted/excluded candidates to `control-center-glass-contract.md` before production hook code.

- [ ] **Step 2: Write RED architecture test from the contract**

Test exact class/method string constants, `SystemUiMaterialHostKind.QS_TILE` use, shared controller use, and absence of GPU/capture ownership.

- [ ] **Step 3: Implement exact-version hook**

Resolve vendor classes through the supplied target ClassLoader only. On authoritative bind/attach, pass the bounded background host to `ControlCenterGlassAdapter.registerTile(...)`; on recycle/unbind/detach, unregister it. `ControlCenterNativeMaterialController` captures original drawable/tint/alpha/vendor blur state once per lifecycle and restores it exactly.

- [ ] **Step 4: Run required tests**

Expected: GREEN.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified Control Center tiles`

---

### Task 4: Control Center sliders

**Files:**
- Modify: `docs/reverse-engineering/systemui-001/control-center-glass-contract.md`
- Modify: `ControlCenterGlassHook.java`
- Modify: `ControlCenterGlassAdapter.java`
- Modify: `ControlCenterNativeMaterialController.java`
- Modify: `ControlCenterGlassArchitectureTest.java`

**Interfaces:**
- Produces `BRIGHTNESS_SLIDER` hosts using `SLIDER` profile.

- [ ] **Step 1: Extend reverse-engineering contract**

Search exact anchors:

```text
Brightness
BrightnessSlider
ControlCenterBrightness
ToggleSlider
MiuiBrightness
```

Document only the bounded track/container that owns the native material, not thumb/icon/text children.

- [ ] **Step 2: Write RED**

Assert exact verified hook point and `BRIGHTNESS_SLIDER` mapping; assert slider host uses same shared controller/session as tile hosts.

- [ ] **Step 3: Implement slider register/update/unregister**

Geometry refresh occurs during actual layout/pre-draw changes and does not create a dedicated animation/render thread. Native background suppression remains authorization-gated.

- [ ] **Step 4: Run GREEN**

Run both required test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified Control Center sliders`

---

### Task 5: Volume panel and per-stream controls

**Files:**
- Create: `docs/reverse-engineering/systemui-001/volume-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/volume/VolumeGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/volume/VolumeGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/volume/VolumeNativeMaterialController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/VolumeGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

**Interfaces:**
- `VOLUME_PANEL -> PANEL`
- `VOLUME_SLIDER -> SLIDER`

- [ ] **Step 1: Reverse-engineer from known runtime anchors**

Start with verified log names `VolumePanelViewController` and `MIUI_VolumeColumn`, then locate their exact classes and methods controlling collapsed/expanded panel creation, stream-column bind/update, background/blur state and teardown.

- [ ] **Step 2: Write RED contract tests**

Require exact hook constants from the written contract and prove panel/slider kinds; forbid per-volume renderer/PassBlur source.

- [ ] **Step 3: Implement panel + stream host lifecycle**

Register panel once per live volume Window; register only stream controls that own a separate bounded material. Expanded/collapsed transitions update geometry/membership through the shared controller; no renderer recreation.

- [ ] **Step 4: Run GREEN**

Run required test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified volume surfaces`

---

### Task 6: Media cards across shade/Control Center/keyguard

**Files:**
- Create: `docs/reverse-engineering/systemui-001/media-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/media/MediaGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/media/MediaGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/media/MediaNativeMaterialController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/MediaGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

**Interfaces:**
- Accepted bounded media surfaces map to `MEDIA_CARD -> CARD`.

- [ ] **Step 1: Reverse-engineer exact media material hosts**

Search `MediaControlPanel`, `MediaViewController`, `MediaHost`, Xiaomi/MIUI media controller names, and background mutation methods. Distinguish the card material host from artwork/image children.

- [ ] **Step 2: RED**

Require exact contract hooks, CARD mapping and lifecycle recycle safety.

- [ ] **Step 3: Implement**

Media artwork remains normal content above glass; do not use artwork as a capture source. Rebind to a new media session increments lifecycle generation and revokes/restores the old card before suppressing the new one.

- [ ] **Step 4: GREEN**

Run both test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified media cards`

---

### Task 7: Status bar capsules and floating SystemUI materials

**Files:**
- Create: `docs/reverse-engineering/systemui-001/statusbar-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/statusbar/StatusBarGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/statusbar/StatusBarGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/statusbar/StatusBarNativeMaterialController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/StatusBarGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

**Interfaces:**
- `STATUS_CAPSULE` and `TRANSIENT_FLOATING_PANEL` map to `FLOATING`.

- [ ] **Step 1: Reverse-engineer bounded floating hosts**

Search exact target for DynamicIsland/dynamic capsule, ongoing activity chip, heads-up bounded material, transient status chips and floating overlays. Exclude full-width status bar root and transparent icon containers.

- [ ] **Step 2: RED**

One test per accepted host kind; explicitly assert documented excluded roots do not appear in registration constants.

- [ ] **Step 3: Implement**

Short-lived hosts must unregister on authoritative hide/remove, not after a delay. Separate overlay Windows get separate sessions automatically through `sessionFor(host)`.

- [ ] **Step 4: GREEN**

Run both test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified floating SystemUI surfaces`

---

### Task 8: Keyguard bounded materials

**Files:**
- Create: `docs/reverse-engineering/systemui-001/keyguard-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/keyguard/KeyguardGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/keyguard/KeyguardGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/keyguard/KeyguardNativeMaterialController.java`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/KeyguardGlassArchitectureTest.java`
- Modify: `ModuleMain.java`

**Interfaces:**
- Bounded card surfaces use CARD, compact action surfaces TILE, bounded containers PANEL.

- [ ] **Step 1: Reverse-engineer exact bounded keyguard material hosts**

Search keyguard affordance/cards/security auxiliary panels and background mutation methods. Explicitly list and exclude full-screen keyguard scrims, wallpaper/dim layers, bouncer/security root and full-screen canvases.

- [ ] **Step 2: RED exclusions + accepted hosts**

Architecture test must fail if excluded root/scrim class constants are registered as glass hosts.

- [ ] **Step 3: Implement accepted bounded hosts only**

Lock/unlock/root replacement must revoke and restore synchronously. No keyguard-specific backdrop/capture source is introduced.

- [ ] **Step 4: GREEN**

Run both test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: glassify verified keyguard surfaces`

---

### Task 9: Misc bounded overlays + coverage audit

**Files:**
- Create: `docs/reverse-engineering/systemui-001/misc-glass-contract.md`
- Create: `src/main/java/com/hellovoid/liquidui/glass/misc/MiscSystemUiGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/misc/MiscSystemUiGlassAdapter.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/misc/MiscSystemUiNativeMaterialController.java`
- Create: `docs/reverse-engineering/systemui-001/GLASS_COVERAGE.md`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiWideGlassCoverageTest.java`
- Modify: `ModuleMain.java`

**Interfaces:**
- Only explicitly documented leftover bounded hosts are accepted.

- [ ] **Step 1: Audit remaining independently materialized SystemUI surfaces**

Use decompiled resource/class references plus existing layer names to review dialogs, bounded shell overlays and system cards. For each candidate, write one row in `GLASS_COVERAGE.md`: `domain | class/host | accepted/excluded | reason | profile`.

- [ ] **Step 2: RED coverage test**

Test that every accepted row has a production adapter constant and every excluded full-screen/root row is absent from node-registration code.

- [ ] **Step 3: Implement only accepted leftovers**

Map bounded dialogs/panels to PANEL and small floating overlays to FLOATING. No wildcard scanner or base-View hook.

- [ ] **Step 4: GREEN**

Run both test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: complete bounded SystemUI glass coverage`

---

### Task 10: Domain enable configuration and complete GUI controls

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquidui/config/LiquidUiConfig.java`
- Modify: `src/main/java/com/hellovoid/liquidui/LiquidUiApp.java`
- Modify: `src/main/kotlin/com/hellovoid/liquidui/SettingsActivity.kt`
- Modify: `src/main/java/com/hellovoid/liquidui/ModuleMain.java`
- Modify: `src/test/java/com/hellovoid/liquidui/config/ConfigSchemaContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/SettingsShellArchitectureTest.java`

**Interfaces:**

Add exact keys:

```text
systemui_glass_control_center=true
systemui_glass_volume=true
systemui_glass_media=true
systemui_glass_statusbar=true
systemui_glass_keyguard=true
systemui_glass_misc=true
```

Keep existing `notification_glass_enabled` as the notification domain key for migration compatibility.

- [ ] **Step 1: RED schema/UI tests**

Require all six new boolean keys in `ConfigSchema.all()`, typed reads in `LiquidUiConfig`, Remote Preferences mirroring in `LiquidUiApp`, and a GUI coverage section containing switches for Notification, Control Center, Volume, Media, Status/Floating, Keyguard and Misc.

- [ ] **Step 2: Run RED**

Expected: missing schema/UI entries.

- [ ] **Step 3: Implement config + GUI**

The coverage section controls only which domain hooks install. Do not duplicate any optical sliders; the existing Global + five profile pages remain authoritative for optics. Show explanatory profile labels beside each domain so the user can see which optical override it uses.

- [ ] **Step 4: GREEN**

Run both test commands.

- [ ] **Step 5: Commit**

Commit message: `feat: add SystemUI glass coverage controls`

---

### Task 11: Diagnostics and final ownership invariants

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/systemui/SystemUiGlassDiagnostics.java`
- Modify: `SystemUiGlassHostController.java`
- Modify: domain adapters for structured domain/host-kind/node diagnostics only.
- Create/modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiWideGlassOwnershipTest.java`
- Modify: `scripts/test-contracts.sh`

**Interfaces:**

Counters exposed to logs/tests:

```text
liveWindows
liveSessions
liveNodesByProfile
liveNodesByDomain
suppressedNativeMaterials
rendererCount
producerCount
```

- [ ] **Step 1: RED ownership tests**

Assert exactly one process construction of `SystemUiGlassCore`, exactly one core `HandlerThread`, no domain creates renderer/producer/capture primitives, and each domain binds through `SystemUiGlassCore.sessionFor(...)`/the shared host controller.

- [ ] **Step 2: Implement rate-limited diagnostics counters**

Counters are observational only; they cannot become rendering authority. Adapter failures include domain, host kind, node ID, lifecycle generation and reason.

- [ ] **Step 3: GREEN**

Run both required commands.

- [ ] **Step 4: Commit**

Commit message: `test: lock SystemUI-wide glass ownership invariants`

---

### Task 12: Final verification, clean APK and device checklist

**Files:**
- Modify: `docs/reverse-engineering/systemui-001/GLASS_COVERAGE.md` with final implementation status.
- Create: `docs/superpowers/plans/2026-09-07-systemui-wide-glass-device-checklist.md`
- Modify `README.md` only if current documented feature coverage is stale.

- [ ] **Step 1: Repository cleanup audit**

Search repository for temporary workflows, patch files, probes, duplicate renderer implementations and stale notification-only ownership statements. Remove only migration/probe scaffolding proven obsolete; keep design/spec/contract documentation and regression tests.

- [ ] **Step 2: Fresh final verification**

Run:

```bash
./scripts/test-contracts.sh
gradle testDebugUnitTest assembleDebug --stacktrace
```

Expected: all GREEN on the clean final HEAD.

- [ ] **Step 3: CI artifact verification**

Require the normal API101 workflow on the final HEAD to upload a directly installable APK. Verify ZIP integrity/APK Signing Block and record SHA-256.

- [ ] **Step 4: Device checklist**

The checklist must cover portrait, landscape 90°, reverse portrait and landscape 270° for notifications, collapsed/expanded Control Center, tile reuse, brightness slider, media, collapsed/expanded volume, multiple streams, floating/status surfaces, accepted keyguard surfaces, lock/unlock, shade open/close, rotation while visible, producer/root rollover and live GUI style updates.

For each scenario record: alignment, radius parity, stale frame, duplicate layer, native background overlap, fallback restoration, SystemUI crash, renderer/producer counts.

- [ ] **Step 5: Commit**

Commit message: `docs: finalize SystemUI-wide glass validation`
