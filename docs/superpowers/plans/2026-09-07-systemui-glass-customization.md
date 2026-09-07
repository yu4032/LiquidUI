# SystemUI Glass Customization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add LiquidDock-grade global/per-profile glass customization with a complete Miuix Compose GUI and live SystemUI hot preview without changing Window GPU ownership.

**Architecture:** Typed preference state becomes an immutable `GlassStyleConfig`. One process `GlassConfigRuntime` observes API101 Remote Preferences and publishes coalesced versions into `SystemUiGlassCore`; existing Window sessions update only `MaterialProfileRegistry` and scene rendering. Global is the default style; CARD/TILE/SLIDER/PANEL/FLOATING may opt into complete independent optical overrides while all profiles share the same Window producer and global sampling authority.

**Tech Stack:** Java 17, Kotlin/Compose, Miuix KMP preferences, Android SharedPreferences/API101 Remote Preferences, Prismal, JUnit4 source/contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-07-systemui-glass-customization-design.md`

## Global Constraints

- Preserve exactly one process `SystemUiGlassCore` and one production glass `HandlerThread`.
- Preserve one PassBlur/OES producer endpoint per Window.
- Optical setting changes never rebuild EGL, SurfaceTexture, OES, PassBlur binding, or producer recovery.
- Notification package remains GPU-owner-free.
- No CPU/readback capture path may be introduced.
- Initial Global optics must equal the current Phase 0 LiquidUI profile.
- Sampling extras are global Window/producer settings, not per-profile material settings.
- Slider writes are immediate; SystemUI propagation is last-write-wins/coalesced.

---

### Task 1: Typed glass configuration and profile inheritance

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/config/ConfigKey.java`
- Modify: `src/main/java/com/hellovoid/liquidui/config/ConfigSource.java`
- Modify: `src/main/java/com/hellovoid/liquidui/config/ConfigReader.java`
- Modify: `src/main/java/com/hellovoid/liquidui/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquidui/config/LiquidUiConfig.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/GlassParameter.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/GlassStyleConfig.java`
- Test: `src/test/java/com/hellovoid/liquidui/config/GlassStyleConfigTest.java`

**Interfaces:**
- Produces: `GlassStyleConfig.read(ConfigReader)`, `GlassStyleConfig.resolved(GlassMaterialProfile)`, global sampling extras, and `GlassParameter` metadata.
- Existing boolean-only `ConfigReader` construction remains source-compatible for bootstrap tests.

- [ ] **Step 1: Write RED config tests** proving current Phase 0 defaults, numeric clamp behavior, disabled-profile inheritance, enabled-profile independence, and four global sampling extras.
- [ ] **Step 2: Run** `./scripts/test-contracts.sh` and require the new tests to fail because the types do not exist.
- [ ] **Step 3: Implement typed keys/readers and immutable glass config.** Boolean source compatibility remains; production gains numeric reads. Store normalized optical values as floats in `GlassStyleConfig` even if SharedPreferences keys are integer-backed.
- [ ] **Step 4: Run** `./scripts/test-contracts.sh` and `gradle testDebugUnitTest --tests '*GlassStyleConfigTest'` and require PASS.
- [ ] **Step 5: Commit** `feat: add typed SystemUI glass style configuration`.

### Task 2: Prismal mapping and mutable profile registry

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/glass/core/GlassPrismalAdapter.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistry.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/GlassPrismalAdapterTest.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/MaterialProfileRegistryTest.java`

**Interfaces:**
- Consumes: `GlassStyleConfig.ResolvedStyle`.
- Produces: `GlassPrismalAdapter.toPrismal(style, density)` and `MaterialProfileRegistry.update(config, version)`.

- [ ] **Step 1: Write RED mapping tests** locking every active `PrismalParams` field and exact Phase 0 Global defaults.
- [ ] **Step 2: Run targeted tests** and verify failure before implementation.
- [ ] **Step 3: Implement the adapter** using LiquidDock active parameter semantics: density only for pixel/dp distance fields; percentage-like values normalized before the adapter.
- [ ] **Step 4: Make `MaterialProfileRegistry` updateable** with an `EnumMap` replacement and monotonic external profile version. No GPU types enter this class.
- [ ] **Step 5: Run targeted + fast contracts** and commit `feat: add updateable glass material profiles`.

### Task 3: Runtime hot update through the shared Window core

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/config/GlassConfigRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquidui/ModuleMain.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRegistry.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquidui/glass/core/WindowGlassRenderer.java`
- Test: `src/test/java/com/hellovoid/liquidui/glass/core/GlassStyleHotUpdateContractTest.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/GlassCustomizationArchitectureTest.java`

