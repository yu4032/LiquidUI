# SystemUI-wide Shared Glass Phase 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the notification-only GPU glass prototype into one process-global SystemUI glass core with one session per live ViewRoot, then migrate notifications without expanding visible scope.

**Architecture:** `SystemUiGlassCore` owns transform, scheduling, material, and rendering policy. `WindowGlassRegistry` returns one `WindowGlassSession` per ViewRoot/display identity. The notification adapter publishes immutable generic nodes and owns only notification geometry and native-material handoff.

**Tech Stack:** Java 17, Android SDK 37, libxposed API 101, OpenGL ES/EGL, `SurfaceTexture` external OES, HyperOS `SetPassBlurSurface`, Prismal, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-07-systemui-wide-shared-glass-design.md`

## Global Constraints

- Target only `systemui-001`: `com.android.systemui` version `16.03.251211.r`, version code `202501210`, SDK 36.
- Exactly one `SystemUiGlassCore` exists per SystemUI process and one `WindowGlassSession` per live ViewRoot/display identity.
- Components provide immutable geometry, four radii, opacity, z-order, material id, visibility, and lifecycle generation only.
- Component packages create no PassBlur producer, OES texture, EGL context, render thread, Prismal renderer, or output `TextureView`.
- SystemUI foreground content and input remain native.
- The pixel path remains GPU-only; CPU capture/readback APIs are forbidden.
- Requested source scale stays `1.0`; producer geometry and the `SurfaceTexture` matrix are authoritative.
- Sampling inset and Prismal parallax remain `0`; current notification optical values remain unchanged.
- Per-component and per-orientation magic offsets are forbidden.
- Native material remains until a successful token contains the exact node id and lifecycle generation.
- Root loss restores native material before producer retirement/rebind.
- Verification is `./scripts/test-contracts.sh` plus `gradle testDebugUnitTest assembleDebug`.

## File map

Create focused core types under `src/main/java/com/hellovoid/liquidui/glass/core/`:

- `GlassMaterialProfile`, `GlassNode`, `GlassSceneSnapshot`, `GlassSceneState`: generic scene model.
- `FrameCoordinator`: bounded latest-state scheduling.
- `DisplayTransformSnapshot`, `DisplayTransform`, `DisplayTransformEngine`: the only coordinate authority.
- `GlassActivationToken`, `WindowPresentationState`: Window/node freshness and handoff policy.
- `WindowKey`, `WindowGlassRegistry`, `SystemUiGlassCore`, `WindowGlassSession`: ownership.
- `WindowGlassRenderer`, `SystemUiPassBlurBridge`, `PassBlurShaders`, `GlassCompositor`, `MaterialProfileRegistry`, `GlassHostView`, `ViewRootSurfaceObserver`, `ProducerRecoveryState`: generic GPU pipeline.

Retain notification geometry/material code under `glass/notification/`, but replace notification session/GPU ownership with `NotificationGlassAdapter`.

---

### Task 1: Add component-neutral scene contracts

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassMaterialProfile.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassNode.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassSceneSnapshot.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassSceneState.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/GlassSceneStateContractTest.java`

**Interfaces:**
- Consumes: Java records and atomics.
- Produces: `GlassSceneState.publish(List<GlassNode>)` and the node/profile types used by every later task.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void publicationIsImmutableSortedAndMonotonic() {
    GlassSceneState state = new GlassSceneState();
    ArrayList<GlassNode> input = new ArrayList<>(List.of(node("front", 9), node("rear", 2)));
    GlassSceneSnapshot first = state.publish(input);
    input.clear();
    GlassSceneSnapshot second = state.publish(List.of(node("front", 9)));
    assertEquals(List.of("rear", "front"), first.nodes().stream().map(GlassNode::id).toList());
    assertEquals(1L, first.generation());
    assertEquals(2L, second.generation());
}

@Test public void invalidGeometryIsClampedAndNotDrawable() {
    GlassNode node = new GlassNode("n", 4L, 0f, 0f, -3f, 4f,
            -1f, -1f, -1f, -1f, 4f, 0, GlassMaterialProfile.CARD);
    assertEquals(0f, node.width(), 0f);
    assertEquals(1f, node.opacity(), 0f);
    assertFalse(node.drawable());
}
```

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*GlassSceneStateContractTest'`

