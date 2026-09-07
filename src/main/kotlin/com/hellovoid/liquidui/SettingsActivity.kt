package com.hellovoid.liquidui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.hellovoid.liquidui.config.ConfigKey
import com.hellovoid.liquidui.config.ConfigSchema
import com.hellovoid.liquidui.config.GlassHighlight
import com.hellovoid.liquidui.config.GlassParameter
import com.hellovoid.liquidui.glass.core.GlassMaterialProfile
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class GlassSettingsPage(val title: String) {
    HOME("LiquidUI"),
    GLOBAL("GLOBAL"),
    CARD("CARD"),
    TILE("TILE"),
    SLIDER("SLIDER"),
    PANEL("PANEL"),
    FLOATING("FLOATING"),
    SAMPLING("采样安全区"),
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = controller) { LiquidUiSettings(this) }
        }
    }
}

@Composable
private fun LiquidUiSettings(activity: SettingsActivity) {
    val prefs = remember(activity) { PreferenceManager.getDefaultSharedPreferences(activity) }
    var page by remember { mutableStateOf(GlassSettingsPage.HOME) }
    var revision by remember { mutableStateOf(0) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    BackHandler(enabled = page != GlassSettingsPage.HOME) { page = GlassSettingsPage.HOME }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = page.title,
                navigationIcon = {
                    if (page != GlassSettingsPage.HOME) {
                        TextButton(text = "返回", onClick = { page = GlassSettingsPage.HOME })
                    }
                },
            )
        },
    ) { padding ->
        when (page) {
            GlassSettingsPage.HOME -> HomePage(prefs, revision, padding = padding) { page = it }
            GlassSettingsPage.GLOBAL -> GlobalPage(prefs, revision, padding)
            GlassSettingsPage.SAMPLING -> SamplingPage(prefs, revision, padding)
            GlassSettingsPage.CARD -> ProfilePage(prefs, revision, GlassMaterialProfile.CARD, padding)
            GlassSettingsPage.TILE -> ProfilePage(prefs, revision, GlassMaterialProfile.TILE, padding)
            GlassSettingsPage.SLIDER -> ProfilePage(prefs, revision, GlassMaterialProfile.SLIDER, padding)
            GlassSettingsPage.PANEL -> ProfilePage(prefs, revision, GlassMaterialProfile.PANEL, padding)
            GlassSettingsPage.FLOATING -> ProfilePage(prefs, revision, GlassMaterialProfile.FLOATING, padding)
        }
    }
}

@Composable
private fun HomePage(
    prefs: SharedPreferences,
    revision: Int,
    padding: androidx.compose.foundation.layout.PaddingValues,
    open: (GlassSettingsPage) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text("SystemUI Liquid Glass", fontSize = 22.sp)
                Text(
                    "systemui-001 · 16.03.251211.r · SDK 36",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    "全局默认 + 五种语义 Profile 独立覆盖；参数写入后实时推送到共享 Window renderer。",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item { SmallTitle("模块") }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.ENABLED, "启用 LiquidUI",
                    "仅在 systemui-001 精确目标验证通过后安装功能 Hook", revision,
                )
                BooleanSetting(
                    prefs, ConfigSchema.NOTIFICATION_GLASS_ENABLED, "通知液态玻璃",
                    "使用共享 PassBlur → Prismal 管线渲染通知背景", revision,
                )
                BooleanSetting(
                    prefs, ConfigSchema.DIAGNOSTICS_ENABLED, "诊断日志",
                    "输出 target、producer 与 renderer 的详细诊断", revision,
                )
            }
        }

        item { SmallTitle("玻璃材质") }
        item {
            SettingsCard {
                ArrowPreference(
                    "GLOBAL",
                    summary = "所有未启用独立覆盖的组件继承这里的完整 Prismal 参数",
                    onClick = { open(GlassSettingsPage.GLOBAL) },
                )
                GlassMaterialProfile.values().forEach { profile ->
                    val overrideKey = ConfigSchema.profileOverrideKey(profile)
                    val overrideEnabled = prefs.getBoolean(
                        overrideKey.name(), overrideKey.defaultValue(),
                    )
                    ArrowPreference(
                        profile.name,
                        summary = if (overrideEnabled) "独立参数覆盖已启用" else "继承 GLOBAL",
                        onClick = { open(pageFor(profile)) },
                    )
                }
                ArrowPreference(
                    "采样安全区",
                    summary = "全 Window 唯一的自动 overscan 与上下左右额外安全区",
                    onClick = { open(GlassSettingsPage.SAMPLING) },
                )
            }
        }
    }
}

