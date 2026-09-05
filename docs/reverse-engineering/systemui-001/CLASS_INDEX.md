# systemui-001 class index

This index records only classes actually inspected for LiquidUI. It is intentionally not a dump of the vendor source tree.

| Class | DEX | Evidence / role | LiquidUI dependency |
| --- | --- | --- | --- |
| `com.android.systemui.SystemUIApplication` | `classes.dex` | SystemUI `Application`; targeted JADX 1.5.6 decompilation completed | Exact bootstrap structural probe: declared `onCreate()` |

## SystemUIApplication

Verified declaration-level contract:

```text
com.android.systemui.SystemUIApplication
  extends android.app.Application
  declared method: onCreate(): void
```

Observed startup responsibilities include dependency injection, boot-complete receiver setup, configuration initialization, secondary-user handling, and publication of application context. LiquidUI currently depends only on the declaration of `onCreate()` for target fingerprinting; it does not hook or alter this method in the bootstrap milestone.

## Expansion policy

Add a class here only when a concrete feature investigation has read its decompiled implementation or bytecode context. For each actual feature, add a separate `<feature>-contract.md` containing the authoritative call path, fields/methods relied upon, failure semantics, structural probes, and validation evidence.