Expected: compilation fails because the generic scene types do not exist.

- [ ] **Step 3: Implement the minimal immutable model**

```java
public enum GlassMaterialProfile { CARD, TILE, SLIDER, PANEL, FLOATING }

public record GlassNode(String id, long lifecycleGeneration,
        float left, float top, float width, float height,
        float topLeftRadius, float topRightRadius,
        float bottomRightRadius, float bottomLeftRadius,
        float opacity, int zOrder, GlassMaterialProfile materialProfile) {
    public GlassNode {
        Objects.requireNonNull(id); Objects.requireNonNull(materialProfile);
        width = Math.max(0f, width); height = Math.max(0f, height);
        topLeftRadius = Math.max(0f, topLeftRadius);
        topRightRadius = Math.max(0f, topRightRadius);
        bottomRightRadius = Math.max(0f, bottomRightRadius);
        bottomLeftRadius = Math.max(0f, bottomLeftRadius);
        opacity = Math.max(0f, Math.min(1f, opacity));
    }
    public boolean drawable() { return width > .5f && height > .5f && opacity > .001f; }
}
```

`GlassSceneSnapshot` copies, null-filters, and sorts nodes by `zOrder` then `id`. `GlassSceneState` uses `AtomicLong` and `AtomicReference`, matching the current notification publication pattern.

- [ ] **Step 4: Run GREEN**

Run: `gradle testDebugUnitTest --tests '*GlassSceneStateContractTest' && ./scripts/test-contracts.sh`

Expected: focused and existing contract tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core src/test/java/com/hellovoid/liquidui/glass/core
git commit -m "feat: add generic SystemUI glass scene contracts"
```

---

### Task 2: Add bounded latest-state frame coalescing

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/FrameCoordinator.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/FrameCoordinatorContractTest.java`

**Interfaces:**
- Consumes: `Poster.post(Runnable)` supplied by the shared render thread.
- Produces: `requestSourceFrame()`, `requestScene()`, `cancel()`, and `Drain.render(boolean, boolean)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test public void sourceAndSceneShareOneQueuedDrain() {
    QueuePoster poster = new QueuePoster();
    List<String> calls = new ArrayList<>();
    FrameCoordinator c = new FrameCoordinator(poster, (source, scene) -> calls.add(source + ":" + scene));
    c.requestSourceFrame(); c.requestScene();
    assertEquals(1, poster.size());
    poster.runNext();
    assertEquals(List.of("true:true"), calls);
}

@Test public void requestsDuringDrawQueueOneFollowUp() {
    QueuePoster poster = new QueuePoster();
    AtomicReference<FrameCoordinator> ref = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    FrameCoordinator c = new FrameCoordinator(poster, (source, scene) -> {
        if (calls.getAndIncrement() == 0) { ref.get().requestScene(); ref.get().requestScene(); }
    });
    ref.set(c); c.requestSourceFrame(); poster.runNext();
    assertEquals(1, poster.size()); poster.runNext(); assertEquals(2, calls.get());
}
```

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*FrameCoordinatorContractTest'`

Expected: `FrameCoordinator` is missing.

- [ ] **Step 3: Implement two pending bits and one queued bit**

```java
public final class FrameCoordinator {
    public interface Poster { void post(Runnable command); }
    public interface Drain { void render(boolean sourcePending, boolean scenePending); }
    private final Poster poster;
    private final Drain drain;
    private boolean sourcePending, scenePending, queued, cancelled;
    public FrameCoordinator(Poster poster, Drain drain) {
        this.poster = Objects.requireNonNull(poster);
        this.drain = Objects.requireNonNull(drain);
    }
    public synchronized void requestSourceFrame() { request(true, false); }
    public synchronized void requestScene() { request(false, true); }
    private void request(boolean source, boolean scene) {
        if (cancelled) return;
        sourcePending |= source; scenePending |= scene;
        if (!queued) { queued = true; poster.post(this::drainOnce); }
    }
    private void drainOnce() {
        boolean source;
        boolean scene;
        synchronized (this) {
            if (cancelled) { queued = false; return; }
            source = sourcePending; scene = scenePending;
            sourcePending = false; scenePending = false;
        }
        drain.render(source, scene);
        synchronized (this) {
            queued = false;
            if (!cancelled && (sourcePending || scenePending)) {
                queued = true; poster.post(this::drainOnce);
            }
        }
    }
    public synchronized void cancel() {
        cancelled = true; sourcePending = false; scenePending = false;
    }
}
```

