# NotificationShade native PassBlur source experiment

Base: `feature/notification-shared-passblur@300f4a64e16ff8bd15c4a4985ce7fe7373a39236`.

## Device evidence

The exact HyperOS build constructs a native `PassBlur` object for `NotificationShade`, enables
`setUpdateTextureFlag`, and RenderEngine reports current foreground task layers while that native
PassBlur is active. The vendor `NotificationPanelView#setPassWindowBlurEnabled(boolean)` remains
the only enable/disable authority used by LiquidUI.

## Experiment

Remove LiquidUI's `RootTaskDisplayAreaOrganizer` source selector. While vendor `notifPassBlur` is
true, attach the caller-owned producer to the live `NotificationShade` ViewRoot `SurfaceControl`
itself with `SetPassBlurSurface(root, producer)` and `setUpdateTextureFlag(root, true, 1.0f)`.

No `setMiBlurWinExc` exclusions are installed and the vendor pass-blur boolean is never rewritten.
The experiment retains both endpoint generation and ViewRoot/surface-sequence identity barriers.

This is intentionally a device experiment, not a merge-ready authority claim: assigning a producer
on the same root may replace HyperOS's current native producer endpoint. The experiment branch must
therefore be validated for NotificationShade imagery plus lockscreen clock/Dynamic Island blur
regressions before any change is carried back to PR #3.

## Falsification

- If unlocked Home/App content appears correctly, the prior wallpaper result was caused by the
  source/exclusion policy rather than an inherent NotificationShade-root limitation.
- If the root still returns lockscreen wallpaper, stop using this endpoint and instrument the
  framework-owned `PassBlur.addTextureView`/SurfaceTexture path instead.
- If native SystemUI blur consumers regress, abandon root endpoint replacement even if imagery is
  correct and proceed only with a non-owning framework PassBlur consumer probe.
