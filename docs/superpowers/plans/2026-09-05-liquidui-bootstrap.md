# LiquidUI Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first installable/libxposed-ready LiquidUI skeleton that supports only the verified `systemui-001` SystemUI target, fails closed on mismatch, and provides explicit hook-install diagnostics.

**Architecture:** Keep `ModuleMain` as a composition root. Resolve `com.android.systemui` through one exact target profile, then delegate to a registry of isolated `SystemUiHook` implementations; vendor classes are always resolved with the target process `ClassLoader`. Keep configuration minimal and schema-owned, and keep proprietary APK/JADX outputs outside Git.

**Tech Stack:** Java 17, Android application plugin 9.3.0, compileSdk 37, minSdk 33, targetSdk 37, libxposed API/service 101, JUnit 4, Compose/MIUIX settings shell.

**Spec:** `docs/superpowers/specs/2026-09-05-liquidui-bootstrap-design.md`

## Global Constraints

- Namespace/application ID: `com.hellovoid.liquidui`.
- Primary injected package: `com.android.systemui` only.
- Exact profile `systemui-001`: versionCode `202501210`, versionName `16.03.251211.r`, runtime SDK `36`.
- Analysis artifact provenance: size `52,843,421`, SHA-256 `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`, DEX count `3`, JADX `1.5.6`.
- Unsupported or failed target resolution installs no feature hooks.
- SystemUI-private classes must be resolved by the supplied target `ClassLoader`; module-classloader `Class.forName(String)` is forbidden for vendor classes.
- No Launcher/Dock/Grid/Widget/Folder/Workstation/Prismal production code.
- Full vendor APK/JADX trees remain untracked and are never required by normal CI.

---

### Task 1: Android/libxposed project shell

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `src/main/AndroidManifest.xml`
- Create: `src/main/resources/META-INF/xposed/module.prop`
- Create: `src/main/resources/META-INF/xposed/java_init.list`
- Create: `src/main/keepRules/liquidui.keep`

**Interfaces:**
- Produces Android application namespace `com.hellovoid.liquidui` and literal libxposed entry `com.hellovoid.liquidui.ModuleMain`.

- [ ] **Step 1: Add project metadata and libxposed resources** using the same API101 dependency pattern as LiquidDock but without `:prismal` or Launcher-specific resources.
- [ ] **Step 2: Add R8 keep rule** for `ModuleMain` and target/profile hook contracts.
- [ ] **Step 3: Verify static metadata** with grep-based architecture checks because this environment has no Android SDK/Gradle.
- [ ] **Step 4: Commit** with `build: bootstrap LiquidUI API101 project`.

### Task 2: Exact SystemUI target authority

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/target/TargetResolutionStatus.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/TargetResolution.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/SystemUiRuntimeInfo.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/StructuralProbe.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/SystemUiTargetProfile.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/profiles/SystemUi001Profile.java`
- Create: `src/main/java/com/hellovoid/liquidui/target/SystemUiTargetResolver.java`
- Test: `src/test/java/com/hellovoid/liquidui/target/SystemUiTargetResolverContractTest.java`

**Interfaces:**
- Produces: `TargetResolution SystemUiTargetResolver.resolve(SystemUiRuntimeInfo, ClassLoader)`.
- `TargetResolutionStatus`: `SUPPORTED`, `UNSUPPORTED`, `FAILED`.

- [ ] **Step 1: Write failing resolver contract tests** for exact match, package/version/SDK mismatch, missing structural probe, and probe infrastructure failure.
- [ ] **Step 2: Compile/run the tests to verify RED** with a local Java harness.
- [ ] **Step 3: Implement minimal immutable profile/resolver types**; mismatch returns `UNSUPPORTED`, probe exception returns `FAILED`, all probes passing returns `SUPPORTED`.
- [ ] **Step 4: Run tests to GREEN**.
- [ ] **Step 5: Commit** with `feat: add exact SystemUI target resolver`.

### Task 3: Hook installation contract and registry

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/hook/HookInstallStatus.java`
- Create: `src/main/java/com/hellovoid/liquidui/hook/HookInstallResult.java`
- Create: `src/main/java/com/hellovoid/liquidui/hook/SystemUiHook.java`
- Create: `src/main/java/com/hellovoid/liquidui/hook/HookRegistryReport.java`
- Create: `src/main/java/com/hellovoid/liquidui/hook/SystemUiHookRegistry.java`
- Test: `src/test/java/com/hellovoid/liquidui/hook/SystemUiHookRegistryContractTest.java`

**Interfaces:**
- `SystemUiHook.install(ClassLoader, SystemUiTargetProfile)` returns `HookInstallResult`.
- Statuses: `INSTALLED`, `UNSUPPORTED`, `FAILED`, `DISABLED`.
- Registry report preserves every per-hook result and exposes `hasFailures()`.

- [ ] **Step 1: Write failing registry tests** proving result distinctions and aggregate failure correctness.
- [ ] **Step 2: Run RED**.
- [ ] **Step 3: Implement minimal registry/report** with per-hook exception isolation converted to explicit `FAILED`.
- [ ] **Step 4: Run GREEN**.
- [ ] **Step 5: Commit** with `feat: define SystemUI hook install contract`.

