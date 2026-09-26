package com.flowt.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.AppViewModel
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
    onOpenAbout: () -> Unit,
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "group_data") {
            SettingsEntryGroup {
                SettingsEntry(
                    title = "分类管理",
                    onClick = onOpenCategoryManage,
                )
                SettingsEntry(
                    title = "首页指标",
                    onClick = onOpenMetrics,
                )
                SettingsEntry(
                    title = "外观",
                    onClick = onOpenAppearance,
                )
                SettingsEntry(
                    title = "数据管理",
                    onClick = onOpenData,
                )
            }
        }

        item(key = "group_about") {
            SettingsEntryGroup {
                SettingsEntry(
                    title = "关于 Flowt",
                    onClick = onOpenAbout,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = subtleTextColor(),
            )
        }
    }
}