**Interfaces:**
- Produces: `SystemUiGlassCore.updateGlassStyles(GlassStyleConfig)`, propagation to live sessions, and scene-only renderer refresh.

- [ ] **Step 1: Write RED tests** requiring one preference listener, monotonic style versions, last-write-wins propagation, latest-style initialization for new sessions, and no producer/EGL/recovery calls in the style-update path.
- [ ] **Step 2: Run fast contracts** and verify only the new requirements fail.
- [ ] **Step 3: Implement `GlassConfigRuntime`** to filter glass keys and coalesce callbacks before publishing the latest complete snapshot.
- [ ] **Step 4: Implement core/session/renderer propagation.** Renderer updates registry + global sampling extras and requests a scene redraw; it never invokes PassBlur bind/unbind or producer recovery for optical-only changes.
- [ ] **Step 5: Preserve activation correctness:** the successful swap token carries the new `profileVersion`; old style versions cannot replace newer versions.
- [ ] **Step 6: Run** `./scripts/test-contracts.sh && gradle testDebugUnitTest assembleDebug` and commit `feat: hot update shared SystemUI glass styles`.

### Task 4: Schema-driven remote preference mirroring and complete GUI

**Files:**
- Modify: `src/main/java/com/hellovoid/liquidui/LiquidUiApp.java`
- Modify: `src/main/kotlin/com/hellovoid/liquidui/SettingsActivity.kt`
- Test: `src/test/java/com/hellovoid/liquidui/config/GlassSettingsArchitectureTest.java`

**Interfaces:**
- Consumes: `ConfigSchema.all()`, `GlassParameter`, profile key helpers.
- Produces: complete Global page, profile selector, five override pages, reset operations, and immediate SharedPreferences writes.

- [ ] **Step 1: Write RED source contracts** requiring schema-driven BOOLEAN/INT synchronization, Miuix `SliderPreference`, five profile override switches, Global/Profile pages, sampling controls only on Global, and reset controls.
- [ ] **Step 2: Run fast contracts** and verify RED.
- [ ] **Step 3: Refactor `LiquidUiApp`** to reconcile/sync every schema key by kind instead of three hard-coded booleans.
- [ ] **Step 4: Implement GUI navigation and grouped slider cards** using Miuix Compose. `onValueChange` writes immediately. Profile controls are disabled/hidden until override is enabled.
- [ ] **Step 5: Implement Global/Profile reset** with one SharedPreferences editor transaction per reset.
- [ ] **Step 6: Run contracts + Gradle** and commit `feat: add complete SystemUI glass customization UI`.

### Task 5: Final ownership verification and device build

**Files:**
- Modify: `README.md`
- Create: `docs/reverse-engineering/systemui-001/systemui-glass-customization.md`
- Create: `docs/superpowers/plans/2026-09-07-systemui-glass-customization-runtime-checklist.md`
- Modify: `src/test/java/com/hellovoid/liquidui/architecture/SystemUiGlassCoreArchitectureTest.java` only if exact new filenames must be added to the ownership scan.

**Interfaces:**
- Produces: verified APK and device-validation checklist.

- [ ] **Step 1: Run** `./scripts/test-contracts.sh` and require zero failures.
- [ ] **Step 2: Run** `gradle testDebugUnitTest assembleDebug --stacktrace` and require exit 0.
- [ ] **Step 3: Verify source invariants:** one core, one render thread, no notification GPU owner, no CPU capture, no style-update producer recreation.
- [ ] **Step 4: Record APK SHA-256** from the CI artifact/build and document live-preview validation: rapid drag, Global reset, CARD override, orientation, row recycle, source loss/recovery, SystemUI restart persistence.
- [ ] **Step 5: Commit** `docs: define glass customization runtime validation`.
