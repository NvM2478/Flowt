package com.flowt.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.theme.ColorPalette
import com.flowt.app.ui.theme.RoleKey
import com.flowt.app.ui.theme.subtleTextColor

/**
 * 角色颜色选择器。
 *
 * 只让用户改**角色对应的颜色值**，不让改"哪个部件用哪个角色" ——
 * 所以改一次「卡片底色」，流水卡片、指标卡、记账页背景会同时变，一致性由架构保证。
 *
 * 为什么用色板而不是自由取色（HSV 拾色器）：
 * 1. 自由取色极易挑出与整套配色冲突的脏色，色板是"已经调好的候选"；
 * 2. 手机上点一下比拖三个滑块快得多，也不用引入第三方取色库；
 * 3. 想扩展就往 ColorPalette.swatches 加颜色，这里自动多一格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleColorPickerSheet(
    role: RoleKey,
    currentColor: Color,
    canReset: Boolean,
    onPick: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                text = "「${role.displayName}」的颜色",
                style = MaterialTheme.typography.titleMedium,
            )
            if (role.pairHint != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = role.pairHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(Modifier.size(12.dp))
                Text("当前颜色", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                if (canReset) {
                    TextButton(onClick = onReset) { Text("恢复默认") }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                modifier = Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ColorPalette.swatches) { argb ->
                    val color = Color(argb)
                    val selected = color.value == currentColor.value
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onPick(argb) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
