package com.hellovoid.liquidui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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

    Scaffold(
        topBar = { SmallTopAppBar(title = "LiquidUI") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text("SystemUI exact-target hooks", fontSize = 22.sp)
                    Text(
                        "systemui-001 · 16.03.251211.r · SDK 36",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    BooleanSetting(
                        prefs = prefs,
                        config = ConfigSchema.ENABLED,
                        title = "启用 LiquidUI",
                        summary = "仅在 systemui-001 精确目标验证通过后安装功能 Hook",
                    )
                    BooleanSetting(
                        prefs = prefs,
                        config = ConfigSchema.NOTIFICATION_GLASS_ENABLED,
                        title = "通知液态玻璃",
                        summary = "使用共享 PassBlur → Prismal 管线渲染所有可见通知背景",
                    )
                    BooleanSetting(
                        prefs = prefs,
                        config = ConfigSchema.DIAGNOSTICS_ENABLED,
                        title = "诊断日志",
                        summary = "保留更详细的 target 与 Hook 安装结果",
                    )
                }
            }
        }
    }
}

@Composable
private fun BooleanSetting(
    prefs: SharedPreferences,
    config: ConfigKey<Boolean>,
    title: String,
    summary: String,
) {
    val key = config.name()
    var value by remember(key) { mutableStateOf(prefs.getBoolean(key, config.defaultValue())) }
    SwitchPreference(
        checked = value,
        onCheckedChange = {
            value = it
            prefs.edit().putBoolean(key, it).apply()
        },
        title = title,
        summary = summary,
    )
}