- [ ] **Step 4: Run GREEN**

Run: `gradle testDebugUnitTest --tests '*FrameCoordinatorContractTest' && ./scripts/test-contracts.sh`

Expected: duplicate requests are bounded and existing contracts pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core/FrameCoordinator.java src/test/java/com/hellovoid/liquidui/glass/core/FrameCoordinatorContractTest.java
git commit -m "feat: centralize glass frame coalescing"
```

---

### Task 3: Centralize four-rotation DisplayTransform

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/DisplayTransformSnapshot.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/DisplayTransform.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/DisplayTransformEngine.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/DisplayTransformEngineContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/Miuix307PassBlurShaders.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java`
- Modify: `src/test/java/com/hellovoid/liquidui/glass/notification/NotificationOesMappingContractTest.java`

**Interfaces:**
- Consumes: Window/host frames, display and config rotation, producer size, ST 4x4 matrix, root and producer generations.
- Produces: `DisplayTransformEngine.compose(snapshot)` returning one column-major Window-UV-to-OES matrix.

- [ ] **Step 1: Write failing basis-point tests**

```java
@Test public void everyRotationPrecedesSurfaceTextureCrop() {
    float[] crop = affine(.25f, 0f, .05f, 0f, -.25f, .75f);
    assertPoint(0, 0f, 0f, crop, .05f, .75f);
    assertPoint(1, 0f, 0f, crop, .05f, .50f);
    assertPoint(2, 0f, 0f, crop, .30f, .50f);
    assertPoint(3, 0f, 0f, crop, .30f, .75f);
}

@Test public void generationsArePartOfTransformValidity() {
    DisplayTransform t = DisplayTransformEngine.compose(snapshot(3, 7L, 11L));
    assertTrue(t.matches(7L, 11L));
    assertFalse(t.matches(8L, 11L));
}
```

Also assert all four corners and center for rotations 0/1/2/3. Expected values apply the declared rotation affine first and ST crop/flip second; none contains a pixel offset.

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*DisplayTransformEngineContractTest'`

Expected: generic transform types are missing.

- [ ] **Step 3: Implement explicit affine composition**

```java
public record DisplayTransform(float[] windowUvToOes, long rootGeneration, long producerGeneration) {
    public DisplayTransform { windowUvToOes = windowUvToOes.clone(); }
    public boolean matches(long root, long producer) {
        return rootGeneration == root && producerGeneration == producer;
    }
}
```

`DisplayTransformEngine` composes `Window -> root`, then normalized rotation (`0:(x,y)`, `1:(y,1-x)`, `2:(1-x,1-y)`, `3:(1-y,x)`), then the supplied ST matrix. It rejects invalid dimensions, rotation codes, NaN/Inf elements, and generation mismatch. Replace shader-side `uConfigRot`, `orientRootUv`, `uBackdropRect`, and UV mirroring with:

```glsl
uniform mat4 uWindowUvToOes;
void main() {
    vec2 uv = (uWindowUvToOes * vec4(vUv, 0.0, 1.0)).xy;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) discard;
    gl_FragColor = vec4(texture2D(uTexture, uv).rgb, 1.0);
}
```

- [ ] **Step 4: Run GREEN**

Run: `gradle testDebugUnitTest --tests '*DisplayTransformEngineContractTest' --tests '*NotificationOesMappingContractTest' && ./scripts/test-contracts.sh`

Expected: four-rotation tests pass and shader contracts contain no orientation/crop compensation helper.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core/DisplayTransform* src/main/java/com/hellovoid/liquidui/glass/notification/Miuix307PassBlurShaders.java src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java src/test/java/com/hellovoid/liquidui/glass/core/DisplayTransformEngineContractTest.java src/test/java/com/hellovoid/liquidui/glass/notification/NotificationOesMappingContractTest.java
git commit -m "fix: centralize SystemUI backdrop transforms"
```

---

### Task 4: Add Window- and node-level presentation authority

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassActivationToken.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/WindowPresentationState.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/WindowPresentationStateContractTest.java`

**Interfaces:**
- Consumes: root/producer/scene/profile generations, swapped node lifecycle map, swap sequence.
- Produces: ids newly authorized for suppression and ids requiring restoration.

