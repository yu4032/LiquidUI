# LiquidUI

LiquidUI is an exact-version-first libxposed API 101 module for reverse-engineering and modifying HyperOS SystemUI behavior.

## Current target

Bootstrap support is intentionally limited to `systemui-001`:

- package: `com.android.systemui`
- version code: `202501210`
- version name: `16.03.251211.r`
- runtime SDK: `36`
- analysis APK SHA-256: `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28`
- JADX baseline: `1.5.6`

A different SystemUI build fails closed until it receives its own audited target profile.

## Bootstrap architecture

```text
ModuleMain
  -> framework PackageInfo authority
  -> SystemUiTargetResolver
  -> exact metadata + target-ClassLoader structural probes
  -> SystemUiHookRegistry
```

Every feature hook must return an explicit `INSTALLED`, `UNSUPPORTED`, `FAILED`, or `DISABLED` result. SystemUI-private classes are resolved through the target process ClassLoader.

The bootstrap registry is deliberately empty: the first user-visible SystemUI modification will be added only after its decompiled call path is documented and tested.

## Local contract tests

The repository includes an SDK-independent Java contract suite:

```bash
./scripts/test-contracts.sh
```

This checks target resolution, hook-result aggregation, classloader boundaries, schema ownership, and architecture invariants without requiring Android SDK or proprietary vendor files.

## Reverse engineering

Vendor APKs and full JADX output are not committed. Verify the supplied target before analysis:

```bash
./scripts/reverse-engineering/verify-systemui-001.sh /path/to/MiuiSystemUI.apk
```

Derived findings live under `docs/reverse-engineering/systemui-001/`.

## Android build

CI uses JDK 17, Gradle 9.6.1, Android Gradle Plugin 9.3.0, and libxposed API 101:

```bash
gradle testDebugUnitTest assembleDebug
```

The normal build does not require the SystemUI APK or JADX distribution.
