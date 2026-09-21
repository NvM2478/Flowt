package com.flowt.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.THEME_ID_SYSTEM_DYNAMIC
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.launch

/**
 * 设置首页：**只放入口**，具体配置都在二级页面里。
 *
 * 这样做的好处不是好看，而是可扩展 —— 以后加"预算""标签管理""提醒"
 * 都只是往这个列表里加一行，不会让首页变成一条长长的滚动条。
 *
 * 二级页面的渲染与转场**不在这里**，而在 [SettingsSubPageHost]：
 * 它必须渲染在 Scaffold 外面，否则盖不住底部导航栏和 FAB。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onOpenCategoryManage: () -> Unit,
    onOpenMetrics: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenData: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "group_data") {
            SettingsEntryGroup {
                SettingsEntry(
                    title = "记账分类管理",
                    subtitle = "建分类树、改名、删除",
                    onClick = onOpenCategoryManage,
                )
                SettingsEntry(
                    title = "首页指标",
                    subtitle = "选择首页显示哪几个指标",
                    onClick = onOpenMetrics,
                )
                SettingsEntry(
                    title = "外观",
                    subtitle = "配色方案与自定义颜色",
                    onClick = onOpenAppearance,
                )
                SettingsEntry(
                    title = "数据管理",
                    subtitle = "导入账单、导出备份",
                    onClick = onOpenData,
                )
            }
        }

        // 逃生通道放在一级页面：配色被改花之后，用户不必先找到「外观」再找到按钮。
        // 用固定配色（深底白字）而不是主题色 —— 它必须在配色被改坏之后依然可见。
        item(key = "reset_colors") {
            Button(
                onClick = {
                    scope.launch { vm.prefsRepo.setSelectedTheme(THEME_ID_SYSTEM_DYNAMIC) }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xE61C1B1F),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复默认颜色")
            }
        }

        item(key = "group_about") {
            SettingsEntryGroup {
                SettingsEntry(
                    title = "关于 Flowt",
                    subtitle = "v1.0 · 数据只存在这台手机上",
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun SettingsEntryGroup(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtleTextColor(),
            )
        }
        if (onClick != null) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = subtleTextColor(),
            )
        }
    }
}