- [ ] **Step 1: Write failing authority tests**

```java
@Test public void newNodeWaitsForItsOwnSwap() {
    WindowPresentationState state = activeStateWith("old", 1L);
    state.scene(4L, Map.of("old", 1L, "new", 7L));
    assertTrue(state.accept(token(3L, 4L, 2L, Map.of("old", 1L))).isEmpty());
    assertEquals(Set.of("new"), state.accept(token(3L, 4L, 3L, Map.of("old", 1L, "new", 7L))));
}

@Test public void sourceLossRevokesAllPresentedNodes() {
    WindowPresentationState state = activeStateWith("a", 1L, "b", 2L);
    assertEquals(Set.of("a", "b"), state.sourceLost(state.producerGeneration()));
}
```

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*WindowPresentationStateContractTest'`

Expected: authority types are missing.

- [ ] **Step 3: Implement the pure policy**

```java
public record GlassActivationToken(long rootGeneration, long producerGeneration,
        long sceneGeneration, long profileVersion, long swapSequence,
        Map<String, Long> renderedNodes) {
    public GlassActivationToken { renderedNodes = Map.copyOf(renderedNodes); }
}
```

`WindowPresentationState` stores current generations, current node lifecycle map, presented map, fresh-source bit, last swap, and phase. `accept` returns only current `(id,lifecycleGeneration)` pairs present in the token. Source/root/profile mismatch rejects the whole token. `sourceLost`, `terminalFailure`, and `detach` return all ids to restore.

- [ ] **Step 4: Run GREEN**

Run: `gradle testDebugUnitTest --tests '*WindowPresentationStateContractTest' --tests '*NotificationGlassPresentationStateContractTest'`

Expected: generic and temporary legacy authority tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core/{GlassActivationToken,WindowPresentationState}.java src/test/java/com/hellovoid/liquidui/glass/core/WindowPresentationStateContractTest.java
git commit -m "feat: add per-window glass presentation authority"
```

---

### Task 5: Add global core and one-session-per-Window registry

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/WindowKey.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRegistry.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java`
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/WindowGlassRegistryContractTest.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java`

**Interfaces:**
- Consumes: attached host `View`, display id, session factory, adapter registrations.
- Produces: `SystemUiGlassCore.sessionFor(View)` and deterministic session/core shutdown.

- [ ] **Step 1: Write failing registry tests**

```java
@Test public void sameRootAndDisplayReuseOneSession() {
    Object root = new Object();
    WindowGlassRegistry registry = registry();
    assertSame(registry.sessionFor(new WindowKey(root, 0)), registry.sessionFor(new WindowKey(root, 0)));
    assertNotSame(registry.sessionFor(new WindowKey(root, 0)), registry.sessionFor(new WindowKey(new Object(), 0)));
}
```

The architecture test asserts one `new SystemUiGlassCore` in `ModuleMain`, none in feature packages, and one render-thread creation in the core package.

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*WindowGlassRegistryContractTest' --tests '*SystemUiGlassCoreArchitectureTest'`

Expected: core/registry types and composition are missing.

- [ ] **Step 3: Implement weak root identity and composition**

```java
public final class WindowKey {
    private final WeakReference<Object> root;
    private final int identityHash, displayId;
    public WindowKey(Object root, int displayId) {
        this.root = new WeakReference<>(Objects.requireNonNull(root));
        this.identityHash = System.identityHashCode(root); this.displayId = displayId;
    }
    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof WindowKey other) || displayId != other.displayId) return false;
        Object left = root.get(); Object right = other.root.get();
        return left != null && left == right;
    }
    @Override public int hashCode() { return 31 * identityHash + displayId; }
}
```

`WindowGlassRegistry` synchronizes exact get/create/remove. `SystemUiGlassCore` owns one `HandlerThread`, render `Handler`, transform engine, material registry, and Window registry. `WindowGlassSession` initially contains generic scene/presentation state, adapter bindings, injected renderer factory, and shutdown callback.

- [ ] **Step 4: Run GREEN**

Run: `gradle testDebugUnitTest --tests '*WindowGlassRegistryContractTest' --tests '*SystemUiGlassCoreArchitectureTest' && ./scripts/test-contracts.sh`

