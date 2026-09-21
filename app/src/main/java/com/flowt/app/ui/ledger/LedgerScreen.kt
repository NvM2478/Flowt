package com.flowt.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.metrics.MetricValue
import com.flowt.app.metrics.formatAmount
import com.flowt.app.ui.components.MetricCard
import com.flowt.app.ui.theme.expenseColor
import com.flowt.app.ui.theme.subtleTextColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 流水列表项：流水 + 它的分类路径。 */
private data class TxRow(
    val entity: TransactionEntity,
    val categoryPath: String,
)

/** 按天分组：一天的流水 + 当天小计。 */
private data class DayGroup(
    val startOfDay: Long,
    val totalCents: Long,
    val rows: List<TxRow>,
)

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.CHINA)

/**
 * 卡片在屏幕上的位置。
 *
 * 用普通可变对象而不是 State：这个值只在手势回调里读一次，不参与渲染。
 * 若用 mutableStateOf，`onGloballyPositioned` 会在**布局阶段**写状态、触发额外重组。
 */
private class CardGestureCoords {
    var topLeft: Offset = Offset.Zero
}

/**
 * 流水页。
 *
 * 结构：每天一个卡片（日期 + 当日小计 + 笔数），内部每笔一个子卡片。
 * 子卡片本身不再放编辑/删除图标 —— 点击进编辑页、长按弹删除确认，
 * 这样每行能空出来放分类与备注，也让"点进去改"成为唯一的心智模型。
 */
@Composable
fun LedgerScreen(
    transactions: List<TransactionEntity>,
    categories: List<Category>,
    metrics: List<MetricValue>,
    onEdit: (TransactionEntity, Offset) -> Unit,
    onLongPress: (TransactionEntity) -> Unit,
    /** 长按指标卡：直达「首页指标」设置。 */
    onLongPressMetric: () -> Unit,
) {
    val pathById = remember(categories) { categories.associate { it.id to it.path } }
    val dayGroups = remember(transactions, pathById) { buildDayGroups(transactions, pathById) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (metrics.isNotEmpty()) {
            item(key = "metrics") {
                MetricCardRow(metrics = metrics, onLongPressMetric = onLongPressMetric)
            }
        }

        if (dayGroups.isEmpty()) {
            item(key = "empty") { EmptyState() }
        }

        items(dayGroups, key = { it.startOfDay }) { group ->
            DayCard(
                group = group,
                onEdit = onEdit,
                onLongPress = onLongPress,
            )
        }
    }
}

/**
 * 指标卡网格：固定三列，行数随勾选数量增长。
 *
 * 没有用 LazyVerticalGrid —— 它不能嵌在 LazyColumn 里（两个可滚动容器套在一起，
 * 高度约束会变成无穷大而抛测量异常）。chunked(3) 手动分行既简单又避开这个坑：
 * 每行三个等宽（weight(1f)），最后一行不足三个用空 Spacer 补齐，
 * 这样卡片宽度不会因数量不同而跳变。
 *
 * 卡片组件与「首页指标」设置页共用，所以设置页里看到的就是首页真正的样子。
 */
