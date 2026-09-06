# Notification Shared Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one shared NotificationShade PassBlur -> Prismal compositor that renders every visible notification row background.

**Architecture:** Port LiquidDock's portable Prismal and zero-copy PassBlur/OES core, but create SystemUI-specific session, bridge, row collector, and reversible vendor-material handoff. One NSSL root owns one GPU pipeline; rows are immutable nodes.

**Tech Stack:** Android 16 / SDK 36 target runtime, Java 17, libxposed API 101, SurfaceControl private HyperOS PassBlur APIs, SurfaceTexture/OES, EGL/GLES2, Prismal.

**Spec:** `docs/superpowers/specs/2026-09-05-notification-shared-liquid-glass-design.md`

## Global Constraints

- Exact target only: `systemui-001`, `com.android.systemui`, `16.03.251211.r`, SDK 36.
- Target-process private classes resolve only through the target process ClassLoader.
- Exactly one producer/TextureView/EGL pipeline per NSSL root.
- No HyperOS/native notification-style branch.
- No CPU capture/readback fallback.
- Vendor background stays visible until a successful non-empty Prismal frame is swapped.
- Failure restores vendor material and fails closed.
- No Launcher lifecycle or fixed-delay workaround.

---

### Task 1: Portable Prismal module

**Files:** `prismal/**`, `settings.gradle.kts`, `build.gradle.kts`, `THIRD_PARTY_NOTICES.md`

- [x] Add a `:prismal` Android library matching LiquidDock's Java 17/SDK contract.
- [x] Port the current renderer/model used by LiquidDock's verified build.
- [x] Add MIT attribution and project dependency.
- [x] Verify the SDK-independent architecture contract.

### Task 2: Zero-copy geometry and producer state

**Files:** `glass/notification/Miuix307BackdropMapping.java`, `ZeroCopyProducerRecoveryState.java`, shader/material helpers.

- [x] Port the validated Stage-B mapping and Prismal defaults.
- [x] Add producer recovery plus endpoint generation identity.
- [x] Reject CPU/screenshot capture APIs in architecture tests.

### Task 3: SystemUI PassBlur bridge

**Files:** `glass/notification/SystemUiPassBlurBridge.java`

- [x] Bind the SystemUI ViewRoot `SurfaceControl` through `SetPassBlurSurface`.
- [x] Support update pause/resume and deterministic unbind.
- [x] Stamp ViewRoot/surface-sequence/layer/endpoint identity.

### Task 4: Notification row geometry snapshots

**Files:** `NotificationGlassNode*.java`, `NotificationGlassScene*.java`

- [x] Reflect exact `systemui-001` row/background geometry on the UI thread.
- [x] Match `NotificationBackgroundView.onDraw()` actual-width/RTL/expand/bottom-clip behavior.
- [x] Publish immutable scene snapshots only.

### Task 5: Shared GPU renderer

**Files:** `NotificationPassBlurTextureView.java`, `NotificationGlassCompositor.java`

- [x] Own one OES producer and one EGL output.
- [x] Normalize one source frame, call `prepareBackdrop()` once, draw N notification nodes, and swap once.
- [x] Reject stale endpoint-generation bind completion.
- [x] Signal activation only after a successful non-empty scene frame is swapped.

### Task 6: Session, registry, and reversible material handoff

**Files:** `NotificationGlassSession.java`, `NotificationGlassRuntime.java`, `NotificationGlassHostView.java`, `NotificationVendorMaterialController.java`

- [x] Insert one sibling host immediately below NSSL.
- [x] Track rows/wrappers through weak ownership.
- [x] Give every session its own material-restore state.
- [x] Suppress vendor material only after first valid GPU frame.
- [x] Restore on terminal failure/detach without reentrant host removal.

### Task 7: Exact hook and configuration

**Files:** `NotificationLiquidGlassHook.java`, `ModuleMain.java`, config/settings files.

- [x] Hook exact row attach/detach and wrapper reinflation declarations.
- [x] Do not branch on notification style.
- [x] Replace the red proof hook with `notification.liquid-glass`.
- [x] Add `notification_glass_enabled` schema/settings wiring.

### Task 8: Verification and device artifact

- [x] Run SDK-independent contracts locally.
- [ ] Push one clean feature commit based on merged `main`.
- [ ] Run GitHub `testDebugUnitTest assembleDebug` with R8 optimization.
- [ ] Fix any Android/AGP compilation failures from the port.
- [ ] Confirm the final branch contains no staging/import payloads.
- [ ] Download the debug APK and record SHA-256.
- [ ] Device-check first OES frame, first EGL material draw, multi-row geometry, scrolling, lockscreen/heads-up, and vendor-material restoration on failure.