Expected: identity/reuse/removal and single-core contracts pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core/{WindowKey,WindowGlassRegistry,SystemUiGlassCore,WindowGlassSession}.java src/test/java/com/hellovoid/liquidui/glass/core/WindowGlassRegistryContractTest.java src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java
git commit -m "feat: add SystemUI glass core and window registry"
```

---

### Task 6: Move OES/EGL/Prismal into the generic Window renderer

**Files:**
- Create: core `WindowGlassRenderer.java`, `SystemUiPassBlurBridge.java`, `PassBlurShaders.java`, `GlassCompositor.java`, `MaterialProfileRegistry.java`, `GlassHostView.java`, `ViewRootSurfaceObserver.java`, `ProducerRecoveryState.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/WindowGlassRendererArchitectureTest.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/ProducerRecoveryStateContractTest.java`

**Interfaces:**
- Consumes: shared render `Handler`, generic scene, transform engine, material registry, root events.
- Produces: one source/output pipeline per Window with `requestScene`, producer enable/rebind/retire, and shutdown.

- [ ] **Step 1: Write failing ownership/recovery tests**

```java
assertEquals(1, count(coreSources, "new HandlerThread("));
assertFalse(notificationSources.contains("EGL14."));
assertFalse(notificationSources.contains("GL_TEXTURE_EXTERNAL_OES"));
assertFalse(notificationSources.contains("new SurfaceTexture("));
assertFalse(notificationSources.contains("new PrismalRenderer("));
assertTrue(renderer.contains("frameCoordinator.requestSourceFrame()"));
assertTrue(bridge.contains("SCALE = 1.0f"));
assertFalse(renderer.contains("glReadPixels"));
```

Recovery unit tests require repeated observe to preserve generation, root destroy to retire it, failed bind not to advance success generation, and rebind to require a new producer plus fresh frame.

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*WindowGlassRendererArchitectureTest' --tests '*ProducerRecoveryStateContractTest'`

Expected: generic renderer/recovery ownership is absent.

- [ ] **Step 3: Extract the validated pipeline without optical changes**

Mechanically genericize current notification renderer, bridge, shaders, compositor, material, host, observer, and recovery state into the listed core files. Inject the shared render handler; remove private thread creation. Replace current draw atomics with `FrameCoordinator`: source callbacks call `requestSourceFrame`, scene changes call `requestScene`, and one drain consumes newest source/scene and swaps once.

Stage A and `prepareBackdrop()` run only for a pending source; scene-only renders reuse the prepared current-generation backdrop. The renderer emits `GlassActivationToken` with exact rendered node lifecycles. Session-local FBOs/OES/ST/output EGLSurface stay on the owning renderer instance.

```java
for (GlassNode node : scene.nodes()) {
    if (!node.drawable()) continue;
    ResolvedMaterial material = materials.resolve(node.materialProfile());
    renderer.drawGlass(geometry(node), material.params(), material.highlights(), node.opacity());
}
```

- [ ] **Step 4: Run GREEN and assemble**

Run: `gradle testDebugUnitTest --tests '*ProducerRecoveryStateContractTest' --tests '*WindowGlassRendererArchitectureTest' --tests '*NotificationSharedGlassArchitectureTest' && ./scripts/test-contracts.sh && gradle assembleDebug`

Expected: ownership/recovery and temporary delegation contracts pass; APK assembles.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquidui/glass/core src/main/java/com/hellovoid/liquidui/glass/notification src/test/java/com/hellovoid/liquidui
git commit -m "refactor: move shared GPU glass into window core"
```

---

### Task 7: Migrate notifications to a geometry/material adapter

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java`
- Modify: `NotificationGlassNodeCollector.java`, `NotificationGlassRuntime.java`, `NotificationSharedGlassHook.java`, `ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/NotificationGlassAdapterArchitectureTest.java`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java`
- Delete: superseded notification renderer/session/scene/material/compositor/shader/bridge/observer/recovery files after migration is green.

**Interfaces:**
- Consumes: one injected `SystemUiGlassCore` and exact notification geometry/material authorities.
- Produces: `GlassNode` snapshots with `CARD` profile and feature-specific suppress/restore callbacks.

- [ ] **Step 1: Write failing adapter boundary tests**

```java
@Test public void notificationPackageOwnsNoGpuPipeline() throws Exception {
    String sources = readTree("src/main/java/com/hellovoid/liquidui/glass/notification");
    assertFalse(sources.contains("EGL14."));
    assertFalse(sources.contains("GL_TEXTURE_EXTERNAL_OES"));
    assertFalse(sources.contains("new SurfaceTexture("));
    assertFalse(sources.contains("new PrismalRenderer("));
    assertFalse(sources.contains("SetPassBlurSurface"));
}
```

Also require exactly one core construction in `ModuleMain` and core injection into the notification hook.

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*NotificationGlassAdapterArchitectureTest'`