@Composable
private fun GlobalPage(
    prefs: SharedPreferences,
    revision: Int,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("GLOBAL", "默认保持 Phase 0 已验证视觉；所有滑块修改都会实时推送 SystemUI。") }
        GlassParameter.Category.values().forEach { category ->
            item { SmallTitle(categoryTitle(category)) }
            item {
                SettingsCard {
                    GlassParameter.values()
                        .filter { it.category() == category }
                        .forEach { parameter ->
                            IntSetting(
                                prefs = prefs,
                                config = ConfigSchema.globalGlassKey(parameter),
                                parameter = parameter,
                                enabled = true,
                                revision = revision,
                            )
                        }
                }
            }
        }
        item { SmallTitle("Prismal Highlights") }
        item {
            SettingsCard {
                GlassHighlight.values().forEach { highlight ->
                    BooleanSetting(
                        prefs,
                        ConfigSchema.globalHighlightKey(highlight),
                        highlightTitle(highlight),
                        highlightSummary(highlight),
                        revision,
                    )
                }
            }
        }
        item {
            SettingsCard {
                ArrowPreference(
                    "重置 GLOBAL",
                    summary = "恢复 Phase 0 兼容默认光学参数与全部关闭的 highlight gates",
                    onClick = { resetGlobal(prefs) },
                )
            }
        }
    }
}

@Composable
private fun ProfilePage(
    prefs: SharedPreferences,
    revision: Int,
    profile: GlassMaterialProfile,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    val overrideKey = ConfigSchema.profileOverrideKey(profile)
    val overrideEnabled = prefs.getBoolean(overrideKey.name(), overrideKey.defaultValue())

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                profile.name,
                if (overrideEnabled) "该 Profile 使用完整独立参数。" else "当前继承 GLOBAL；开启覆盖后可独立调节。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    overrideKey,
                    "启用 ${profile.name} 独立覆盖",
                    "关闭时不读取下方独立值，完整继承 GLOBAL",
                    revision,
                )
            }
        }
        GlassParameter.Category.values().forEach { category ->
            item { SmallTitle(categoryTitle(category)) }
            item {
                SettingsCard {
                    GlassParameter.values()
                        .filter { it.category() == category }
                        .forEach { parameter ->
                            IntSetting(
                                prefs = prefs,
                                config = ConfigSchema.profileGlassKey(profile, parameter),
                                parameter = parameter,
                                enabled = overrideEnabled,
                                revision = revision,
                            )
                        }
                }
            }
        }
        item { SmallTitle("Prismal Highlights") }
        item {
            SettingsCard {
                GlassHighlight.values().forEach { highlight ->
                    BooleanSetting(
                        prefs,
                        ConfigSchema.profileHighlightKey(profile, highlight),
                        highlightTitle(highlight),
                        highlightSummary(highlight),
                        revision,
                        enabled = overrideEnabled,
                    )
                }
            }
        }
        item {
            SettingsCard {
                ArrowPreference(
                    "重置 ${profile.name}",
                    summary = "恢复该 Profile 默认值并关闭独立覆盖，重新继承 GLOBAL",
                    onClick = { resetProfile(prefs, profile) },
                )
            }
        }
    }
}

