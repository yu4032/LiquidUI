# Notification Red Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Force standard SystemUI notification row backgrounds to fully opaque pure red on exact target `systemui-001`.

**Architecture:** Add one notification-scoped `SystemUiHook` that installs two exact argument rewrites through the API101 interceptor chain: `NotificationBackgroundView#setCustomBackground(int)` is forced to `notification_material_bg`, and `ActivatableNotificationView#setBackgroundTintColor(int)` is forced to `0xFFFF0000`. Keep SystemUI's original methods intact so roundness, Ripple, clipping, and lifecycle logic remain authoritative.

**Tech Stack:** Java 17, Android SDK 37 compile target, libxposed API 101.0.1, JUnit 4, exact-build SystemUI reverse engineering with JADX 1.5.6.

**Spec:** `docs/superpowers/specs/2026-09-05-notification-red-background-design.md`

## Global Constraints

- Runtime target is only `systemui-001`: `com.android.systemui` versionCode `202501210`, versionName `16.03.251211.r`, SDK 36.
- Output color is exactly opaque red `0xFFFF0000`.
- Resolve SystemUI-private classes only through the target process `ClassLoader`.
- No fuzzy class/method fallback, resource-name scanning, fixed delays, or whole-row `ColorDrawable` replacement.
- Resolve `R.drawable.notification_material_bg` exactly from the target `R$drawable` class; do not hard-code its numeric resource ID.
- Dynamic Island, guts/detail panels, overall shade background, and app RemoteViews backgrounds are out of scope.
- Hook installation must report explicit `INSTALLED`, `UNSUPPORTED`, or `FAILED` through the existing registry contract.

---

### Task 1: Notification tint policy contract

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundPolicy.java`
- Create: `src/test/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundPolicyContractTest.java`

**Interfaces:**
- Produces: `NotificationRedBackgroundPolicy.OPAQUE_RED` and `int rewriteTint(int originalTint)`.

- [ ] **Step 1: Write the failing policy contract**

```java
assertEquals(0xFFFF0000, NotificationRedBackgroundPolicy.OPAQUE_RED);
assertEquals(0xFFFF0000, NotificationRedBackgroundPolicy.rewriteTint(0));
assertEquals(0xFFFF0000, NotificationRedBackgroundPolicy.rewriteTint(0x55FFFFFF));
```

- [ ] **Step 2: Run `./scripts/test-contracts.sh` and verify RED because the policy class does not exist.**

- [ ] **Step 3: Implement the minimal policy**

```java
public final class NotificationRedBackgroundPolicy {
    public static final int OPAQUE_RED = 0xFFFF0000;
    private NotificationRedBackgroundPolicy() {}
    public static int rewriteTint(int originalTint) { return OPAQUE_RED; }
}
```

- [ ] **Step 4: Run the contract suite and verify GREEN.**

### Task 2: Exact notification hook installer

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundHook.java`
- Create: `src/test/java/com/hellovoid/liquidui/hook/systemui/notification/NotificationRedBackgroundHookContractTest.java`

**Interfaces:**
- Consumes: `NotificationRedBackgroundPolicy.OPAQUE_RED`.
- Produces: `NotificationRedBackgroundHook implements SystemUiHook`, id `notification.red-background`.

- [ ] **Step 1: Write failing architecture/contract tests** proving the hook ID, exact class names, exact method names, `int.class` parameters, exact `R$drawable.notification_material_bg` field, and target-ClassLoader resolution contract.
- [ ] **Step 2: Run contract suite and verify RED.**
- [ ] **Step 3: Implement installation** by resolving the two notification classes and `R$drawable` through `TargetClassResolver`, resolving `setBackgroundTintColor(int)` and `setCustomBackground(int)`, resolving the static `notification_material_bg` resource ID, and registering highest-priority argument-rewrite interceptors for the material background and opaque-red tint.
- [ ] **Step 4: Map class/method absence to `UNSUPPORTED`; map hook registration/access failures to `FAILED`; return `INSTALLED` only after registration succeeds.**
- [ ] **Step 5: Run contract suite and verify GREEN.**

### Task 3: Registry integration and reverse-engineering contract

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/ModuleMain.java`
- Modify: `docs/reverse-engineering/systemui-001/CLASS_INDEX.md`
- Create: `docs/reverse-engineering/systemui-001/notification-red-background.md`
- Create: `src/test/java/com/hellovoid/liquidui/architecture/NotificationHookRegistrationArchitectureTest.java`

**Interfaces:**
- Consumes: `NotificationRedBackgroundHook`.
- Produces: default SystemUI registry containing the red-background hook.

- [ ] **Step 1: Write a failing architecture test** requiring `ModuleMain` to construct a registry containing `new NotificationRedBackgroundHook()` and forbidding `SystemUiHookRegistry.empty()` as the production registry.
- [ ] **Step 2: Run contract suite and verify RED.**
- [ ] **Step 3: Register exactly one notification hook in `ModuleMain`.**
- [ ] **Step 4: Document the decompiled call chain and explicit exclusions.**
- [ ] **Step 5: Run `./scripts/test-contracts.sh` and verify all tests pass.**

### Task 4: Android build and PR verification

**Files:**
- No production source changes unless CI exposes an actual compile/runtime-contract defect.

**Interfaces:**
- Produces: buildable debug APK artifact for device testing.

- [ ] **Step 1: Push the feature branch and open a PR to `main`.**
- [ ] **Step 2: Require GitHub Actions `testDebugUnitTest assembleDebug` to succeed.**
- [ ] **Step 3: Confirm `LiquidUI-api101-debug` artifact exists.**
- [ ] **Step 4: Review PR changed-file list to confirm no APK/DEX/JADX output or temporary reverse-engineering payload is committed.**