### Task 4: Target-classloader reflection boundary

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/reflect/TargetClassResolver.java`
- Test: `src/test/java/com/hellovoid/liquidui/reflect/TargetClassResolverContractTest.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/NoVendorClassForNameArchitectureTest.java`

**Interfaces:**
- `Class<?> TargetClassResolver.require(ClassLoader targetClassLoader, String className)`.
- `Class<?> TargetClassResolver.find(ClassLoader targetClassLoader, String className)` returns null only for `ClassNotFoundException`; other linkage failures propagate.

- [ ] **Step 1: Write failing classloader tests** using a recording custom classloader.
- [ ] **Step 2: Run RED**.
- [ ] **Step 3: Implement resolver** exclusively via `ClassLoader.loadClass`.
- [ ] **Step 4: Add architecture scan** rejecting `Class.forName(` from production code outside an explicit framework-safe allowlist (initially empty).
- [ ] **Step 5: Run GREEN and commit** with `feat: enforce target classloader reflection`.

### Task 5: Minimal schema-owned configuration

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/config/ConfigKey.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/ConfigSchema.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/ConfigSource.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/ConfigReader.java`
- Create: `src/main/java/com/hellovoid/liquidui/config/LiquidUiConfig.java`
- Test: `src/test/java/com/hellovoid/liquidui/config/ConfigSchemaContractTest.java`

**Interfaces:**
- Persisted keys are schema-created only.
- Bootstrap keys: master enabled and diagnostics enabled; no speculative feature flags.

- [ ] **Step 1: Write failing tests** proving schema uniqueness/defaults and inaccessible persisted-key construction outside the config package.
- [ ] **Step 2: Run RED**.
- [ ] **Step 3: Implement package-private `ConfigKey` construction and immutable config snapshot**.
- [ ] **Step 4: Run GREEN and commit** with `feat: add minimal LiquidUI config schema`.

### Task 6: API101 bridge and ModuleMain composition root

**Files:**
- Create: `src/main/java/com/hellovoid/liquidui/Api101Bridge.java`
- Create: `src/main/java/com/hellovoid/liquidui/diagnostics/LiquidUiLog.java`
- Create: `src/main/java/com/hellovoid/liquidui/ModuleMain.java`
- Test: `src/test/java/com/hellovoid/liquidui/architecture/ModuleMainArchitectureTest.java`

**Interfaces:**
- `ModuleMain` ignores every package except `com.android.systemui`.
- Runtime metadata is collected from package/app APIs, resolved through `SystemUiTargetResolver`, and only `SUPPORTED` reaches `SystemUiHookRegistry.installAll`.

- [ ] **Step 1: Write architecture tests** proving the package constant and no feature/reflection implementation accumulates in `ModuleMain`.
- [ ] **Step 2: Implement API101 bridge/log prefix `[LUI]` and composition flow**.
- [ ] **Step 3: Ensure registry is initially empty**: bootstrap proves wiring, not a speculative visible modification.
- [ ] **Step 4: Run local contract suite and static checks**.
- [ ] **Step 5: Commit** with `feat: wire LiquidUI SystemUI bootstrap`.

### Task 7: Reverse-engineering provenance workflow

**Files:**
- Create: `scripts/reverse-engineering/verify-systemui-001.sh`
- Create: `docs/reverse-engineering/systemui-001/TARGET.md`
- Create: `docs/reverse-engineering/systemui-001/CLASS_INDEX.md`
- Create: `.local/reverse-engineering/.gitkeep` only if needed; `.local/` itself remains ignored.

**Interfaces:**
- Script takes APK path as its first argument and verifies exact byte size, SHA-256, ZIP DEX count, and expected package marker before analysis.

- [ ] **Step 1: Write shell self-test fixture checks** for wrong hash and correct supplied APK.
- [ ] **Step 2: Implement provenance verifier without downloads**.
- [ ] **Step 3: Run against `/mnt/data/liquidui-bootstrap/MiuiSystemUI.apk` and record PASS**.
- [ ] **Step 4: Document target identity and empty initial class index**.
- [ ] **Step 5: Commit** with `docs: add systemui-001 provenance workflow`.

### Task 8: CI and final architecture verification

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `scripts/test-contracts.sh`
- Create/Modify: `README.md`

**Interfaces:**
- CI runs JDK 17, Gradle unit tests, lint/build where supported, and never requires proprietary SystemUI/JADX inputs.

- [ ] **Step 1: Add CI based on LiquidDock's generic cache/build shape** but with no Launcher/Prismal jobs.
- [ ] **Step 2: Add local `scripts/test-contracts.sh`** that compiles/runs pure-Java contract tests and architecture scans in SDK-less environments.
- [ ] **Step 3: Run all locally available verification** and inspect `git diff --check`.
- [ ] **Step 4: Record Android build as environment-blocked locally, not as passed**.
- [ ] **Step 5: Commit** with `ci: add LiquidUI bootstrap verification`.
