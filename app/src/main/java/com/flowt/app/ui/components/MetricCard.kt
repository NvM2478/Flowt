package com.flowt.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.metrics.formatAmount

/**
 * 指标卡。
 *
 * 首页和「首页指标」设置页共用同一个组件 —— 这样设置页里看到的就是首页真正会长的样子，
 * 而不是一个需要脑内换算的勾选列表。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricCard(
    title: String,
    amountText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** 长按（可选）。传了才启用长按手势与随之而来的振动反馈。 */
    onLongClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    )

    // fillMaxWidth 必须有：卡片要被"外部约束"撑满，而不是按内容自适应。
    // 首页的 weight(1f) / 设置页的网格格子都只作用到调用方的容器上，
    // 若这里不撑开，"本月支出"这种短标签的卡片就会比邻居窄一截。
    val baseModifier = modifier.fillMaxWidth()

    when {
        // 注意分支顺序：有长按就必须走中间那支，不能先判 onClick == null。
        // 首页卡片只传 onLongClick（单击不需要动作），若先判 onClick 为空，
        // 长按逻辑会被"纯展示"分支整个吞掉 —— 表现就是点、长按都毫无反应。
        onLongClick != null -> {
            // 用 combinedClickable 而不是 Card(onClick=...)：后者只支持单击，
            // 而这里同时要"点一下"和"长按"两种手势。
            //
            // 两个细节都是踩过的坑：
            // 1. combinedClickable 必须挂在 Card **自身**、并由外层 clip 提供圆角，
            //    不能挂在 Card 内部的容器上 —— 内部容器按内容大小测量（只有文字那块），
            //    涟漪就只覆盖文字区域，而不是整张卡片。
            // 2. **不要再手动 performHapticFeedback**：combinedClickable 长按成功时
            //    自身就会触发一次系统震动，手动再加一次就是"震两下"。
            //    （之前用户要求"更干脆的震动"，我选了 LongPress 类型，结果与系统默认叠加了。）
            // 注意 baseModifier（含调用方给的 weight(1f)）必须作用在**外层 Box** 上，
            // 不能只给里面的 Card —— 否则 Box 没有宽度约束、会按内容撑满整行，
            // 表现就是"一个卡片占满整行"，而不是只占一格。
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .combinedClickable(
                        onClick = onClick ?: { },
                        onLongClick = onLongClick,
                    )
                    .then(baseModifier),
            ) {
                Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
                    MetricCardContent(title = title, amountText = amountText)
                }
            }
        }

        onClick != null -> {
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

        else -> {
            // 既不可点也不可长按：纯展示，不白给交互层
            Card(modifier = baseModifier, colors = colors) {
                MetricCardContent(title = title, amountText = amountText)
            }
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
    onLongClick: (() -> Unit)? = null,
) = MetricCard(
    title = title,
    amountText = formatAmount(amountCents),
    modifier = modifier,
    onClick = onClick,
    onLongClick = onLongClick,
)
