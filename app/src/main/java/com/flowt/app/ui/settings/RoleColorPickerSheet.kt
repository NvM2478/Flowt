package com.flowt.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.theme.ColorPalette
import com.flowt.app.ui.theme.FlowtColors
import com.flowt.app.ui.theme.RoleKey
import com.flowt.app.ui.theme.subtleTextColor

/**
 * 角色颜色选择器。
 *
 * 布局三段式：**头部固定**（角色名 + 新颜色预览，与外观页角色行同构）→
 * **内容区单滚动**（色板全展开 + 自定义取色，没有嵌套滚动源）→
 * **底部按钮固定**（重置 / 取消 / 使用此颜色，滚到哪里都够得着）。
 *
 * 弹窗取消部分展开，只有全开和关闭两种状态。
 * 只让用户改**角色对应的颜色值**，改一次「卡片底色」所有绑它的部件同时变色。
 * 配对文字角色（[RoleKind.ON]）头部实时显示与配对底色的对比度，达标与否先看见。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoleColorPickerSheet(
    role: RoleKey,
    /** 当前生效明暗：色板候选按它成套生成（雾色/文字深浅都跟着换）。 */
    dark: Boolean,
    currentColor: Color,
    /** 该角色清掉自身覆盖后的默认色：重置的回退目标，也是置灰的判断基准。 */
    defaultColor: Color,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember(role, currentColor) { mutableStateOf(currentColor) }
    // 色板选色时让取色盘跟着定位（序号递增：重复点同一色块也能再次触发）
    var hsvResync by remember(role, currentColor) { mutableStateOf<Pair<Int, Color>?>(null) }
    // 重置随取色实时判断：预览色离开默认色即可重置，点回默认色自动置灰
    val canResetNow = selected != defaultColor

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // —— 头部（固定）：角色名与说明 …… 新颜色预览圆 ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "「${role.displayName}」的颜色",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    role.pairHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtleTextColor(),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(selected)
                        .border(1.dp, FlowtColors.current.outlineBorder, CircleShape),
                )
            }

            // —— 内容区（唯一滚动源）：色板全展开，自定义取色 ——
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ColorPalette.swatchesFor(role, dark).forEach { argb ->
                        val color = Color(argb)
                        val isSelected = color == selected
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        FlowtColors.current.accentInlineText
                                    } else {
                                        FlowtColors.current.outlineBorder
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    selected = color
                                    hsvResync = ((hsvResync?.first ?: 0) + 1) to color
                                },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "自定义取色",
                    style = MaterialTheme.typography.labelLarge,
                    color = subtleTextColor(),
                )
                Spacer(Modifier.height(8.dp))
                HsvColorPicker(
                    initial = currentColor,
                    onColorChange = { selected = it },
                    resync = hsvResync,
                )
                Spacer(Modifier.height(16.dp))
            }

            // —— 底部按钮（固定） ——
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 「重置」是纯预览操作：只把取色状态拉回默认色，应用仍由「使用此颜色」确认；
                // 随取色实时置灰——预览色回到默认色即恢复置灰
                OutlinedButton(
                    onClick = {
                        selected = defaultColor
                        hsvResync = ((hsvResync?.first ?: 0) + 1) to defaultColor
                    },
                    enabled = canResetNow,
                    modifier = Modifier.weight(1f),
                ) { Text("重置") }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                Button(
                    onClick = { onPick(selected.toArgb()) },
                    modifier = Modifier.weight(1.6f),
                ) { Text("应用") }
            }
        }
    }
}
