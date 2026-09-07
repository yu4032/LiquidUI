package com.hellovoid.liquidui.glass.systemui;

/** Android-free boundary for exact native material suppression/restoration. */
public interface NativeMaterialController<T> {
    void suppress(T host, long lifecycleGeneration);
    void restore(T host, long lifecycleGeneration);
    void restoreAll();
}
