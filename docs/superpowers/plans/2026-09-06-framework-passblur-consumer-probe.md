# Framework PassBlur Consumer Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only `systemui-001` diagnostic that identifies the real NotificationShade PassBlur owner/consumer path and the remaining notification corner authority without mutating framework PassBlur state.

**Architecture:** Keep the existing working notification glass path intact. Add a bounded transaction observer keyed to the live NotificationShade root, extend the existing framework object-graph probe with an authoritative Shade-root trigger, and add a one-shot read-only corner hierarchy snapshot. The experiment must produce diagnostics only; it must not invoke producer or consumer mutation APIs.

**Tech Stack:** Java 17, Android framework reflection, libxposed API 101 hook backends, existing LiquidUI contract runner, Gradle Android build.

**Spec:** `docs/superpowers/specs/2026-09-06-framework-passblur-consumer-probe-design.md`

## Global Constraints

- Exact target remains `systemui-001`: `com.android.systemui`, versionCode `202501210`, versionName `16.03.251211.r`, SDK 36.
- The new diagnostic must not call `SetPassBlurSurface`, `setUpdateTextureFlag`, `addTextureView`, `removeTextureView`, `Surface.release`, `SurfaceTexture.release`, or `SurfaceControl.Transaction.apply()`.
- The existing notification glass source/content-authority path is not replaced by this probe.
- No Prismal optical parameter, overscan, row radius, or native material-suppression behavior changes are allowed in this diagnostic branch.
- All diagnostic failures fail closed and must not disable native SystemUI rendering.
- No merge is performed as part of this plan.

---

### Task 1: NotificationShade PassBlur transaction ownership observer

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurTransactionProbe.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbe.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeHook.java`
- Modify: `src/test/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeContractTest.java`

**Interfaces:**
- Consumes: a live `NotificationPanelView` and its `ViewRootImpl#getSurfaceControl()` result.
- Produces: `FrameworkPassBlurTransactionProbe.registerShadeRoot(SurfaceControl)` and `FrameworkPassBlurTransactionProbe.observeSetPassBlurSurface(Object,Object[])` / `observeSetUpdateTextureFlag(Object,Object[])` callbacks used only by before-method hooks.

- [ ] **Step 1: Write failing contract assertions**

Add assertions requiring the probe hook to resolve and intercept both framework transaction methods, to register the live Shade root before transaction matching, and to use the `[NotifGlass][FrameworkPB][TX]` marker. Add negative assertions forbidding `ArgumentRewriteHookBackend`, `invoke(` on either mutation method, transaction construction/application, or release APIs from the diagnostic probe sources.

- [ ] **Step 2: Run contracts and verify RED**

Run:

```bash
./scripts/test-contracts.sh
```

Expected: `FrameworkPassBlurProbeContractTest` fails because the transaction observer and required markers do not exist yet.

- [ ] **Step 3: Implement the minimal read-only observer**

`FrameworkPassBlurTransactionProbe` should:

```java
final class FrameworkPassBlurTransactionProbe {
    static void registerShadeRoot(SurfaceControl root) { ... }
    static void observeSetPassBlurSurface(Object thisObject, Object[] args) { ... }
    static void observeSetUpdateTextureFlag(Object thisObject, Object[] args) { ... }
}
```

The observer stores only identity/layer/name metadata for the current valid Shade root, filters transaction callbacks before logging, assigns a monotonic sequence number, deduplicates unchanged update state, and logs at most a small fixed number of full stack traces. Stack output must be bounded to the first useful Java frames and exclude the probe package itself where possible.

`FrameworkPassBlurProbe.inspect(...)` must register the exact `ViewRootImpl#getSurfaceControl()` as the Shade root. `FrameworkPassBlurProbeHook.install(...)` must add before-method hooks for:

```java
SurfaceControl.Transaction.SetPassBlurSurface(SurfaceControl.class, Surface.class)
SurfaceControl.Transaction.setUpdateTextureFlag(SurfaceControl.class, boolean.class, float.class)
```

The callbacks only forward observations; they never rewrite args or call the methods themselves.

- [ ] **Step 4: Run contracts and verify GREEN**

Run:

```bash
./scripts/test-contracts.sh
```

Expected: all contracts pass.

- [ ] **Step 5: Commit**

Commit message:

```text
diagnostic: trace NotificationShade PassBlur ownership
```

---

