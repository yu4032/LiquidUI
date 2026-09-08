# Notification Native Commit Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace notification geometry publication timing with `NotificationStackScrollLayout.applyCurrentState$1()` AFTER-hook authority while preserving bounded native animation tracking and the shared Shade renderer.

**Architecture:** Stable geometry snapshots are captured only after HyperOS has applied `ExpandableViewState` to notification rows. `requestChildrenUpdate()` remains a SystemUI scheduling detail and is removed from LiquidUI publication. `setAnimationRunning(boolean)` remains the only bounded per-frame compensation for cross-tree shared-renderer geometry during native property animations.

**Tech Stack:** Android/Java 17, LSPosed/Xposed-style hook backends, HyperOS SystemUI 16.03.251211.r, JUnit contract tests, Gradle 9.6.1 CI.

**Spec:** `docs/superpowers/specs/2026-09-08-notification-native-commit-authority-design.md`

## Global Constraints

- Keep one shared `NotificationShadeWindowView` renderer; do not create per-row renderers.
- Do not change Prismal shader parameters, Gaussian quality, PassBlur source resolution, root Shade blur policy, or Control Center adapters.
- Do not restore a permanent notification `OnPreDrawListener`.
- Native material suppression remains presentation-authorized only.
- Do not merge to `main` before Stage 1 device validation.

---

### Task 1: Lock the native commit authority in contracts

**Files:**
- Modify: `src/test/java/com/hellovoid/liquidui/glass/notification/NotificationTransitionArchitectureContractTest.java`

**Interfaces:**
- Consumes: current `NotificationSharedGlassHook`, `NotificationGlassRuntime`, `NotificationGlassAdapter` source structure.
- Produces: RED contracts requiring `applyCurrentState$1`, `onNativeStateCommitted`, and forbidding request-triggered publication.

- [ ] **Step 1: Replace the old requestChildrenUpdate authority assertion**

Require the production hook/runtime to contain:

```java
assertTrue(hook.contains("applyCurrentState$1"));
assertTrue(runtime.contains("onNativeStateCommitted"));
assertFalse(hook.contains("afterBackend.intercept(\n                    requestChildrenUpdate"));
assertFalse(runtime.contains("onChildrenUpdateRequested"));
assertFalse(adapter.contains("scheduleAfterNativeChildrenUpdate"));
```

Keep existing assertions that `updateExpandedHeight`, `mExpandedFraction`, and `onPanelExpansion` are absent.

- [ ] **Step 2: Strengthen animation-final-capture contract**

Require:

```java
assertTrue(adapter.contains("setNativeAnimationRunning"));
assertTrue(adapter.contains("removeAnimationPreDraw"));
assertTrue(adapter.contains("refreshScene();"));
```

The production implementation must remove the animation listener before the final capture when `running=false`.

- [ ] **Step 3: Run CI and verify RED**

Expected: `scripts/test-contracts.sh` fails only on the new native-commit authority expectations; unrelated contracts remain green.

- [ ] **Step 4: Commit RED contract**

Commit message:

```text
test: require native notification state commit authority
```

---

### Task 2: Route stable notification refresh from applyCurrentState$1

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationSharedGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java`

**Interfaces:**
- Consumes: `NotificationStackScrollLayout` instance as `View`.
- Produces: `NotificationGlassRuntime.onNativeStateCommitted(View stack)`.

- [ ] **Step 1: Resolve the exact HyperOS method**

Replace the `requestChildrenUpdate` method field with:

```java
final Method applyCurrentState;
...
applyCurrentState = accessible(stackClass.getDeclaredMethod("applyCurrentState$1"));
```

- [ ] **Step 2: Install AFTER hook on the commit method**

Add:

```java
rollbacks.add(afterBackend.intercept(
        applyCurrentState,
        AfterMethodHookBackend.PRIORITY_HIGHEST,
        (thisObject, args) -> {
            if (thisObject instanceof View stack) {
                runtime.onNativeStateCommitted(stack);
            }
        })::unhook);
```

Delete LiquidUI's `requestChildrenUpdate` interception entirely.

- [ ] **Step 3: Replace runtime request scheduling with direct committed-state refresh**

Use:

```java
void onNativeStateCommitted(View stack) {
    if (stack == null) return;
    NotificationGlassAdapter adapter = adapters.get(stack);
    if (adapter != null && !adapter.isShutdown()) {
        adapter.onNativeStateCommitted();
    }
}
```

No `post`, `postOnAnimation`, or one-shot pre-draw is permitted here.

- [ ] **Step 4: Commit production routing**

Commit message:

```text
fix: follow committed notification ViewState
```

---

### Task 3: Remove obsolete request-frame listener from the adapter

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java`

**Interfaces:**
- Consumes: `onNativeStateCommitted()` from runtime.
- Produces: immediate `refreshScene()` after native commit; bounded animation listener remains unchanged.

- [ ] **Step 1: Add the committed-state entry point**

```java
void onNativeStateCommitted() {
    if (isShutdown()) return;
    refreshScene();
}
```

- [ ] **Step 2: Delete obsolete request listener state and methods**

Remove:

```text
childrenUpdateObserver
childrenUpdateListener
scheduleAfterNativeChildrenUpdate()
removeChildrenUpdatePreDraw()
```

Remove calls to `removeChildrenUpdatePreDraw()` from shutdown/fail-closed paths.

- [ ] **Step 3: Preserve bounded native-animation semantics**

`setNativeAnimationRunning(false)` must:

```java
nativeAnimationRunning = false;
removeAnimationPreDraw();
refreshScene();
```

`setNativeAnimationRunning(true)` installs exactly one NSSL-scoped pre-draw listener and captures the current scene once.

- [ ] **Step 4: Commit adapter cleanup**

Commit message:

```text
refactor: remove notification request-frame polling
```

---

### Task 4: Add bounded diagnostics for device validation

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassAdapter.java`

**Interfaces:**
- Produces log markers usable with adb without changing behavior.

- [ ] **Step 1: Log commit authority hits at a bounded cadence**

Maintain a counter per runtime and emit a rate-limited line such as:

```text
[LUI][NotifGlass][Runtime] native commit count=... stack=...
```

Do not log every frame indefinitely; emit first few hits and then every fixed coarse interval.

- [ ] **Step 2: Log animation lifecycle edges**

Emit only on state changes:

```text
[LUI][NotifGlass][Adapter] native animation running=true
[LUI][NotifGlass][Adapter] native animation running=false
```

- [ ] **Step 3: Commit diagnostics**

Commit message:

```text
diag: trace notification native commit timing
```

---

### Task 5: Full verification and device APK

**Files:** none beyond prior tasks.

- [ ] **Step 1: Run SDK-independent contracts**

Run:

```bash
./scripts/test-contracts.sh
```

Expected: PASS.

- [ ] **Step 2: Run Android verification**

Run in CI:

```bash
gradle testDebugUnitTest assembleDebug --stacktrace
```

Expected: PASS and `LiquidUI-api101-debug` artifact uploaded.

- [ ] **Step 3: Verify diff scope**

Only Stage 1 timing files/tests/docs may change. No Prismal, renderer, Control Center, blur policy, or material transaction code changes.

- [ ] **Step 4: Device gate**

Install the CI APK and test normal Shade open, close, notification scroll, row expansion/grouping, and notification/control-center page switching. PASS requires notification glass to appear without Advanced Material, follow rows without freeze/top stacking, and leave Control Center unchanged.
