package com.flowt.app.ui.report

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.metrics.formatAmount
import com.flowt.app.ui.theme.FlowtColors
import com.flowt.app.ui.theme.subtleTextColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class CategoryTotal(
    val path: String,
    val amountCents: Long,
    val ratio: Float,
)

/**
 * 报表页（V1 雏形）。
 *
 * 只做"本月合计 + 分类占比"这一件事 —— 图表、趋势、预算都不在 V1 范围内。
 * 但需要**额外传入分类列表**，因为只统计 amountCents 没法知道钱花在什么上。
 */
@Composable
fun ReportScreen(
    transactions: List<TransactionEntity>,
    categories: List<Category>,
) {
    val monthStart = remember { startOfThisMonth() }
    val monthLabel = remember { SimpleDateFormat("yyyy年M月", Locale.CHINA).format(monthStart) }
    val pathById = remember(categories) { categories.associate { it.id to it.path } }

    val monthTransactions = remember(transactions, monthStart) {
        transactions.filter { it.timestamp >= monthStart }
    }
    val total = monthTransactions.sumOf { it.amountCents }
    val byCategory = remember(monthTransactions, pathById, total) {
        monthTransactions
            .groupBy { pathById[it.categoryId] ?: "（未分类）" }
            .map { (path, items) ->
                val sum = items.sumOf { it.amountCents }
                CategoryTotal(
                    path = path,
                    amountCents = sum,
                    ratio = if (total > 0) sum.toFloat() / total else 0f,
                )
            }
            .sortedByDescending { it.amountCents }
    }

    // 按分类名长度简单缩进，体现层级（完整路径用 / 分隔）
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = FlowtColors.current.metricCardBackground,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$monthLabel 支出",
                        style = MaterialTheme.typography.labelLarge,
                        color = FlowtColors.current.metricCardText,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatAmount(total.takeIf { monthTransactions.isNotEmpty() }),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FlowtColors.current.metricCardText,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${monthTransactions.size} 笔",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlowtColors.current.metricCardText,
                    )
                }
            }
        }

        if (byCategory.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "本月还没有流水",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtleTextColor(),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            item(key = "title") {
                Text(
                    text = "分类占比",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(byCategory.size, key = { byCategory[it].path }) { index ->
                CategoryRow(byCategory[index])
            }
        }
    }
}

@Composable
private fun CategoryRow(item: CategoryTotal) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.path, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatAmount(item.amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.ratio },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(item.ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = subtleTextColor(),
            )
        }
    }
}

private fun startOfThisMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
