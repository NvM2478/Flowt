package com.flowt.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.flowt.app.data.AppPrefs
import com.flowt.app.data.BrightnessMode
import com.flowt.app.data.PrefsRepository
import com.flowt.app.data.combinedRoleOverrides

/** "跟随系统取色"的虚拟方案 id：不是一套固定配色，而是从壁纸取色（同时决定明暗）。 */
const val THEME_ID_SYSTEM_DYNAMIC = "system_dynamic"

/**
 * 系统当前是否深色，由 MainActivity 的 onConfigurationChanged 实时维护。
 *
 * 设置页等需要"系统明暗"的地方读它，而不是 isSystemInDarkTheme() ——
 * 后者依赖组合时提供的 Configuration，在 uiMode 变化被清单拦截后不会更新。
 */
val LocalSystemDark = staticCompositionLocalOf { false }

/**
 * 动态取色方案：优先从壁纸颜色**自行推导**，读取失败时退回 framework 方案。
 *
 * 不用 `dynamicColorScheme` 的直接结果做强调色，是因为部分系统（HyperOS 实测）
 * 不向第三方 app 推送壁纸调色板 —— 换壁纸后 framework 的颜色资源纹丝不动。
 * 直接读 WallpaperManager 的壁纸三色，色相保真，明度/饱和度拉回主题对应的
 * 色带（深色亮、浅色深），配对文字与雾底全部由本地工具派生 —— 行为在任何
 * ROM 上一致。framework 方案只作为兜底的"形状"（其余色槽），大面同样雾化。
 */
internal fun dynamicSchemeFromWallpaper(context: android.content.Context, dark: Boolean): androidx.compose.material3.ColorScheme {    // 动态取色 API 需要 31+（壁纸三色读取需要 27+，被它覆盖）：低版本退回默认预设。
    // 版本守卫放在函数第一行，lint 与运行时的保证一致。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return PresetTheme.byId("violet").let { if (dark) it.dark else it.light }
    }
    val framework = if (dark) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
    val wallpaperColors = runCatching {
        android.app.WallpaperManager.getInstance(context)
            .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
    }.getOrNull() ?: return framework.neutralizeLargeSurfaces(dark)

    val primary = toneAdjusted(wallpaperColors.primaryColor.toArgb().toComposeColor(), dark)
    val secondary = wallpaperColors.secondaryColor
        ?.toArgb()?.toComposeColor()?.let { toneAdjusted(it, dark) } ?: primary
    val tertiary = wallpaperColors.tertiaryColor
        ?.toArgb()?.toComposeColor()?.let { toneAdjusted(it, dark) } ?: secondary

    // 强调三色归一后，大面雾化与深色页面底（纯黑）统一交给 neutralizeLargeSurfaces ——
    // 与预设深色同一条规则，两条路径永不走样
    return framework.copy(
        primary = primary,
        onPrimary = deriveOnColor(primary),
        secondary = secondary,
        tertiary = tertiary,
    ).neutralizeLargeSurfaces(dark)
}

/** 把任意颜色拉回当前主题的明度/饱和度带（保持壁纸色相）：强调色既醒目又不刺眼。 */
private fun toneAdjusted(color: Color, dark: Boolean): Color {
    val (hue, saturation, _) = rgbToHsv(color)
    return if (dark) {
        hsvToColor(hue, minOf(saturation, 0.45f), value = 0.80f)
    } else {
        hsvToColor(hue, minOf(saturation, 0.75f), value = 0.45f)
    }
}

/**
 * 解析出**基底**配色方案（不含任何用户覆盖）。
 *
 * 明暗由 [AppPrefs.brightnessMode] 决定：跟随 [systemDark] / 强制浅 / 强制深；
 * 「我的方案」是固定颜色，不受明暗影响（兜底基底跟随系统即可，反正会被覆盖）。
 *
 * 三种来源：
 * 1. 跟随系统取色（Android 12+ 从壁纸取色），大面积底色做雾化后处理；
 * 2. 选中了保存方案 —— 基底用系统取色或默认预设兜底，方案的角色值经
 *    [AppPrefs.combinedRoleOverrides] 以"覆盖值"身份参与计算；
 * 3. 内置方案（"violet" 这类），取其明暗对应版本。
 *
 * 刻意做成**普通函数**而不是 @Composable：壁纸取色是个重操作，
 * 由调用方以 (方案 id, 明暗) 记忆化，避免每次重组都重新解析壁纸。
 */
