package com.hellovoid.liquidui.hook;

import com.hellovoid.liquidui.target.SystemUiTargetProfile;

public interface SystemUiHook {
    String id();
    HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) throws Throwable;
}
