package com.flowt.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.theme.hsvToColor
import com.flowt.app.ui.theme.rgbToHsv

/**
 * HSV 自由取色盘：色相条 + 饱和度/明度面板 + HEX 输入。
 *
 * 只负责"选出一个颜色"并实时上报，不做任何风险判断 —— 那是调用方（角色颜色弹窗）
 * 结合角色类型与对比度要做的事。内部以 HSV 为单一事实源（拖动手势天然是 HSV 坐标），
 * 上报后**不回读**外部状态，避免拖动 → 上报 → 重组 → 重置手位的循环。
 *
 * 外部其他入口（色板、HEX 之外再点选了颜色）要带着取色盘一起定位时，传 [resync]：
 * 序号递增保证"重复点同一个颜色"也能再次触发。
 */
@Composable
fun HsvColorPicker(
    initial: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    resync: Pair<Int, Color>? = null,
) {
    val initialHsv = FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    // remember 以 initial 为 key：弹窗每次打开（initial=当时的生效色）都重新定位指针，
    // 不会残留上一次打开时拖到的位置
    var hue by remember(initial) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initial) { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(initial) { mutableStateOf(formatHex(initial)) }

    // 外部选色 → 取色盘指针一起定位过去
    LaunchedEffect(resync) {
        resync?.second?.let { color ->
            val (h, s, v) = rgbToHsv(color)
            hue = h
            saturation = s
            value = v
            hexInput = formatHex(color)
        }
    }

    fun report() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        onColorChange(Color(argb))
        hexInput = formatHex(Color(argb))
    }

    Column(modifier = modifier) {
        // 饱和度（横向：白 → 纯色）× 明度（纵向：纯色 → 黑）的二维面板
        SvPanel(
            hue = hue,
            saturation = saturation,
            value = value,
            onChange = { s, v ->
                saturation = s
                value = v
                report()
            },
        )

        Spacer(Modifier.height(12.dp))

        HueBar(
            hue = hue,
            onChange = {
                hue = it
                report()
            },
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    hexInput = raw
                    parseHex(raw)?.let { parsed ->
                        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(parsed.toArgb(), it) }
                        hue = hsv[0]
                        saturation = hsv[1]
                        value = hsv[2]
                        onColorChange(parsed)
                    }
                },
                label = { Text("HEX") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = "拖动选色，当前颜色实时预览在上方",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 饱和度/明度二维面板：横向是"白 → 当前色相的纯色"，纵向叠"透明 → 黑"。 */
@Composable
private fun SvPanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    var panelSize by remember { mutableStateOf(IntSize.Zero) }

    fun update(pos: Offset) {
        if (panelSize == IntSize.Zero) return
        val s = (pos.x / panelSize.width).coerceIn(0f, 1f)
        val v = 1f - (pos.y / panelSize.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .onSizeChanged { panelSize = it }
            .pointerInput(hue) {
                detectTapGestures { offset -> update(offset) }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position)
                }
            },
    ) {
        clipRect {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hsvToColor(hue, 1f, 1f))))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }
        // 当前色点：饱和度定 x，明度定 y（翻转：上亮下暗）
        drawCircle(
            color = hsvToColor(hue, saturation, value),
            radius = 12.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
        )
        drawCircle(
            color = Color.White,
            radius = 12.dp.toPx(),
            center = Offset(saturation * size.width, (1f - value) * size.height),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

/** 色相条：0°~360° 的彩虹渐变。 */
@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    val rainbow = remember {
        List(13) { i -> hsvToColor(i * 30f, 1f, 1f) }
    }

    fun update(pos: Offset) {
        if (barSize == IntSize.Zero) return
        onChange((pos.x / barSize.width * 360f).coerceIn(0f, 360f))
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { barSize = it }
            .pointerInput(Unit) {
                detectTapGestures { offset -> update(offset) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position)
                }
            },
    ) {
        clipRect {
            drawRect(brush = Brush.horizontalGradient(rainbow))
        }
        drawCircle(
            color = hsvToColor(hue, 1f, 1f),
            radius = 10.dp.toPx(),
            center = Offset(hue / 360f * size.width, size.height / 2f),
        )
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(hue / 360f * size.width, size.height / 2f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun formatHex(color: Color): String =
    Integer.toHexString(color.toArgb() and 0xFFFFFF).padStart(6, '0').uppercase().let { "#$it" }

/** 解析 "#RRGGBB" / "RRGGBB" / "#AARRGGBB"；不合法返回 null（输入框内容保留原样）。 */
internal fun parseHex(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (cleaned.length !in 6..8) return null
    val value = cleaned.toLongOrNull(16) ?: return null
    val argb = when (cleaned.length) {
        6 -> (0xFF000000L or value).toInt()
        8 -> value.toInt()
        else -> return null
    }
    return Color(argb)
}
