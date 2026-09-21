package com.flowt.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.metrics.formatAmount

/**
 * 指标卡。
 *
 * 首页和「首页指标」设置页共用同一个组件 —— 这样设置页里看到的就是首页真正会长的样子，
 * 而不是一个需要脑内换算的勾选列表。
 */
@Composable
fun MetricCard(
    title: String,
    amountText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    )

    // fillMaxWidth 必须有：卡片要被"外部约束"撑满，而不是按内容自适应。
    // 首页的 weight(1f) / 设置页的网格格子都只作用到调用方的容器上，
    // 若这里不撑开，"本月支出"这种短标签的卡片就会比邻居窄一截。
    val baseModifier = modifier.fillMaxWidth()

    if (onClick == null) {
        // 不可点击的场合（首页纯展示）用无交互版本，避免白给一层点击处理
        Card(modifier = baseModifier, colors = colors) {
            MetricCardContent(title = title, amountText = amountText)
        }
    } else {
        // 用 Card(onClick=...) 而不是在外层套 clickable：
        // Card 自带形状裁剪，涟漪会贴合圆角；套在外面则涟漪是个矩形，和圆角对不上。
        // 顺带获得正确的可点击语义（TalkBack 会念出"按钮"）。
        Card(
            onClick = onClick,
            modifier = baseModifier,
            colors = colors,
        ) {
            MetricCardContent(title = title, amountText = amountText)
        }
    }
}

@Composable
private fun MetricCardContent(title: String, amountText: String) {
    Column(modifier = Modifier.padding(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
        )
    }
}

/** 便捷重载：金额为 null（无数据）时显示为 "—"，与"花了 0 元"区分开。 */
@Composable
fun MetricCard(
    title: String,
    amountCents: Long?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) = MetricCard(
    title = title,
    amountText = formatAmount(amountCents),
    modifier = modifier,
    onClick = onClick,
)