### Task 2: Authoritative framework PassBlur consumer discovery trigger

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbe.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeHook.java`
- Modify: `src/test/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlurProbeContractTest.java`

**Interfaces:**
- Consumes: NotificationPanelView attach/pass-blur lifecycle plus the first matching NotificationShade transaction observation from Task 1.
- Produces: repeatable-but-bounded `FrameworkPassBlurProbe.inspectIfGenerationChanged(View)` diagnostics that can re-run when the live ViewRoot/renderer/pass-blur holder changes.

- [ ] **Step 1: Write failing contract assertions**

Require the probe to support a generation-aware re-inspection path rather than permanently suppressing a ViewRoot after the first early probe. Require method search terms for both `addTextureView` and `removeTextureView`, and require the transaction observer to request a deferred read-only re-inspection on the UI-thread panel after the first matching Shade transaction.

- [ ] **Step 2: Run contracts and verify RED**

Run `./scripts/test-contracts.sh` and expect the new assertions to fail.

- [ ] **Step 3: Implement generation-aware bounded inspection**

Replace the one-shot root identity set with a small state keyed by ViewRoot identity plus a generation/fingerprint derived from relevant runtime holder identities (`mThreadedRenderer` and discovered pass/blur/texture/surface holder identities). Allow re-inspection only when that fingerprint changes or when the first matching PassBlur transaction explicitly marks the root dirty. Keep maximum object/depth/field/method budgets bounded.

Log exact candidate runtime class, identity, object path, and signatures for methods matching:

```text
addTextureView
removeTextureView
passBlur
passWindowBlur
texture
surface
blur
```

Never invoke any candidate method.

- [ ] **Step 4: Run contracts and verify GREEN**

Run `./scripts/test-contracts.sh` and expect all contracts to pass.

- [ ] **Step 5: Commit**

Commit message:

```text
diagnostic: re-probe framework PassBlur consumers
```

---

### Task 3: Read-only notification corner authority snapshot

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCornerAuthorityProbe.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java`
- Create: `src/test/java/com/hellovoid/liquidui/glass/notification/NotificationCornerAuthorityProbeContractTest.java`

**Interfaces:**
- Consumes: the first drawable `ExpandableNotificationRow`, its `mBackgroundNormal`, the resulting immutable `NotificationGlassNode`, and the current renderer `BackdropSnapshot`.
- Produces: one bounded `[NotifGlass][CornerProbe]` hierarchy/node snapshot plus one Stage-B mapping snapshot per session/generation.

- [ ] **Step 1: Write failing contract tests**

Require the new probe to log row/background bounds, `actualWidth`/`actualHeight`, clip values, expansion state, top/bottom radius, parent `clipChildren`/`clipToPadding`/`clipToOutline`/outline-provider class, and final node geometry/radii. Require the renderer-side snapshot to include `backdropRect`, valid dock rect, overscan insets, and coverage. Add negative assertions forbidding setters for clip/outline/radius/layout properties.

- [ ] **Step 2: Run contracts and verify RED**

Run `./scripts/test-contracts.sh` and expect the new corner probe contract to fail before implementation.

- [ ] **Step 3: Implement one-shot read-only corner diagnostics**

`NotificationCornerAuthorityProbe` exposes package-private static methods such as:

```java
static void observeNode(
        Object row,
        View background,
        View host,
        NotificationGlassNode node,
        int actualWidth,
        int actualHeight,
        int clipTop,
        int clipBottom,
        boolean expand,
        float topRadius,
        float bottomRadius)
```

Walk parents from row/background toward the shared host with a strict maximum depth and log only ViewGroup clipping booleans, clip-to-outline, outline-provider class, class name, screen bounds, and alpha. Do not request outlines or mutate views.

`NotificationGlassNodeCollector.collect(...)` calls this observer only after constructing the immutable node. `NotificationPassBlurTextureView` emits a one-shot mapping diagnostic from the already-computed immutable `BackdropSnapshot`; it must not alter the snapshot or sampling math.

- [ ] **Step 4: Run contracts and verify GREEN**

Run `./scripts/test-contracts.sh` and expect all contracts to pass.

- [ ] **Step 5: Commit**

Commit message:

```text
diagnostic: trace notification corner authority
```

---

### Task 4: Full verification, draft PR, and device artifact

**Files:**
- Modify only if needed for contract registration: `scripts/test-contracts.sh`
- No production behavior changes are allowed in this task.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: green CI, a draft PR, and a debug APK artifact for device diagnosis.

- [ ] **Step 1: Run the complete local contract suite**

```bash
./scripts/test-contracts.sh
```

Expected: PASS with zero failures.

- [ ] **Step 2: Verify diagnostic mutation ban**

Run source searches equivalent to:

```bash
grep -R -nE 'SetPassBlurSurface.*invoke|setUpdateTextureFlag.*invoke|addTextureView.*invoke|removeTextureView.*invoke|new SurfaceControl.Transaction|\.apply\(\)|\.release\(\)' \
  src/main/java/com/hellovoid/liquidui/glass/notification/FrameworkPassBlur* \
  src/main/java/com/hellovoid/liquidui/glass/notification/NotificationCornerAuthorityProbe.java
```

Expected: no mutation calls in diagnostic sources.

- [ ] **Step 3: Run Android tests/build**

```bash
gradle testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Open a draft PR**

Base the PR on the branch containing commit `4256ec15af30e51d8f4bb28fae8de50af9d10397` without merging. Describe the probe as diagnostic-only and list the exact log filters/device success criteria from the spec.

- [ ] **Step 5: Verify remote CI**

Inspect the PR head workflow. Do not claim success until both contracts and Android build steps are green on the exact head commit.

- [ ] **Step 6: Download and expose the exact CI APK**

Download the successful workflow artifact, extract the debug APK, compute SHA-256, and provide the artifact plus the recommended logcat filter:

```bash
adb logcat -c
adb shell su -c 'killall com.android.systemui'
adb logcat -v threadtime | grep -E \
'FrameworkPB|CornerProbe|NotifGlass|PassBlur|SetPassBlurSurface|setUpdateTextureFlag|addTextureView|removeTextureView'
```

The device run is successful diagnostically when the log identifies the endpoint owner/stack, a concrete framework consumer candidate/signature or explicit absence, and the row/parent/node corner authorities.