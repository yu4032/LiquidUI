package com.hellovoid.liquidui.target.profiles;

import com.hellovoid.liquidui.target.RequiredMethodProbe;
import com.hellovoid.liquidui.target.StructuralProbe;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.util.List;

public final class SystemUi001Profile implements SystemUiTargetProfile {
    public static final SystemUi001Profile INSTANCE = new SystemUi001Profile();

    private static final StructuralProbe SYSTEM_UI_APPLICATION_ON_CREATE =
            new RequiredMethodProbe(
                    "com.android.systemui.SystemUIApplication",
                    "onCreate",
                    new Class<?>[0]);

    private SystemUi001Profile() {}

    @Override public String id() { return "systemui-001"; }
    @Override public String packageName() { return "com.android.systemui"; }
    @Override public long versionCode() { return 202501210L; }
    @Override public String versionName() { return "16.03.251211.r"; }
    @Override public int sdkInt() { return 36; }
    @Override public List<StructuralProbe> structuralProbes() { return List.of(SYSTEM_UI_APPLICATION_ON_CREATE); }
}
