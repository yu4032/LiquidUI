# systemui-001 target

`systemui-001` is the first and only supported LiquidUI bootstrap target.

## Provenance

| Field | Value |
| --- | --- |
| Package | `com.android.systemui` |
| Version code | `202501210` |
| Version name | `16.03.251211.r` |
| Runtime SDK | `36` |
| Application | `com.android.systemui.SystemUIApplication` |
| APK size | `52,843,421` bytes |
| SHA-256 | `84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28` |
| DEX files | `classes.dex`, `classes2.dex`, `classes3.dex` |
| JADX baseline | `1.5.6` |

The supplied APK and JADX distribution are external analysis inputs. They are not committed to this repository.

Run the local provenance gate before analyzing the target:

```bash
scripts/reverse-engineering/verify-systemui-001.sh /path/to/MiuiSystemUI.apk
```

The verifier checks exact byte size, SHA-256, DEX count, and package/version-name markers from the binary Android manifest. It does not download inputs.

## Runtime target gate

LiquidUI does not hash the installed 52 MB APK during every SystemUI start. Runtime identity is resolved from Android's framework package-manager authority and then checked against the exact profile:

```text
AppGlobals
  -> IPackageManager.getPackageInfo(packageName, 0, userId)
  -> PackageInfo.getLongVersionCode / versionName
  -> package + version code + version name + SDK
  -> target ClassLoader structural probe
  -> systemui-001 SUPPORTED / UNSUPPORTED / FAILED
```

Any metadata-authority or structural-probe infrastructure failure is `FAILED` and installs no feature hooks.

## Bootstrap decompilation evidence

JADX 1.5.6 targeted decompilation of `com.android.systemui.SystemUIApplication` from this APK shows:

- class is loaded from `classes.dex`;
- it extends `android.app.Application`;
- it implements the SystemUI context-initializer and WM component interfaces;
- it declares a zero-argument `final void onCreate()` override;
- `onCreate()` begins with `super.onCreate()`, performs SystemUI dependency/bootstrap setup, and later publishes its application context to the static `sContext` field.

LiquidUI therefore uses `SystemUIApplication#onCreate()` as the initial structural fingerprint. The bootstrap does not use a fixed startup delay.

## Compatibility rule

A different SystemUI APK, even if its classes look similar, is unsupported until it receives a separate target profile and its affected contracts are re-audited from that APK.
