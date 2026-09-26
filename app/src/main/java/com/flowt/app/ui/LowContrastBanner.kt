package com.flowt.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 低对比度颜色的全局待决胶囊：应用了低对比配对色后浮出，悬浮于所有页面之上，
 * 永不自动消失 —— [onKeep] 保留颜色并关闭，[onRevert] 撤销这次颜色。
 *
 * 刻意固定深底白字、不跟随主题：用户配色可能改得很糟，这个提醒必须在
 * 任何配色下都清晰可见，不能被用户的配色“藏”进背景里。
 */
@Composable
fun LowContrastBanner(
    onKeep: () -> Unit,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        color = Color(0xE61C1B1F),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "对比度较低，可能看不清",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(4.dp))
            TextButton(onClick = onKeep) { Text("保留", color = Color.White) }
            TextButton(onClick = onRevert) { Text("撤销", color = Color(0xFFFF8A80)) }
        }
    }
}
