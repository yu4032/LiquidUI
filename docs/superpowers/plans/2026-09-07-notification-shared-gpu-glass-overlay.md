# Notification Shared GPU Glass Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activate one shared zero-copy SurfaceFlinger PassBlur -> OES -> Prismal notification glass renderer per NSSL, with crash-safe root rollover recovery and fail-closed native 2dp fallback.

**Architecture:** Reuse the dormant `NotificationGlassHostView`, scene state, geometry collector, compositor, session, and shared `NotificationPassBlurTextureView`. Replace its old producer-recovery assumptions with the runtime-validated rule that real ViewRoot surface destruction retires the input producer and the next root receives a fresh `SurfaceTexture`/`Surface`. Presentation authority is granted only after a fresh current-generation OES frame is rendered and `eglSwapBuffers()` succeeds.

**Tech Stack:** Java 17, Android API 36/101 hidden framework APIs, SurfaceControl.Transaction, SurfaceTexture/BufferQueue, EGL14/GLES20, GL_TEXTURE_EXTERNAL_OES, PrismalRenderer, JUnit4 architecture/contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-07-notification-shared-gpu-glass-overlay-design.md`

## Global Constraints

- Target only `systemui-001` / HyperOS SystemUI `16.03.251211.r`.
- PassBlur source scale is exactly `0.25f`.
- No Bitmap capture/readback, PixelCopy, ImageReader, MediaProjection, ScreenCapture, `SurfaceControl.capture*`, or `glReadPixels`.
- One shared renderer / input producer generation / EGL context / output TextureView per NSSL, never per row.
- `ViewRootImpl.SurfaceChangedCallback` is lifecycle authority only; never call `SetPassBlurSurface` through its callback transaction.
- `surfaceDestroyed` retires the old producer; it may never be rebound.
- Native 2dp card PassBlur remains visible until a current-generation optical frame is swapped successfully.
- Bloom/highlight rendering remains disabled for the first validation build.

---

### Task 1: Pure presentation-authority state

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassPresentationState.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/notification/NotificationGlassPresentationStateContractTest.java`

**Interfaces:**
- Produces: `NotificationGlassPresentationState` with `sourceBound(long)`, `freshFrame(long)`, `scene(long,int)`, `swapSucceeded(long,long,long)`, `sourceLost(long)`, `terminalFailure()`, `isGlassActive()`, and `ActivationToken`.
- `ActivationToken` contains `sourceGeneration`, `sceneGeneration`, and `swapSequence` and is accepted only when it still matches current state.

- [ ] **Step 1: Write the failing state tests**

```java
@Test public void requiresFreshFrameSceneAndSwapForActivation() {
    NotificationGlassPresentationState s = new NotificationGlassPresentationState();
    s.sourceBound(7);
    s.scene(11, 2);
    assertFalse(s.isGlassActive());
    s.freshFrame(7);
    assertFalse(s.isGlassActive());
    NotificationGlassPresentationState.ActivationToken token = s.swapSucceeded(7, 11, 1);
    assertNotNull(token);
    assertTrue(s.accept(token));
    assertTrue(s.isGlassActive());
}

@Test public void staleTokenRejectedAfterSourceLoss() {
    NotificationGlassPresentationState s = new NotificationGlassPresentationState();
    s.sourceBound(3); s.scene(4, 1); s.freshFrame(3);
    var token = s.swapSucceeded(3, 4, 1);
    s.sourceLost(3);
    assertFalse(s.accept(token));
    assertFalse(s.isGlassActive());
}
```

- [ ] **Step 2: Run unit tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NotificationGlassPresentationStateContractTest'`
Expected: FAIL because `NotificationGlassPresentationState` does not exist.

- [ ] **Step 3: Implement minimal pure state**