@Composable
private fun SamplingPage(
    prefs: SharedPreferences,
    revision: Int,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    val auto = prefs.getBoolean(
        ConfigSchema.SAMPLING_AUTO_ENABLED.name(),
        ConfigSchema.SAMPLING_AUTO_ENABLED.defaultValue(),
    )
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            PageHeader(
                "采样安全区",
                "采样属于 Window/producer 全局 authority，不按 CARD/TILE 等 Profile 分裂。默认关闭自动 overscan 以保持 Phase 0。",
            )
        }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs,
                    ConfigSchema.SAMPLING_AUTO_ENABLED,
                    "自动光学安全区",
                    "开启后由 Prismal 计算 guard；四个额外值在自动结果上增减",
                    revision,
                )
                SamplingIntSetting(prefs, ConfigSchema.SAMPLING_EXTRA_TOP, "顶部额外采样", auto, revision)
                SamplingIntSetting(prefs, ConfigSchema.SAMPLING_EXTRA_BOTTOM, "底部额外采样", auto, revision)
                SamplingIntSetting(prefs, ConfigSchema.SAMPLING_EXTRA_LEFT, "左侧额外采样", auto, revision)
                SamplingIntSetting(prefs, ConfigSchema.SAMPLING_EXTRA_RIGHT, "右侧额外采样", auto, revision)
                ArrowPreference(
                    "重置采样",
                    summary = "关闭自动安全区并将四边额外值恢复为 0",
                    onClick = { resetSampling(prefs) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
}

@Composable
private fun PageHeader(title: String, summary: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, fontSize = 22.sp)
        Text(summary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun BooleanSetting(
    prefs: SharedPreferences,
    config: ConfigKey<Boolean>,
    title: String,
    summary: String,
    revision: Int,
    enabled: Boolean = true,
) {
    val key = config.name()
    var value by remember(key, revision) {
        mutableStateOf(prefs.getBoolean(key, config.defaultValue()))
    }
    SwitchPreference(
        checked = value,
        onCheckedChange = {
            value = it
            prefs.edit().putBoolean(key, it).apply()
        },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
private fun IntSetting(
    prefs: SharedPreferences,
    config: ConfigKey<Int>,
    parameter: GlassParameter,
    enabled: Boolean,
    revision: Int,
) {
    val key = config.name()
    val min = requireNotNull(config.minInt())
    val max = requireNotNull(config.maxInt())
    var value by remember(key, revision) {
        mutableStateOf(prefs.getInt(key, config.defaultValue()).toFloat())
    }
    val raw = value.roundToInt().coerceIn(min, max)
    SliderPreference(
        value = value,
        onValueChange = { next ->
            val nextRaw = next.roundToInt().coerceIn(min, max)
            value = nextRaw.toFloat()
            prefs.edit().putInt(key, nextRaw).apply()
        },
        title = parameterTitle(parameter),
        summary = parameterSummary(parameter),
        valueText = formatParameterValue(parameter, raw),
        enabled = enabled,
        valueRange = min.toFloat()..max.toFloat(),
        steps = 0,
    )
}

@Composable
private fun SamplingIntSetting(
    prefs: SharedPreferences,
    config: ConfigKey<Int>,
    title: String,
    enabled: Boolean,
    revision: Int,
) {
    val key = config.name()
    val min = requireNotNull(config.minInt())
    val max = requireNotNull(config.maxInt())
    var value by remember(key, revision) {
        mutableStateOf(prefs.getInt(key, config.defaultValue()).toFloat())
    }
    SliderPreference(
        value = value,
        onValueChange = { next ->
            val nextRaw = next.roundToInt().coerceIn(min, max)
            value = nextRaw.toFloat()
            prefs.edit().putInt(key, nextRaw).apply()
        },
        title = title,
        summary = "最终安全区 = 自动安全区 + 此值；单位 px，可为负值",
        valueText = "${value.roundToInt()} px",
        enabled = enabled,
        valueRange = min.toFloat()..max.toFloat(),
        steps = 0,
    )
}

private fun resetGlobal(prefs: SharedPreferences) {
    val editor = prefs.edit()
    GlassParameter.values().forEach { parameter ->
        val key = ConfigSchema.globalGlassKey(parameter)
        editor.putInt(key.name(), key.defaultValue())
    }
    GlassHighlight.values().forEach { highlight ->
        val key = ConfigSchema.globalHighlightKey(highlight)
        editor.putBoolean(key.name(), key.defaultValue())
    }
    editor.apply()
}

private fun resetProfile(prefs: SharedPreferences, profile: GlassMaterialProfile) {
    val editor = prefs.edit()
    val overrideKey = ConfigSchema.profileOverrideKey(profile)
    editor.putBoolean(overrideKey.name(), false)
    GlassParameter.values().forEach { parameter ->
        val key = ConfigSchema.profileGlassKey(profile, parameter)
        editor.putInt(key.name(), key.defaultValue())
    }
    GlassHighlight.values().forEach { highlight ->
        val key = ConfigSchema.profileHighlightKey(profile, highlight)
        editor.putBoolean(key.name(), key.defaultValue())
    }
    editor.apply()
}

private fun resetSampling(prefs: SharedPreferences) {
    prefs.edit()
        .putBoolean(ConfigSchema.SAMPLING_AUTO_ENABLED.name(), false)
        .putInt(ConfigSchema.SAMPLING_EXTRA_TOP.name(), 0)
        .putInt(ConfigSchema.SAMPLING_EXTRA_BOTTOM.name(), 0)
        .putInt(ConfigSchema.SAMPLING_EXTRA_LEFT.name(), 0)
        .putInt(ConfigSchema.SAMPLING_EXTRA_RIGHT.name(), 0)
        .apply()
}

private fun pageFor(profile: GlassMaterialProfile): GlassSettingsPage = when (profile) {
    GlassMaterialProfile.CARD -> GlassSettingsPage.CARD
    GlassMaterialProfile.TILE -> GlassSettingsPage.TILE
    GlassMaterialProfile.SLIDER -> GlassSettingsPage.SLIDER
    GlassMaterialProfile.PANEL -> GlassSettingsPage.PANEL
    GlassMaterialProfile.FLOATING -> GlassSettingsPage.FLOATING
}

private fun categoryTitle(category: GlassParameter.Category): String = when (category) {
    GlassParameter.Category.BASIC -> "基础光学"
    GlassParameter.Category.REFRACTION -> "折射与色散"
    GlassParameter.Category.COLOR -> "颜色与透射"
    GlassParameter.Category.LIGHTING -> "光照与阴影"
    GlassParameter.Category.DEBUG -> "调试"
}

private fun parameterTitle(parameter: GlassParameter): String = when (parameter) {
    GlassParameter.BLUR -> "Blur"
    GlassParameter.THICKNESS -> "Thickness"
    GlassParameter.IOR -> "IOR 折射率"
    GlassParameter.NORMAL_STRENGTH -> "Normal Strength"
    GlassParameter.DOME -> "Dome"
    GlassParameter.LENS_REFRACTION -> "Lens Refraction"
    GlassParameter.LENS_DEPTH -> "Lens Depth"
    GlassParameter.CHROMATIC -> "Chromatic"
    GlassParameter.REFRACTION_INSET -> "Refraction Inset"
    GlassParameter.DISPLACEMENT_SCALE -> "Displacement Scale"
    GlassParameter.HEIGHT_TRANSITION_WIDTH -> "Height Transition Width"
    GlassParameter.SMIN_SMOOTHING -> "SMin Smoothing"
    GlassParameter.EDGE_REFRACTION_FALLOFF -> "Edge Refraction Falloff"
    GlassParameter.FRESNEL_REFLECT -> "Fresnel Reflect"
    GlassParameter.DISPERSION_R -> "Dispersion R"
    GlassParameter.DISPERSION_B -> "Dispersion B"
    GlassParameter.BACKDROP_SCALE_X -> "Backdrop Scale X"
    GlassParameter.BACKDROP_SCALE_Y -> "Backdrop Scale Y"
    GlassParameter.PARALLAX_SCALE -> "Parallax Scale"
    GlassParameter.VIBRANCY -> "Vibrancy"
    GlassParameter.BRIGHTNESS -> "Brightness"
    GlassParameter.TINT_R -> "Tint R"
    GlassParameter.TINT_G -> "Tint G"
    GlassParameter.TINT_B -> "Tint B"
    GlassParameter.TINT_ALPHA -> "Tint Alpha"
    GlassParameter.TRANSMITTANCE -> "Transmittance"
    GlassParameter.HIGHLIGHT_WIDTH -> "Highlight Width"
    GlassParameter.PLAIN_HIGHLIGHT -> "Plain Highlight Strength"
    GlassParameter.LIGHT_DIR_X -> "Light Direction X"
    GlassParameter.LIGHT_DIR_Y -> "Light Direction Y"
    GlassParameter.SPECULAR_STRENGTH -> "Specular Strength"
    GlassParameter.SPECULAR_SHARPNESS -> "Specular Sharpness"
    GlassParameter.RIM_LIGHT -> "Rim Light"
    GlassParameter.CAUSTICS -> "Caustics"
    GlassParameter.SHADOW_R -> "Shadow R"
    GlassParameter.SHADOW_G -> "Shadow G"
    GlassParameter.SHADOW_B -> "Shadow B"
    GlassParameter.SHADOW_ALPHA -> "Shadow Alpha"
    GlassParameter.SHADOW_SOFTNESS -> "Shadow Softness"
    GlassParameter.SHOW_NORMALS -> "Show Normals"
}

private fun parameterSummary(parameter: GlassParameter): String = when (parameter.category()) {
    GlassParameter.Category.BASIC -> "Prismal 基础材质参数"
    GlassParameter.Category.REFRACTION -> "控制折射位移、边缘衰减、色散和视差"
    GlassParameter.Category.COLOR -> "控制背景颜色、亮度、鲜艳度与透射"
    GlassParameter.Category.LIGHTING -> "控制高光、光向、镜面、边缘光和内阴影"
    GlassParameter.Category.DEBUG -> "仅用于调试材质法线输出"
}

private fun formatParameterValue(parameter: GlassParameter, raw: Int): String {
    val normalized = parameter.normalize(raw)
    val value = when {
        parameter.scale() == 1f -> raw.toString()
        parameter.scale() == 10f -> String.format(java.util.Locale.ROOT, "%.1f", normalized)
        else -> String.format(java.util.Locale.ROOT, "%.2f", normalized)
    }
    return if (parameter.unit().isBlank()) value else "$value ${parameter.unit()}"
}

private fun highlightTitle(highlight: GlassHighlight): String = when (highlight) {
    GlassHighlight.SKY_HAZE -> "Sky Haze"
    GlassHighlight.SPECULAR -> "Specular"
    GlassHighlight.RIM_LIT -> "Lit Rim"
    GlassHighlight.OPPOSITE_RIM -> "Opposite Rim"
    GlassHighlight.CORNER_RIM -> "Corner Rim"
    GlassHighlight.FACE_SHEEN -> "Face Sheen"
    GlassHighlight.PLAIN_HIGHLIGHT -> "Plain Highlight"
    GlassHighlight.CAUSTICS -> "Caustics"
    GlassHighlight.PRESS_GLOW -> "Press Glow"
}

private fun highlightSummary(highlight: GlassHighlight): String =
    "Prismal highlight gate · ${highlight.suffix()}；默认关闭以保持 Phase 0 视觉"
