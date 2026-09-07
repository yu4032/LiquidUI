package com.hellovoid.liquidui.glass.core;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Weak identity for one ViewRoot-like owner on one display. */
public final class WindowKey {
    private final WeakReference<Object> root;
    private final int rootIdentityHash;
    private final int displayId;

    public WindowKey(Object root, int displayId) {
        Object requiredRoot = Objects.requireNonNull(root, "root");
        this.root = new WeakReference<>(requiredRoot);
        this.rootIdentityHash = System.identityHashCode(requiredRoot);
        this.displayId = displayId;
    }

    public Object root() {
        return root.get();
    }

    public int displayId() {
        return displayId;
    }

    public boolean isCollected() {
        return root.get() == null;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof WindowKey other) || displayId != other.displayId) return false;
        Object leftRoot = root.get();
        Object rightRoot = other.root.get();
        return leftRoot != null && leftRoot == rightRoot;
    }

    @Override
    public int hashCode() {
        return 31 * rootIdentityHash + displayId;
    }
}