@Composable
private fun MetricCardRow(metrics: List<MetricValue>, onLongPressMetric: () -> Unit) {
    val rows = metrics.chunked(3)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowMetrics.forEach { metric ->
                    MetricCard(
                        title = metric.displayName,
                        amountCents = metric.amountCents,
                        // 长按直达「首页指标」设置；单击无动作（首页的卡片是纯展示）
                        onLongClick = onLongPressMetric,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 补齐最后一行的空位，保证同一列的卡片宽度跨行一致
                repeat(3 - rowMetrics.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 一天一个卡片：头部是日期与小计，下面是当天的每一笔。
 *
 *  底色用 surfaceContainerLow 而不是 surface：后者在浅色主题下与页面 background
 *  几乎同色，卡片会"融"进背景。这两个本来就是不同层级，该用不同角色。 */
@Composable
private fun DayCard(
    group: DayGroup,
    onEdit: (TransactionEntity, Offset) -> Unit,
    onLongPress: (TransactionEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDay(group.startOfDay),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "支 ${formatAmount(group.totalCents)} · ${group.rows.size} 笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = subtleTextColor(),
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.rows.forEach { row ->
                    TxRowItem(
                        row = row,
                        onEdit = onEdit,
                        onLongPress = onLongPress,
                    )
                }
            }
        }
    }
}

/**
 * 单笔流水卡片。
 *
 * 点击 = 进入编辑页；长按 = 弹删除确认；揭示圆的圆心 = **手指点下去的位置**。
 *
 * 用一个 detectTapGestures 同时处理三种手势，而不是叠加 combinedClickable：
 * 试过 pointerInput(onPress 记录坐标) + combinedClickable(处理点击) 的组合，
 * 结果坐标始终是 (0,0) —— 两条链在同一个指针事件流里互相干扰，press 拿不到真实位置。
 * 单一手势源既简单又不会打架：
 * - onTap 自带坐标，直接用来算圆心；
 * - awaitRelease() 返回 null 表示手势被取消（滑动列表），此时不触发点击 ——
 *   这是让 LazyColumn 正常滚动的关键，否则手指一划就会误进编辑页。
 */
@Composable
private fun TxRowItem(
    row: TxRow,
    onEdit: (TransactionEntity, Offset) -> Unit,
    onLongPress: (TransactionEntity) -> Unit,
) {
    // 卡片在屏幕上的左上角，把"卡片内坐标"换算成"屏幕坐标"。
    // 用普通 holder 而不是 State：onGloballyPositioned 在布局阶段回调，
    // 在那里写 State 会触发额外重组，而这两个值只在手势回调里读一次。
    // 卡片在屏幕上的左上角，用来把"卡片内坐标"换算成"屏幕坐标"
    val coords = remember { CardGestureCoords() }
    // 长按的振动反馈。放在这里而不是 FlowtApp：振动属于手势的即时反馈，
    // 和手势识别在同一处更好维护，也不必为它往上层多传一个回调。
    val haptic = LocalHapticFeedback.current

    // 水波纹与手势是两层：indication 负责画，手势识别负责告诉它"什么时候按下了"。
    // clickable 是把这两层接好了才"自带涟漪"；这里手动接，所以两者都能要 ——
    // 既有水波纹，又能从 onTap 拿到点击坐标（圆心位置）。
    val interactionSource = remember { MutableInteractionSource() }
    // 涟漪颜色用 onPrimaryContainer：卡片底色正是 primaryContainer，这样对比才够
    val rippleColor = MaterialTheme.colorScheme.onPrimaryContainer
    val ripple = remember { ripple(color = rippleColor) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            // 与编辑页背景同一个颜色角色（primaryContainer）：
            // 点它进编辑页时，揭示的圆从这张卡片长出来，起点与终点颜色一致，过渡是连续的。
            .background(MaterialTheme.colorScheme.primaryContainer)
            .indication(interactionSource = interactionSource, indication = ripple)
            .onGloballyPositioned { layoutCoords ->
                val bounds = layoutCoords.boundsInRoot()
                coords.topLeft = Offset(bounds.left, bounds.top)
            }
            .pointerInput(row.entity.id) {
                detectTapGestures(
                    onPress = { downOffset ->
                        // 手动驱动涟漪：按下发 Press、抬手或取消时发对应的结束事件。
                        // InteractionSource 没有 emitPress 这类便捷方法，必须自行发 PressInteraction。
                        //
                        // 用 tryAwaitRelease() 而不是 awaitRelease()：
                        // 后者在手势被取消时会抛异常，前者返回 Boolean 告诉你结果，
                        // 因此也不需要 try/finally 来兜底。
                        // 手势取消（手指划走变成滚动）要发 Cancel，正常抬手才发 Release。
                        val press = PressInteraction.Press(downOffset)
                        interactionSource.emit(press)
                        val releasedNormally = tryAwaitRelease()
                        interactionSource.emit(
                            if (releasedNormally) {
                                PressInteraction.Release(press)
                            } else {
                                PressInteraction.Cancel(press)
                            }
                        )
                    },
                    onTap = { localOffset ->
                        onEdit(row.entity, coords.topLeft + localOffset)
                    },
                    onLongPress = {
                        // 先振动、再弹确认框：两个调用之间没有任何挂起或额外计算，
                        // 剩余的那点先后差是系统振动服务的起振时间，代码层面无法消除。
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress(row.entity)
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.categoryPath,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            // 时间与备注合成一行副标题：时间必显示，备注有才拼上
            val time = TIME_FORMAT.format(Date(row.entity.timestamp))
            val note = row.entity.note
            Text(
                text = if (note.isNullOrBlank()) time else "$time | $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
        Text(
            text = "-${formatAmount(row.entity.amountCents).removePrefix("¥")}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = expenseColor(),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "还没有任何流水",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点右下角的 + 记第一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = subtleTextColor(),
        )
    }
}

// --- 数据加工 ---

private fun buildDayGroups(
    transactions: List<TransactionEntity>,
    pathById: Map<Long, String>,
): List<DayGroup> {
    if (transactions.isEmpty()) return emptyList()
    return transactions
        // 时间倒序（DAO 已排序，这里防御性再排一次，避免上游改动导致乱序）
        .sortedByDescending { it.timestamp }
        .groupBy { startOfDay(it.timestamp) }
        .map { (dayStart, dayItems) ->
            DayGroup(
                startOfDay = dayStart,
                totalCents = dayItems.sumOf { it.amountCents },
                rows = dayItems.map { entity ->
                    TxRow(
                        entity = entity,
                        categoryPath = pathById[entity.categoryId] ?: "（分类已删除）",
                    )
                },
            )
        }
        .sortedByDescending { it.startOfDay }
}

private fun startOfDay(timeMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 日期标题：今天/昨天用文字，今年内用"M月d日 周几"，跨年用完整年月日。 */
private fun formatDay(startOfDay: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = startOfDay }
    val today = startOfDay(System.currentTimeMillis())
    val yesterday = today - 24L * 60 * 60 * 1000
    return when (startOfDay) {
        today -> "今天"
        yesterday -> "昨天"
        else -> {
            val inCurrentYear = cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
            val pattern = if (inCurrentYear) "M月d日 EEEE" else "yyyy年M月d日"
            SimpleDateFormat(pattern, Locale.CHINA).format(Date(startOfDay))
        }
    }
}
