package com.flowt.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/** WCAG AA 对正文文字的可读性阈值：对比度低于它就该触发风险确认。 */
const val CONTRAST_READABLE = 4.5

/** WCAG 相对对比度。1.0 = 无对比，21.0 = 纯黑对纯白。 */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * 从一个底色派生出它上面的文字色。
 *
 * 规则：保持底色的色相（字与底是"一家人"），饱和度适度收敛（正文不需要底色那么艳），
 * 明度向远离底色的方向拉开，再用对比度校验兜底 —— 明度与感知亮度单调相关，
 * 朝远离方向步进必然收敛到 ≥ [CONTRAST_READABLE]。
 *
 * 纯函数：输入底色唯一确定输出，所以"改底色 → 配对字色实时重算"不需要存储任何东西。
 */
fun deriveOnColor(background: Color): Color {
    val (hue, saturation, value) = rgbToHsv(background)
    // 正文文字不需要底色那么鲜艳，收敛一下更像 Material 的 on-container 色
    val textSaturation = minOf(saturation, 0.7f)
    val darkBackground = value < 0.5f

    // 第一段：拉开明度（暗底变亮 / 亮底变暗）
    var candidate = if (darkBackground) {
        (value + 0.55f).coerceAtMost(1f)
    } else {
        (value - 0.55f).coerceAtLeast(0f)
    }
    var guard = 0
    while (guard++ < 48) {
        val color = hsvToColor(hue, textSaturation, candidate)
        if (contrastRatio(color, background) >= CONTRAST_READABLE) return color
        candidate = if (darkBackground) candidate + 0.04f else candidate - 0.04f
        if (candidate !in 0f..1f) break
    }

    // 第二段：明度到边界仍不达标 —— 红/粉/蓝这类色相"最亮的纯色"本身亮度有限，
    // 光拉明度永远到不了 4.5。降饱和度让颜色向纯白/纯黑收敛，对比度随之上升，必然达标。
    val edgeValue = if (darkBackground) 1f else 0f
    var textSat = textSaturation
    while (textSat > 0f) {
        textSat = (textSat - 0.1f).coerceAtMost(0f)
        val color = hsvToColor(hue, textSat, edgeValue)
        if (contrastRatio(color, background) >= CONTRAST_READABLE) return color
    }
    return if (darkBackground) Color.White else Color.Black
}

/** RGB → HSV。返回 (hue 0~360, saturation 0~1, value 0~1)。 */
internal fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}

/**
 * 从一个强调色派生同色相的"雾色"（低饱和微彩）：大面积底色的中性化。
 *
 * 跟随系统取色的动态方案里，记账页/流水条目的大面积底天生是 tone 90 的饱和
 * 容器色 —— 与"面积与饱和度成反比"的分层原则相反，用这个函数把它拉回雾色，
 * 与预设主题的手工校准值保持同一观感（浅色 S≈0.04/V≈0.97 一族）。
 *
 * [strong] 为 true 时保留多一点彩感，用于指标卡这类小面积、需要一点颜色的容器。
 */
fun mistFrom(accent: Color, dark: Boolean, strong: Boolean = false): Color {
    val (hue, _, _) = rgbToHsv(accent)
    return when {
        dark && strong -> hsvToColor(hue, saturation = 0.10f, value = 0.30f)
        dark -> hsvToColor(hue, saturation = 0.05f, value = 0.17f)
        strong -> hsvToColor(hue, saturation = 0.08f, value = 0.94f)
        else -> hsvToColor(hue, saturation = 0.04f, value = 0.965f)
    }
}

/** HSV → Color。与 [rgbToHsv] 互逆。 */
internal fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - abs(hue / 60f % 2f - 1f))
    val m = value - c
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
