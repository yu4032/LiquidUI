package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.GlassStyleConfig;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Core-owned mapping from semantic component material to versioned Prismal presentation state. */
final class MaterialProfileRegistry {
    static final long INITIAL_PROFILE_VERSION = 1L;

    record MaterialState(PrismalParams params, PrismalHighlightProfile highlights) {
        MaterialState {
            Objects.requireNonNull(params, "params");
            Objects.requireNonNull(highlights, "highlights");
        }
    }

    private final float density;
    private final Map<GlassMaterialProfile, MaterialState> profiles =
            new EnumMap<>(GlassMaterialProfile.class);
    private long profileVersion = INITIAL_PROFILE_VERSION;

    MaterialProfileRegistry(float density) {
        this.density = Math.max(0.1f, density);
        replaceProfiles(GlassStyleConfig.defaults());
    }

    synchronized MaterialState stateFor(GlassMaterialProfile profile) {
        MaterialState state = profiles.get(profile);
        if (state != null) return state;
        state = profiles.get(GlassMaterialProfile.CARD);
        if (state == null) throw new IllegalStateException("glass material registry empty");
        return state;
    }

    synchronized PrismalParams paramsFor(GlassMaterialProfile profile) {
        return stateFor(profile).params();
    }

    synchronized PrismalHighlightProfile highlightsFor(GlassMaterialProfile profile) {
        return stateFor(profile).highlights();
    }

    synchronized long profileVersion() {
        return profileVersion;
    }

    /**
     * Atomically replace every semantic profile. Stale or duplicate versions are ignored so a
     * delayed preference callback can never overwrite a newer frame's material state.
     */
    synchronized boolean update(GlassStyleConfig styleConfig, long nextVersion) {
        Objects.requireNonNull(styleConfig, "styleConfig");
        if (nextVersion <= profileVersion) return false;
        replaceProfiles(styleConfig);
        profileVersion = nextVersion;
        return true;
    }

    private void replaceProfiles(GlassStyleConfig styleConfig) {
        Map<GlassMaterialProfile, MaterialState> next =
                new EnumMap<>(GlassMaterialProfile.class);
        for (GlassMaterialProfile profile : GlassMaterialProfile.values()) {
            GlassStyleConfig.ResolvedStyle style = styleConfig.resolved(profile);
            next.put(profile, new MaterialState(
                    GlassPrismalAdapter.toPrismal(style, density),
                    GlassPrismalAdapter.toHighlights(style)));
        }
        profiles.clear();
        profiles.putAll(next);
    }
}