fun resolveBaseScheme(
    prefs: AppPrefs,
    systemDark: Boolean,
    context: android.content.Context,
): androidx.compose.material3.ColorScheme {
    val effectiveDark = when (prefs.brightnessMode) {
        BrightnessMode.LIGHT -> false
        BrightnessMode.DARK -> true
        BrightnessMode.SYSTEM -> systemDark
    }
    return when {
        prefs.selectedThemeId == THEME_ID_SYSTEM_DYNAMIC ->
            dynamicSchemeFromWallpaper(context, effectiveDark)

        prefs.selectedThemeId.startsWith(PrefsRepository.SAVED_PREFIX) ->
            // 保存方案可能因兼容性问题缺失个别角色，用"跟随系统取色"的基底兜底
            dynamicSchemeFromWallpaper(context, systemDark)

        else -> {
            val preset = PresetTheme.byId(prefs.selectedThemeId)
            if (effectiveDark) preset.dark else preset.light
        }
    }
}

/**
 * 壁纸版本号：Activity 每次回到前台递增（换壁纸必然离开过 app）。
 * 壁纸取色的记忆化以它为 key 的一部分 —— 版本变了就重新向系统查询新配色。
 */
val LocalWallpaperEpoch = staticCompositionLocalOf { 0 }

/**
 * 应用主题。[dark] 由调用方提供（MainActivity 从系统配置实时维护），
 * 颜色的计算只此一处，且全部按**内容**记忆化 —— 方案没变时，无论界面因为什么
 * 在重组，这里都不会重算、不会产生新的颜色对象，更不会向全树广播失效：
 * 1. [resolveBaseScheme] 取基底配色（按方案 id + 明暗 + 壁纸版本记忆化，壁纸取色只在变化时发生）；
 * 2. [effectiveRoleColors] 算出全部角色的生效值（覆盖 > 派生 > 基底）；
 * 3. [withRoleValues] 把它们写进色槽喂给 M3 内置组件；
 * 4. 同一份角色值装配成 [FlowtColors] 供 app 自己的部件按颜色位取色。
 *
 * 与 Studio 模板版本的关键差异：**不强制动态取色**。
 * 模板的 `dynamicColor = true` 会在 Android 12+ 上永远用壁纸配色，
 * 导致用户选的预设配色根本显示不出来。这里改成由 [AppPrefs.selectedThemeId] 决定。
 */
@Composable
fun FlowtTheme(
    prefs: AppPrefs,
    dark: Boolean,
    wallpaperEpoch: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // combinedRoleOverrides 的 Map 按内容 equals，方案/微调没变时就是同一个 key
    val overrides = prefs.combinedRoleOverrides()

    val base = remember(prefs.selectedThemeId, prefs.brightnessMode, dark, wallpaperEpoch) {
        resolveBaseScheme(prefs, dark, context)
    }
    val roleValues = remember(base, overrides, dark) {
        effectiveRoleColors(base, overrides, dark)
    }
    // 每个角色"清掉临时微调"后的默认值：取色弹窗的重置判断与回退目标。
    // 保存方案下 = 方案存的值（方案的完整色值就是该场景的"默认"）；
    // 内置方案下 = 基底槽值。ON 角色派生自同规则下的配对底色。
    val defaultRoleValues = remember(base, overrides, prefs.roleOverrides, dark) {
        RoleKey.entries.associateWith { role ->
            val pairBase = role.pairsWith
            when {
                role.kind == RoleKind.ON && pairBase != null && pairBase.name in overrides ->
                    deriveOnColor(roleValues.getValue(pairBase))
                role.name in overrides -> baseValueOf(role, base, dark)
                else -> roleValues.getValue(role)
            }
        }
    }
    // 色槽同样按内容记忆化：颜色没变时保持同一实例，M3 组件不会因无关重组而全树失效
    val colorScheme = remember(base, roleValues) { base.withRoleValues(roleValues) }
    val colors = remember(roleValues, defaultRoleValues) { FlowtColors(roleValues, defaultRoleValues) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        CompositionLocalProvider(
            LocalFlowtColors provides colors,
            LocalSystemDark provides dark,
            LocalWallpaperEpoch provides wallpaperEpoch,
        ) {
            content()
        }
    }
}