Expected: notification still owns GPU resources and no injected core exists.

- [ ] **Step 3: Implement adapter and delete duplicate ownership**

`ModuleMain` constructs the core after target resolution and injects it into hooks. `NotificationGlassAdapter` obtains `core.sessionFor(stack)`, publishes stable identity-derived ids, monotonically increasing lifecycle generations, native 24dp resource radius, opacity/order, and `CARD` profile. Pre-draw publishes immediately without `postOnAnimation`.

Suppress a row/wrapper only when the session authorizes its exact node generation. Source loss, removal, rollback, and terminal failure restore corresponding native material. Keep the old probe only as uninstantiated diagnostic evidence; remove all superseded production GPU files.

- [ ] **Step 4: Run full GREEN**

Run: `./scripts/test-contracts.sh && gradle testDebugUnitTest assembleDebug`

Expected: all tests/build pass and notification sources own no GPU pipeline.

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java/com/hellovoid/liquidui src/test/java/com/hellovoid/liquidui
git commit -m "refactor: migrate notifications to shared window glass"
```

---

### Task 8: Verify Phase 0 and prepare the device-validation build

**Files:**
- Modify: `README.md`
- Create: `docs/reverse-engineering/systemui-001/systemui-wide-shared-glass-core.md`
- Create: `docs/superpowers/plans/2026-09-07-systemui-wide-shared-glass-phase-0-runtime-checklist.md`
- Modify: `SystemUiGlassCoreArchitectureTest.java` only for final exact filenames.

**Interfaces:**
- Consumes: completed core and notification adapter.
- Produces: ownership evidence, runtime checklist, and verified debug APK.

- [ ] **Step 1: Add final source invariants**

```java
assertEquals(1, count(module, "new SystemUiGlassCore("));
assertEquals(1, count(coreTree, "new HandlerThread("));
assertFalse(notificationTree.contains("postOnAnimation"));
assertFalse(notificationTree.contains("EGL14."));
assertFalse(notificationTree.contains("SetPassBlurSurface"));
assertFalse(allProduction.contains("glReadPixels"));
assertFalse(allProduction.contains("PixelCopy"));
assertTrue(passBlurBridge.contains("SCALE = 1.0f"));
```

- [ ] **Step 2: Run the final architecture test**

Run: `gradle testDebugUnitTest --tests '*SystemUiGlassCoreArchitectureTest'`

Expected: PASS; fix production ownership rather than weakening an invariant.

- [ ] **Step 3: Document runtime evidence and acceptance**

Record process core -> Window registry -> notification adapter ownership, root/producer transitions, four-rotation transform inputs, node-token fallback behavior, and rate-limited session diagnostics. The runtime checklist covers both portraits, both landscapes, drag/scroll/expand, root rollover, loss/recovery, and constant ownership counts. New component adapters remain out of scope until Phase 0 device acceptance.

- [ ] **Step 4: Run fresh full verification**

Run: `./scripts/test-contracts.sh`

Run: `gradle testDebugUnitTest assembleDebug`

Run: `sha256sum build/outputs/apk/debug/LiquidUI-debug.apk`

Run: `git diff --check && git status --short`

Expected: zero failures, build exit 0, one SHA-256 line, no whitespace errors, and only intended documentation changes before commit.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/reverse-engineering/systemui-001/systemui-wide-shared-glass-core.md docs/superpowers/plans/2026-09-07-systemui-wide-shared-glass-phase-0-runtime-checklist.md src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java
git commit -m "docs: define shared glass core runtime validation"
```

After this commit, push the branch and offer the APK only as a Phase 0 notification-parity validation build. Start Phase 1 reverse engineering only after notification rendering, both portrait directions, latency, fallback, and lifecycle pass on device.