```java
final class NotificationGlassPresentationState {
    enum Phase { FALLBACK_NATIVE, SOURCE_BOUND, SOURCE_FRESH, GLASS_ACTIVE, SOURCE_LOST, TERMINAL_FAILURE }
    record ActivationToken(long sourceGeneration, long sceneGeneration, long swapSequence) {}

    private Phase phase = Phase.FALLBACK_NATIVE;
    private long sourceGeneration = -1;
    private long sceneGeneration = -1;
    private int drawableNodes;
    private long swapSequence;
    private boolean fresh;

    synchronized void sourceBound(long generation) { sourceGeneration=generation; fresh=false; phase=Phase.SOURCE_BOUND; }
    synchronized void freshFrame(long generation) { if (generation==sourceGeneration) { fresh=true; phase=Phase.SOURCE_FRESH; } }
    synchronized void scene(long generation, int nodes) { sceneGeneration=generation; drawableNodes=Math.max(0,nodes); }
    synchronized ActivationToken swapSucceeded(long source, long scene, long swap) {
        if (source!=sourceGeneration || scene!=sceneGeneration || !fresh || drawableNodes<=0) return null;
        swapSequence=swap; phase=Phase.GLASS_ACTIVE; return new ActivationToken(source,scene,swap);
    }
    synchronized boolean accept(ActivationToken t) { return t!=null && phase==Phase.GLASS_ACTIVE && t.sourceGeneration()==sourceGeneration && t.sceneGeneration()==sceneGeneration && t.swapSequence()==swapSequence; }
    synchronized void sourceLost(long generation) { if (generation==sourceGeneration) { fresh=false; phase=Phase.SOURCE_LOST; } }
    synchronized void terminalFailure() { fresh=false; phase=Phase.TERMINAL_FAILURE; }
    synchronized boolean isGlassActive() { return phase==Phase.GLASS_ACTIVE; }
}
```

- [ ] **Step 4: Run tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*NotificationGlassPresentationStateContractTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassPresentationState.java src/test/java/com/hellovoid/liquidui/glass/notification/NotificationGlassPresentationStateContractTest.java
git commit -m "feat: add notification glass presentation state"
```

### Task 2: Port validated root-rollover producer lifecycle into the shared renderer

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java`

**Interfaces:**
- Consumes: Task 1 `NotificationGlassPresentationState`.
- Produces: shared renderer callbacks `onGlassActivated(ActivationToken)` and `onGlassRevoked(String)`; root-destroy producer retirement and root-created fresh producer recreation.

- [ ] **Step 1: Add failing architecture assertions**

Require the shared renderer source to contain `addSurfaceChangedCallback`, `surfaceDestroyed`, `producerPreserved=false`, and `NotificationGlassPresentationState`, and forbid producer reuse across surface destruction and any callback-transaction `SetPassBlurSurface` path.

- [ ] **Step 2: Run contracts and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NotificationSharedGlassArchitectureTest'`
Expected: FAIL because the dormant shared renderer still uses `ZeroCopyProducerRecoveryState` without the validated ViewRoot rollover contract.

- [ ] **Step 3: Implement root rollover lifecycle**

Add a `ViewRootImpl.SurfaceChangedCallback` proxy owned by the shared renderer. On `surfaceDestroyed`, invalidate the binding, revoke presentation, clear fresh-frame state, detach listener, release old `Surface`/`SurfaceTexture` on the EGL thread, and set input references to null. On `surfaceCreated`/`surfaceReplaced`, post out of the callback transaction, create a fresh OES `SurfaceTexture`/`Surface`, then bind it with an independent `SurfaceControl.Transaction` through `SystemUiPassBlurBridge.bind(...)`.

- [ ] **Step 4: Replace bind-success activation with fresh-frame + swap activation**

After `updateTexImage()` call `presentationState.freshFrame(inputProducerGeneration)`. Publish scene generation/node count before draw. After successful `eglSwapBuffers()`, increment `swapSequence`, call `swapSucceeded(...)`, and only then post activation to the UI thread. On source loss post revocation immediately.

- [ ] **Step 5: Run focused tests and full unit suite**

