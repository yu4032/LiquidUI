# Notification View-owned PassBlur Implementation Plan

**Goal:** Replace notification target discovery and PassBlur ownership with HyperOS material-dispatch authority while keeping Prismal code available but dormant.

**Architecture:** Add an after-invocation hook backend; use `applyElementViewBlend` scope + `setMiBackgroundBlendColors` exact View discovery; maintain weak target/row/round-state registry; clear all five native blur states on the exact target and re-enable per-View HWUI PassBlur/material; remove row attach/wrapper/NSSL and global Shade blur hooks from active notification routing.

**Tech:** Java, libxposed API 101, Android hidden View APIs via reflection, JUnit source-contract tests, GitHub Actions API101 build.

## Task 1 — RED architecture contract

Update the native PassBlur experiment test so the old NotificationShade endpoint architecture fails. Require exact material-dispatch hooks, an after-hook backend, five-state exact-target suppression, round authority, and no active `NotificationPassBlurTextureView`/`SystemUiPassBlurBridge` routing.

## Task 2 — Hook lifecycle support

Add `AfterMethodHookBackend` and API101 adapter. It must call the callback after `chain.proceed()` and also clear scope in `finally` when the target method throws.

## Task 3 — Material target and round authority

Add `NotificationMaterialTargetRegistry` using weak references/maps. Register only Views seen by `setMiBackgroundBlendColors` while the ThreadLocal notification scope is active. Walk parents to resolve `ExpandableNotificationRow`. Observe `NotificationUtil#setRoundRect` and `NotificationChildrenContainer#setChildrenExpanded` as final rounding state.

## Task 4 — Exact-target native material controller

Refactor `NotificationVendorMaterialController` away from guessed `mBackgroundNormal`. Resolve and invoke the five hidden View clearing APIs on the exact target, then enable View-owned PassBlur/material. Preserve existing vendor blend colors when possible instead of inventing a screen-capture or NotificationShade producer.

## Task 5 — Replace active routing

Rewrite `NotificationLiquidGlassHook` to install only the material-dispatch/round hooks required by this experiment. Do not register row attach, wrapper reinflate, ShadeBlendBlurController, BlurUtils, NotificationPanel `setPassWindowBlurEnabled`, or caller-owned SurfaceControl endpoint hooks. Leave Prismal/OES classes in source but unreferenced by the active notification hook.

## Task 6 — GREEN verification

Run SDK-independent contracts and `testDebugUnitTest assembleDebug` in CI. Inspect the branch diff to confirm ScreenCapture is absent, NotificationShade `SetPassBlurSurface` is not reachable from the active hook, five-state clearing is exact-target, and all hook registrations are transactional. Runtime frozen-frame/visual validation remains a device test because CI cannot execute HyperOS HWUI hidden APIs.
