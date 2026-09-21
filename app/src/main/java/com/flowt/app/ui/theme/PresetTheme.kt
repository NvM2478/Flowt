package com.flowt.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 配色注册表。
 *
 * 扩展方式：往 [PresetTheme.all] 里加一项 —— 设置页的配色选择器会自动多出一行，
 * 不需要改界面代码。
 *
 * Material 3 是**角色制**配色（primary / secondary / tertiary / container …），
 * 不是"部件制"。所以配色方案只定义角色色，具体某个部件用什么颜色由组件自己决定 ——
 * 这样每套配色都能自动适配浅色/深色、以及所有组件的状态色。
 *
 * [seedColor] 是为"用户自选主色"预留的扩展点：将来做一个取色器，
 * 用种子色推导出整套 ColorScheme 即可，架构不用动。V1 不开放。
 */
class PresetTheme(
    val id: String,
    val displayName: String,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
    val seedColor: Color,
) {
    companion object {

        val all: List<PresetTheme> = listOf(
            PresetTheme(
                id = "violet",
                displayName = "紫罗兰",
                seedColor = Color(0xFF6750A4),
                light = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    secondary = Color(0xFF625B71),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFE8DEF8),
                    onSecondaryContainer = Color(0xFF1D192B),
                    tertiary = Color(0xFF7D5260),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFFFD8E4),
                    onTertiaryContainer = Color(0xFF31111D),
                    error = Color(0xFFB3261E),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFF9DEDC),
                    onErrorContainer = Color(0xFF410E0B),
                    background = Color(0xFFFFFBFE),
                    onBackground = Color(0xFF1C1B1F),
                    surface = Color(0xFFFFFBFE),
                    onSurface = Color(0xFF1C1B1F),
                    surfaceVariant = Color(0xFFE7E0EC),
                    onSurfaceVariant = Color(0xFF49454F),
                    outline = Color(0xFF79747E),
                    outlineVariant = Color(0xFFCAC4D0),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    onPrimary = Color(0xFF381E72),
                    primaryContainer = Color(0xFF4F378B),
                    onPrimaryContainer = Color(0xFFEADDFF),
                    secondary = Color(0xFFCCC2DC),
                    onSecondary = Color(0xFF332D41),
                    secondaryContainer = Color(0xFF4A4458),
                    onSecondaryContainer = Color(0xFFE8DEF8),
                    tertiary = Color(0xFFEFB8C8),
                    onTertiary = Color(0xFF492532),
                    tertiaryContainer = Color(0xFF633B48),
                    onTertiaryContainer = Color(0xFFFFD8E4),
                    error = Color(0xFFF2B8B5),
                    onError = Color(0xFF601410),
                    errorContainer = Color(0xFF8C1D18),
                    onErrorContainer = Color(0xFFF9DEDC),
                    background = Color(0xFF1C1B1F),
                    onBackground = Color(0xFFE6E1E5),
                    surface = Color(0xFF1C1B1F),
                    onSurface = Color(0xFFE6E1E5),
                    surfaceVariant = Color(0xFF49454F),
                    onSurfaceVariant = Color(0xFFCAC4D0),
                    outline = Color(0xFF938F99),
                    outlineVariant = Color(0xFF49454F),
                ),
            ),
            PresetTheme(
                id = "teal",
                displayName = "青碧",
                seedColor = Color(0xFF00696E),
                light = lightColorScheme(
                    primary = Color(0xFF00696E),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFF9DF0F6),
                    onPrimaryContainer = Color(0xFF002022),
                    secondary = Color(0xFF4A6365),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFCCE8EA),
                    onSecondaryContainer = Color(0xFF051F21),
                    tertiary = Color(0xFF4B607C),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFD3E4FF),
                    onTertiaryContainer = Color(0xFF041C35),
                    error = Color(0xFFBA1A1A),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFDAD6),
                    onErrorContainer = Color(0xFF410002),
                    background = Color(0xFFFAFDFC),
                    onBackground = Color(0xFF191C1C),
                    surface = Color(0xFFFAFDFC),
                    onSurface = Color(0xFF191C1C),
                    surfaceVariant = Color(0xFFDAE4E5),
                    onSurfaceVariant = Color(0xFF3F4949),
                    outline = Color(0xFF6F7979),
                    outlineVariant = Color(0xFFBEC8C9),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFF80D4DA),
                    onPrimary = Color(0xFF003739),
                    primaryContainer = Color(0xFF004F53),
                    onPrimaryContainer = Color(0xFF9DF0F6),
                    secondary = Color(0xFFB0CCCE),
                    onSecondary = Color(0xFF1B3436),
                    secondaryContainer = Color(0xFF324B4D),
                    onSecondaryContainer = Color(0xFFCCE8EA),
                    tertiary = Color(0xFFB3C8E8),
                    onTertiary = Color(0xFF1C314B),
                    tertiaryContainer = Color(0xFF334763),
                    onTertiaryContainer = Color(0xFFD3E4FF),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005),
                    errorContainer = Color(0xFF93000A),
                    onErrorContainer = Color(0xFFFFDAD6),
                    background = Color(0xFF191C1C),
                    onBackground = Color(0xFFE0E3E3),
                    surface = Color(0xFF191C1C),
                    onSurface = Color(0xFFE0E3E3),
                    surfaceVariant = Color(0xFF3F4949),
                    onSurfaceVariant = Color(0xFFBEC8C9),
                    outline = Color(0xFF899393),
                    outlineVariant = Color(0xFF3F4949),
                ),
            ),
            PresetTheme(
                id = "orange",
                displayName = "暖橙",
                seedColor = Color(0xFF9A4520),
                light = lightColorScheme(
                    primary = Color(0xFF9A4520),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFFFDBCD),
                    onPrimaryContainer = Color(0xFF370E00),
                    secondary = Color(0xFF77574B),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFFFDBCD),
                    onSecondaryContainer = Color(0xFF2C150C),
                    tertiary = Color(0xFF6B5D2F),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFF4E1A7),
                    onTertiaryContainer = Color(0xFF221B00),
                    error = Color(0xFFBA1A1A),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFDAD6),
                    onErrorContainer = Color(0xFF410002),
                    background = Color(0xFFFFF8F6),
                    onBackground = Color(0xFF231917),
                    surface = Color(0xFFFFF8F6),
                    onSurface = Color(0xFF231917),
                    surfaceVariant = Color(0xFFF5DED7),
                    onSurfaceVariant = Color(0xFF53433F),
                    outline = Color(0xFF85736E),
                    outlineVariant = Color(0xFFD8C2BC),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFFFFB59B),
                    onPrimary = Color(0xFF5A1B00),
                    primaryContainer = Color(0xFF7A2F0A),
                    onPrimaryContainer = Color(0xFFFFDBCD),
                    secondary = Color(0xFFE7BEB0),
                    onSecondary = Color(0xFF442A20),
                    secondaryContainer = Color(0xFF5D4035),
                    onSecondaryContainer = Color(0xFFFFDBCD),
                    tertiary = Color(0xFFD7C58D),
                    onTertiary = Color(0xFF3A2F04),
                    tertiaryContainer = Color(0xFF524619),
                    onTertiaryContainer = Color(0xFFF4E1A7),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005),
                    errorContainer = Color(0xFF93000A),
                    onErrorContainer = Color(0xFFFFDAD6),
                    background = Color(0xFF1A110F),
                    onBackground = Color(0xFFF1DFDA),
                    surface = Color(0xFF1A110F),
                    onSurface = Color(0xFFF1DFDA),
                    surfaceVariant = Color(0xFF53433F),
                    onSurfaceVariant = Color(0xFFD8C2BC),
                    outline = Color(0xFFA08C87),
                    outlineVariant = Color(0xFF53433F),
                ),
            ),
            PresetTheme(
                id = "indigo",
                displayName = "靛蓝",
                seedColor = Color(0xFF4355B9),
                light = lightColorScheme(
                    primary = Color(0xFF4355B9),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFDEE0FF),
                    onPrimaryContainer = Color(0xFF00105C),
                    secondary = Color(0xFF5B5D72),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFE0E1F9),
                    onSecondaryContainer = Color(0xFF181A2C),
                    tertiary = Color(0xFF77536D),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFFFD7F1),
                    onTertiaryContainer = Color(0xFF2D1228),
                    error = Color(0xFFBA1A1A),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFDAD6),
                    onErrorContainer = Color(0xFF410002),
                    background = Color(0xFFFBF8FF),
                    onBackground = Color(0xFF1B1B21),
                    surface = Color(0xFFFBF8FF),
                    onSurface = Color(0xFF1B1B21),
                    surfaceVariant = Color(0xFFE2E1EC),
                    onSurfaceVariant = Color(0xFF45464F),
                    outline = Color(0xFF767680),
                    outlineVariant = Color(0xFFC6C5D0),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFFBAC3FF),
                    onPrimary = Color(0xFF08218A),
                    primaryContainer = Color(0xFF293CA0),
                    onPrimaryContainer = Color(0xFFDEE0FF),
                    secondary = Color(0xFFC4C5DD),
                    onSecondary = Color(0xFF2D2F42),
                    secondaryContainer = Color(0xFF434559),
                    onSecondaryContainer = Color(0xFFE0E1F9),
                    tertiary = Color(0xFFE6BAD7),
                    onTertiary = Color(0xFF44263D),
                    tertiaryContainer = Color(0xFF5D3C55),
                    onTertiaryContainer = Color(0xFFFFD7F1),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005),
                    errorContainer = Color(0xFF93000A),
                    onErrorContainer = Color(0xFFFFDAD6),
                    background = Color(0xFF1B1B21),
                    onBackground = Color(0xFFE3E1E9),
                    surface = Color(0xFF1B1B21),
                    onSurface = Color(0xFFE3E1E9),
                    surfaceVariant = Color(0xFF45464F),
                    onSurfaceVariant = Color(0xFFC6C5D0),
                    outline = Color(0xFF90909A),
                    outlineVariant = Color(0xFF45464F),
                ),
            ),
            PresetTheme(
                id = "forest",
                displayName = "森绿",
                seedColor = Color(0xFF3F6837),
                light = lightColorScheme(
                    primary = Color(0xFF3F6837),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFC0EFB0),
                    onPrimaryContainer = Color(0xFF002201),
                    secondary = Color(0xFF55624F),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFD9E7CF),
                    onSecondaryContainer = Color(0xFF131F10),
                    tertiary = Color(0xFF38656A),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFBCEBF0),
                    onTertiaryContainer = Color(0xFF002022),
                    error = Color(0xFFBA1A1A),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFDAD6),
                    onErrorContainer = Color(0xFF410002),
                    background = Color(0xFFF8FBF1),
                    onBackground = Color(0xFF191D17),
                    surface = Color(0xFFF8FBF1),
                    onSurface = Color(0xFF191D17),
                    surfaceVariant = Color(0xFFDFE4D7),
                    onSurfaceVariant = Color(0xFF43483F),
                    outline = Color(0xFF73796E),
                    outlineVariant = Color(0xFFC3C8BB),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFFA5D397),
                    onPrimary = Color(0xFF11380D),
                    primaryContainer = Color(0xFF285020),
                    onPrimaryContainer = Color(0xFFC0EFB0),
                    secondary = Color(0xFFBDCBB4),
                    onSecondary = Color(0xFF283423),
                    secondaryContainer = Color(0xFF3E4A38),
                    onSecondaryContainer = Color(0xFFD9E7CF),
                    tertiary = Color(0xFFA0CFD4),
                    onTertiary = Color(0xFF00363A),
                    tertiaryContainer = Color(0xFF1F4D51),
                    onTertiaryContainer = Color(0xFFBCEBF0),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005),
                    errorContainer = Color(0xFF93000A),
                    onErrorContainer = Color(0xFFFFDAD6),
                    background = Color(0xFF11140F),
                    onBackground = Color(0xFFE1E4DA),
                    surface = Color(0xFF11140F),
                    onSurface = Color(0xFFE1E4DA),
                    surfaceVariant = Color(0xFF43483F),
                    onSurfaceVariant = Color(0xFFC3C8BB),
                    outline = Color(0xFF8D9287),
                    outlineVariant = Color(0xFF43483F),
                ),
            ),
            PresetTheme(
                id = "rose",
                displayName = "玫红",
                seedColor = Color(0xFF984061),
                light = lightColorScheme(
                    primary = Color(0xFF984061),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFFFD9E2),
                    onPrimaryContainer = Color(0xFF3E001D),
                    secondary = Color(0xFF74565F),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFFFD9E2),
                    onSecondaryContainer = Color(0xFF2B151C),
                    tertiary = Color(0xFF7C5635),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFFFDCC1),
                    onTertiaryContainer = Color(0xFF2E1500),
                    error = Color(0xFFBA1A1A),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFDAD6),
                    onErrorContainer = Color(0xFF410002),
                    background = Color(0xFFFFF8F8),
                    onBackground = Color(0xFF22191B),
                    surface = Color(0xFFFFF8F8),
                    onSurface = Color(0xFF22191B),
                    surfaceVariant = Color(0xFFF2DDE2),
                    onSurfaceVariant = Color(0xFF514347),
                    outline = Color(0xFF837377),
                    outlineVariant = Color(0xFFD5C2C6),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFFFFB1C8),
                    onPrimary = Color(0xFF5E1133),
                    primaryContainer = Color(0xFF7B2949),
                    onPrimaryContainer = Color(0xFFFFD9E2),
                    secondary = Color(0xFFE3BDC6),
                    onSecondary = Color(0xFF422931),
                    secondaryContainer = Color(0xFF5A3F47),
                    onSecondaryContainer = Color(0xFFFFD9E2),
                    tertiary = Color(0xFFEFBD94),
                    onTertiary = Color(0xFF472A0D),
                    tertiaryContainer = Color(0xFF613F20),
                    onTertiaryContainer = Color(0xFFFFDCC1),
                    error = Color(0xFFFFB4AB),
                    onError = Color(0xFF690005),
                    errorContainer = Color(0xFF93000A),
                    onErrorContainer = Color(0xFFFFDAD6),
                    background = Color(0xFF191113),
                    onBackground = Color(0xFFEFDFE1),
                    surface = Color(0xFF191113),
                    onSurface = Color(0xFFEFDFE1),
                    surfaceVariant = Color(0xFF514347),
                    onSurfaceVariant = Color(0xFFD5C2C6),
                    outline = Color(0xFF9E8C90),
                    outlineVariant = Color(0xFF514347),
                ),
            ),
            // 刻意做成"极端方案"：深色底 + 高饱和强调色。
            // 它的作用不只是好看 —— 切到这套配色能立刻看出每个部件实际用的是哪个角色：
            // 该亮的地方没亮、该是文字色的地方变成了容器色，一眼就能发现角色指派错了。
            PresetTheme(
                id = "neon",
                displayName = "霓虹",
                seedColor = Color(0xFF00E5FF),
                light = lightColorScheme(
                    primary = Color(0xFF00838F),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFB2EBF2),
                    onPrimaryContainer = Color(0xFF00201F),
                    secondary = Color(0xFF6A1B9A),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFE1BEE7),
                    onSecondaryContainer = Color(0xFF2A0033),
                    tertiary = Color(0xFF00897B),
                    onTertiary = Color(0xFFFFFFFF),
                    tertiaryContainer = Color(0xFFA7FFEB),
                    onTertiaryContainer = Color(0xFF00201A),
                    error = Color(0xFFD50000),
                    onError = Color(0xFFFFFFFF),
                    errorContainer = Color(0xFFFFCDD2),
                    onErrorContainer = Color(0xFF3E0000),
                    background = Color(0xFF0A0E27),
                    onBackground = Color(0xFFE0F7FA),
                    surface = Color(0xFF131A3A),
                    onSurface = Color(0xFFE0F7FA),
                    surfaceVariant = Color(0xFF2A3352),
                    onSurfaceVariant = Color(0xFFB0BEC5),
                    outline = Color(0xFF00E5FF),
                    outlineVariant = Color(0xFF3A4A6B),
                ),
                dark = darkColorScheme(
                    primary = Color(0xFF00E5FF),
                    onPrimary = Color(0xFF00363D),
                    primaryContainer = Color(0xFF005662),
                    onPrimaryContainer = Color(0xFFB2EBF2),
                    secondary = Color(0xFFE040FB),
                    onSecondary = Color(0xFF3A0050),
                    secondaryContainer = Color(0xFF6A1B9A),
                    onSecondaryContainer = Color(0xFFF3E5F5),
                    tertiary = Color(0xFF1DE9B6),
                    onTertiary = Color(0xFF00382E),
                    tertiaryContainer = Color(0xFF00695C),
                    onTertiaryContainer = Color(0xFFA7FFEB),
                    error = Color(0xFFFF5252),
                    onError = Color(0xFF3E0000),
                    errorContainer = Color(0xFF8E0000),
                    onErrorContainer = Color(0xFFFFCDD2),
                    background = Color(0xFF05070F),
                    onBackground = Color(0xFFE0F7FA),
                    surface = Color(0xFF0D1226),
                    onSurface = Color(0xFFE0F7FA),
                    surfaceVariant = Color(0xFF1E2740),
                    onSurfaceVariant = Color(0xFF90A4AE),
                    outline = Color(0xFF00E5FF),
                    outlineVariant = Color(0xFF2A3352),
                ),
            ),
        )

        fun byId(id: String): PresetTheme = all.firstOrNull { it.id == id } ?: all.first()

        /**
         * 从种子色推导整套配色的预留接口。
         *
         * V1 未开放 UI —— Material 3 的角色色推导需要 HCT 色彩空间计算，
         * 手写容易做出对比度不达标的配色。将来要做，用官方的
         * `material-color-utilities` 的 SchemeTonalSpot 实现这个函数即可，
         * 调用方（主题注册表 / 设置页）不需要改动。
         */
        fun fromSeed(seedColor: Color): PresetTheme =
            throw NotImplementedError("自定义种子色配色将在后续版本开放")
    }
}

