package com.flowt.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.flowt.app.data.AppPrefs
import com.flowt.app.data.PrefsRepository

/** "跟随系统取色"的虚拟方案 id：不是一套固定配色，而是从壁纸取色（同时决定明暗）。 */
const val THEME_ID_SYSTEM_DYNAMIC = "system_dynamic"

/**
 * 按偏好设置解析出当前应该使用的配色方案。
 *
 * 三层优先级：
 * 1. **基底**：跟随系统取色 / 内置变体（"violet@dark"）/ 用户保存的方案（"saved:名称"）
 * 2. **保存方案自带的完整角色值** —— 它自包含，所以删掉该方案也不影响正在用的主题
 * 3. **当前未保存的临时微调** [AppPrefs.roleOverrides] 叠在最上面
 */
@Composable
fun AppPrefs.resolveColorScheme(): androidx.compose.material3.ColorScheme {
    val context = LocalContext.current

    val base: androidx.compose.material3.ColorScheme = when {
        selectedThemeId == THEME_ID_SYSTEM_DYNAMIC &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isSystemInDarkTheme()) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        selectedThemeId.startsWith(PrefsRepository.SAVED_PREFIX) -> {
            val name = selectedThemeId.removePrefix(PrefsRepository.SAVED_PREFIX)
            val saved = savedThemes.firstOrNull { it.name == name }
            // 保存的方案可能因为兼容性问题缺失个别角色，用"跟随系统取色"的基底兜底
            val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                PresetTheme.byId("violet").let { if (isSystemInDarkTheme()) it.dark else it.light }
            }
            fallback.withOverrides(saved?.overrides.orEmpty())
        }

        else -> ThemeVariant.byId(selectedThemeId)?.scheme()
            ?: PresetTheme.byId("violet").light
    }

    return base.withOverrides(roleOverrides)
}

/**
 * 应用主题。
 *
 * 与 Studio 模板版本的关键差异：**不强制动态取色**。
 * 模板的 `dynamicColor = true` 会在 Android 12+ 上永远用壁纸配色，
 * 导致用户选的预设配色根本显示不出来。这里改成由 [AppPrefs.themeId] 决定。
 */
@Composable
fun FlowtTheme(
    prefs: AppPrefs,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = prefs.resolveColorScheme(),
        typography = Typography,
        content = content,
    )
}
