# LiquidUI Bootstrap Design

## Status

Approved architectural design for the initial LiquidUI bootstrap. This document defines the exact-version-first baseline before implementation.

## Goal

Build `yu4032/LiquidUI` as a libxposed API 101 module targeting one verified HyperOS SystemUI build first. LiquidUI will reverse-engineer the supplied `MiuiSystemUI.apk` and implement SystemUI hooks from proven runtime/decompiled contracts instead of guessing vendor classes or broadening compatibility prematurely.

## Exact target profile

Initial target profile `systemui-001`:

- Package: `com.android.systemui`
- Version code: `202501210`
- Version name: `16.03.251211.r`
- minSdk / targetSdk: `36`
- compileSdk: `36`
- Application: `com.android.systemui.SystemUIApplication`
- APK size: `52,843,421` bytes
- APK SHA-256: `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`
- DEX count: `3`
- Reverse-engineering tool baseline: JADX `1.5.6`

The APK hash is the provenance identity for the analysis artifact. Runtime target gating must not hash the installed 52 MB APK on every SystemUI start; it should use package/version/SDK metadata plus structural probes for the classes and methods needed by enabled hooks.

## Scope

### In scope for bootstrap

- New Android/libxposed API 101 application namespace `com.hellovoid.liquidui`.
- Primary injected process: `com.android.systemui` only.
- Exact target-profile gate.
- Shared API 101 logging, Remote Preferences, configuration schema/reader, hook utilities and settings UI patterns selectively extracted from LiquidDock.
- SystemUI-specific hook registry with explicit install outcomes.
- Cached reverse-engineering workflow for the supplied SystemUI APK and JADX 1.5.6.
- Reverse-engineering documentation recording proven class/method/data-flow contracts without committing vendor APKs or full decompiled source.
- Unit/architecture tests for target gating, hook registration and classloader rules.
- CI build/test path derived from LiquidDock where generic.

### Explicitly out of scope

- LiquidDock Launcher, Dock, Grid, Workstation, Prismal or Launcher glass business logic.
- Injection into `com.miui.home` in the bootstrap release.
- Generic HyperOS support or class-name fallback chains.
- Support for other SystemUI versions before `systemui-001` is proven.
- Bundling `MiuiSystemUI.apk`, JADX distribution, or full vendor decompilation output in the public repository.
- Fixed-delay lifecycle workarounds where a real SystemUI lifecycle/content authority can be identified.

## Architecture

```text
LiquidUI app / Settings
        |
        +-- config/
        |   +-- ConfigSchema
        |   +-- ConfigReader
        |   +-- Remote Preferences
        |
        +-- libxposed API 101
                |
                v
           ModuleMain
                |
        SystemUiTargetResolver
                |
                v
        SystemUiTargetProfile(systemui-001)
        +-- package/version/sdk gate
        +-- structural probes
                |
                v
        SystemUiHookRegistry
        +-- keyguard/
        +-- statusbar/
        +-- notification/
        +-- controlcenter/
        +-- volume/
        +-- misc/
```

`ModuleMain` is a composition root only. It initializes the API 101 bridge, identifies the package/process, resolves the exact target and delegates hook installation. Feature-specific reflection, state machines and UI ownership must not accumulate in `ModuleMain`.

## Target authority

`SystemUiTargetProfile` is the sole authority for an analyzed SystemUI build. A profile contains stable metadata and structural expectations needed to prove the runtime is the same implementation family as the decompiled target.

Target resolution has three outcomes:

- `SUPPORTED`: static metadata matches and required structural probes pass.
- `UNSUPPORTED`: metadata or probes prove the runtime is not this target. No feature hooks are installed.
- `FAILED`: infrastructure prevented target resolution. Log distinctly; do not silently treat it as supported or install speculative hooks.

The bootstrap target resolver must fail closed.

## Hook installation contract

Every feature hook reports one explicit result:

- `INSTALLED`: all required classes/methods were resolved using the target process classloader and callbacks were registered.
- `UNSUPPORTED`: the exact profile does not declare or expose the required contract.
- `FAILED`: the profile says the hook should exist but resolution/installation failed unexpectedly.
- `DISABLED`: feature is intentionally disabled by configuration.

The registry aggregates results for diagnostics but must not convert partial failure into overall success.

Vendor/private SystemUI classes must be resolved with the `PackageReadyParam` target `ClassLoader` and passed as `Class<?>` to reflection/hook utilities. String-only helper paths that call `Class.forName` from the module classloader are forbidden for SystemUI-private classes. Boot/framework classes may use explicit framework-safe paths.

## Package/module boundaries

Recommended Java/Kotlin package layout:

```text
com.hellovoid.liquidui
  ModuleMain
  api/
  config/
  target/
    SystemUiTargetProfile
    SystemUiTargetResolver
    profiles/SystemUi001Profile
  hook/
    HookInstallResult
    SystemUiHook
    SystemUiHookRegistry
    keyguard/
    statusbar/
    notification/
    controlcenter/
    volume/
    misc/
  reflect/
  diagnostics/
  ui/
```

Feature directories are organizational boundaries, not permission to add empty abstractions. A category appears only when the first real decompiled hook in that category is implemented.