/** 供设置页预览用：某套主题的浅色代表色（深色主色 → 浅色容器色）。 */
@Composable
fun PresetTheme.swatches(): List<Color> = listOf(
    light.primary,
    light.secondary,
    light.tertiary,
    light.primaryContainer,
    light.surfaceVariant,
)

/**
 * 一个可选的配色方案变体 = 某一套配色 × 浅色或深色。
 *
 * 为什么要把浅暗拆成独立选项，而不是"选方案 + 选明暗模式"两个设置：
 * - 少一个维度、少一个设置项；
 * - 用户能**直接看到"暗色版长什么样"再选**，不用先选方案再切深色去猜效果；
 * - 两套变体在列表里并列，对比起来一目了然。
 */
data class ThemeVariant(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    private val schemeProvider: () -> androidx.compose.material3.ColorScheme,
) {
    /** 懒解析：列表里十几个变体，没必要在构建列表时就把所有 ColorScheme 都造出来。 */
    fun scheme(): androidx.compose.material3.ColorScheme = schemeProvider()

    /** 列表用的代表色（取该变体自身的主色系）。 */
    fun previewColors(): List<Color> {
        val s = scheme()
        return listOf(s.primary, s.secondary, s.tertiary, s.primaryContainer, s.surfaceVariant)
    }

    companion object {
        /** 变体 id 编码："violet@light" / "violet@dark"。 */
        fun variantId(themeId: String, dark: Boolean) = "$themeId@${if (dark) "dark" else "light"}"

        fun themeIdOf(variantId: String): String = variantId.substringBefore('@')

        fun isDark(variantId: String): Boolean = variantId.substringAfter('@', "light") == "dark"

        /** 全部内置变体：每套配色一个浅色版、一个深色版。 */
        val builtIn: List<ThemeVariant> = PresetTheme.all.flatMap { preset ->
            listOf(
                ThemeVariant(
                    id = variantId(preset.id, dark = false),
                    displayName = "${preset.displayName} · 浅色",
                    isDark = false,
                    schemeProvider = { preset.light },
                ),
                ThemeVariant(
                    id = variantId(preset.id, dark = true),
                    displayName = "${preset.displayName} · 深色",
                    isDark = true,
                    schemeProvider = { preset.dark },
                ),
            )
        }

        fun byId(variantId: String): ThemeVariant? = builtIn.firstOrNull { it.id == variantId }
    }
}