Run: `./gradlew testDebugUnitTest --tests '*NotificationSharedGlassArchitectureTest'`
Expected: PASS.
Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java
git commit -m "fix: make shared glass source rollover-safe"
```

### Task 3: Reactivate one shared NSSL runtime and fail-closed material handoff

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/LegacyNotificationVendorMaterialController.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/NotificationNativePassBlurSourceExperimentTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java`

**Interfaces:**
- Consumes: Task 2 shared renderer activation/revocation.
- Produces: exactly one `NotificationGlassSession` per NSSL; rows are geometry only; current 2dp material is fallback until activation.

- [ ] **Step 1: Add failing hook/runtime contracts**

Require the active hook to feed observed rows into `NotificationGlassRuntime`; require the runtime to own sessions by NSSL; forbid creating `NotificationGpuPassBlurStreamProbe` once shared runtime is active.

- [ ] **Step 2: Run contracts and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NotificationSharedGlassArchitectureTest' --tests '*NotificationNativePassBlurSourceExperimentTest'`
Expected: FAIL because the hook still owns the standalone probe and dormant runtime is disconnected.

- [ ] **Step 3: Wire shared runtime into `updateBackground$1` authority**

Resolve the existing row/background geometry fields already required by `NotificationGlassNodeCollector`, construct one `NotificationGlassRuntime`, and call `runtime.onRowAttached(registeredRow)` after target observation. Remove active `NotificationGpuPassBlurStreamProbe.observe(target)` ownership.

- [ ] **Step 4: Make material handoff fail closed**

Keep `NotificationVendorMaterialController` 2dp PassBlur active until `onGlassActivated`. On activation, suppress only background material that covers the shared scene; on `onGlassRevoked`, surface loss, renderer failure, or detach, restore 2dp fallback immediately.

- [ ] **Step 5: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/notification src/test/java/com/hellovoid/liquidui/architecture
git commit -m "feat: activate shared notification glass runtime"
```

### Task 4: Exaggerated Prismal refraction validation profile and final contracts

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassMaterial.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassCompositor.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java`
- Modify: `scripts/test-contracts.sh` only if needed to include new source contracts.

**Interfaces:**
- Consumes: active shared renderer from Task 3.
- Produces: first visually falsifiable build with strong spatial displacement/RGB dispersion and no highlight/bloom ambiguity.

- [ ] **Step 1: Add failing optical contracts**

Assert that the first validation profile uses `PrismalRenderer`, a no-highlight `PrismalHighlightProfile(false,false,false,false,false,false,false,false,false)`, nonzero lens refraction/depth/chromatic aberration, and bloom remains disabled.

- [ ] **Step 2: Run tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*NotificationSharedGlassArchitectureTest'`
Expected: FAIL because compositor currently uses `PrismalHighlightProfile.ALL_ENABLED`.

- [ ] **Step 3: Implement validation optics**

Use Prismal parameters seeded from the approved spec, with deliberately strong first-build displacement/dispersion. Replace `ALL_ENABLED` with a static no-highlight profile for notification shared glass only.

- [ ] **Step 4: Full verification**

Run: `bash scripts/test-contracts.sh`
Expected: PASS.
Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and debug APK produced.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/notification src/test/java/com/hellovoid/liquidui/architecture scripts/test-contracts.sh
git commit -m "feat: render shared notification refraction"
```

### Task 5: CI/runtime artifact gate

**Files:** No production source changes unless CI exposes a verified defect.

- [ ] **Step 1: Verify GitHub Actions**

Require contracts, `testDebugUnitTest`, `assembleDebug`, and artifact upload all green.

- [ ] **Step 2: Download artifact and checksum it**

Download the CI artifact, extract the debug APK, calculate SHA-256, and provide it for device testing.

- [ ] **Step 3: Runtime validation logging**

Use:

```bash
adb logcat -c
adb logcat -v time | grep -E 'NotifGlass.*(Session|PBTX|GpuStream|PBGL)|PassBlur|RenderEngine'
```

Acceptance: no SystemUI crash; root rollover creates a fresh producer; source frame count continues; activation occurs only after fresh frame + swap; visible notification edges spatially displace background with RGB dispersion; fallback returns immediately on source loss.
