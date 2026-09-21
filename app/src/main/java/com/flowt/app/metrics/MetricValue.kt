package com.flowt.app.metrics

import com.flowt.app.data.db.TransactionEntity

/** 一个指标在当前时刻的计算结果。 */
data class MetricValue(
    val id: String,
    val displayName: String,
    /** null 表示没有数据（界面显示 "—"），与"支出为 0 元"区分开。 */
    val amountCents: Long?,
) {
    companion object {

        /**
         * 按 [enabledIds] 给出的**顺序**计算指标 —— 顺序即首页卡片的排列顺序。
         * 未知 id 直接忽略（删掉某个指标后旧偏好不会崩）。
         */
        fun computeAll(
            records: List<TransactionEntity>,
            enabledIds: List<String>,
            now: Long = System.currentTimeMillis(),
        ): List<MetricValue> = enabledIds.mapNotNull { id ->
            LedgerMetric.byId(id)?.let { metric ->
                MetricValue(
                    id = metric.id,
                    displayName = metric.displayName,
                    amountCents = metric.compute(records, now),
                )
            }
        }

        /**
         * 计算注册表里的**全部**指标，与用户是否启用无关。
         * 设置页需要它：未显示区的卡片也要展示真实金额，
         * 否则把一个指标搬上搬下时数字会从 "—" 变成具体值，看着像在跳。
         */
        fun computeEveryMetric(
            records: List<TransactionEntity>,
            now: Long = System.currentTimeMillis(),
        ): List<MetricValue> = LedgerMetric.all.map { metric ->
            MetricValue(
                id = metric.id,
                displayName = metric.displayName,
                amountCents = metric.compute(records, now),
            )
        }
    }
}

/** 金额格式化为 "¥1,234.56"；null 显示为 "—"。 */
fun formatAmount(amountCents: Long?): String {
    if (amountCents == null) return "—"
    val negative = amountCents < 0
    val abs = kotlin.math.abs(amountCents)
    val yuan = abs / 100
    val cents = abs % 100
    val grouped = yuan.toString().reversed().chunked(3).joinToString(",").reversed()
    return buildString {
        if (negative) append('-')
        append('¥')
        append(grouped)
        append('.')
        append(cents.toString().padStart(2, '0'))
    }
}