## LiquidDock reuse policy

Reuse only infrastructure whose semantics are independent of Launcher:

- libxposed API 101 bootstrap/bridge patterns;
- Remote Preferences integration;
- typed configuration/schema concepts;
- logging and diagnostic conventions;
- safe reflection utilities after removing Launcher assumptions;
- Compose/MIUIX settings scaffolding;
- R8/keep rules for the module entry and reflection contracts;
- generic CI/Gradle cache structure.

Do not copy:

- `MainHook` and feature composition from LiquidDock;
- Launcher glass/session/scene code;
- Dock/Grid/Widget/Folder/Workstation hooks;
- Prismal unless a future SystemUI feature independently establishes a rendering need.

If a supposedly generic LiquidDock class imports or encodes `com.miui.home` lifecycle/ownership semantics, it is not generic and must not be copied wholesale.

## Reverse-engineering workflow

The supplied `MiuiSystemUI.apk` and JADX 1.5.6 are external analysis inputs. The public repository keeps only reproducible metadata and findings.

Recommended local ignored layout:

```text
.local/reverse-engineering/
  systemui-001/
    MiuiSystemUI.apk
    jadx/
    manifest/
    fingerprints/
```

Repository documentation:

```text
docs/reverse-engineering/systemui-001/
  TARGET.md
  CLASS_INDEX.md
  <feature>-contract.md
```

Each feature contract records:

1. user-visible behavior being modified;
2. exact classes and methods observed in decompilation;
3. call/lifecycle authority;
4. fields/arguments/results relied upon;
5. runtime structural probes;
6. proposed hook point and why it is authoritative;
7. failure behavior;
8. tests/diagnostics used to validate it.

Full decompiled vendor source remains untracked. CI must never depend on possessing the proprietary APK to compile the normal module.

## Cached JADX strategy

Full decompilation is a developer analysis step, not a per-CI-build task. For one exact target APK:

- verify the source artifact hash once;
- unpack/use pinned JADX 1.5.6;
- generate a cached decompiled tree locally or in a private analysis workspace;
- use targeted search/decompilation when investigating a feature;
- record only derived contracts/fingerprints in Git.

A helper script may verify provenance and invoke JADX, but it must not download or commit proprietary inputs.

## Configuration

Bootstrap configuration should stay minimal:

- master enable;
- per-feature enable flags only for actually implemented hooks;
- diagnostics toggle if needed.

Persisted keys must be registered through one schema authority. Do not begin LiquidUI by cloning LiquidDock's entire preference surface.

## Error handling and diagnostics

SystemUI is a high-value process, so bootstrap must prefer fail-closed behavior:

- unsupported build: one clear diagnostic, no feature hooks;
- one failed feature: report `FAILED`, skip that feature, keep unrelated proven hooks isolated where safe;
- unexpected exception during bootstrap: catch at the smallest meaningful boundary and log package/profile/hook identity;
- never loop/retry indefinitely inside SystemUI startup;
- no silent-null reflection helper that makes missing vendor contracts indistinguishable from valid null results.

Diagnostic logs use a LiquidUI-specific tag/prefix rather than LiquidDock's existing `[DC]` namespace.

## Testing

Bootstrap tests must cover at minimum:

1. exact metadata match -> profile candidate;
2. version/package/SDK mismatch -> `UNSUPPORTED`;
3. required structural probe missing -> `UNSUPPORTED` or profile-contract failure, never speculative install;
4. hook registry preserves `INSTALLED/UNSUPPORTED/FAILED/DISABLED` distinctions;
5. aggregate status does not claim success for failed required hooks;
6. SystemUI private classes are resolved through the supplied target classloader;
7. architecture test prevents feature code from bypassing the target/profile registry;
8. configuration keys cannot be persisted outside the schema authority;
9. release/debug optimized builds retain libxposed entry/reflection contracts.

Feature-specific tests are added before each production hook according to TDD.

## CI

Start from LiquidDock's current generic Android/Gradle/API101 CI decisions but remove LiquidDock-specific modules and tests. CI should build and test LiquidUI without SystemUI APK/JADX inputs. Reverse-engineering provenance verification can exist as a manual/local script or optional workflow requiring user-supplied artifacts.

## Initial milestone

The bootstrap milestone is complete when:

- LiquidUI builds as an installable libxposed API 101 module;
- `ModuleMain` loads successfully;
- only `com.android.systemui` is considered for injection;
- target `systemui-001` resolves only on its exact proven runtime contract;
- unsupported builds fail closed;
- hook registry and install-result diagnostics work;
- configuration/settings shell works;
- reverse-engineering target metadata and workflow are documented;
- CI passes;
- no Launcher/Dock/Grid/Prismal business code is present.

This milestone deliberately does not require a user-visible SystemUI modification. The first actual feature is a separate bounded task driven by decompilation evidence after the bootstrap is established.

## Future expansion rule

A new SystemUI build becomes a new target profile only after its APK is independently fingerprinted and the affected feature contracts are re-audited. Compatibility is added by explicit profiles/adapters, not by broad exception swallowing or increasingly fuzzy class/method fallback chains.
