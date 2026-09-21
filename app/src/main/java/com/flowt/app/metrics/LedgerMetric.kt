package com.flowt.app.metrics

import com.flowt.app.data.db.TransactionEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * 指标注册表。
 *
 * 扩展方式：在 [LedgerMetric.all] 里加一项即可 —— 设置页的勾选项、首页卡片、
 * 偏好存储都会自动跟上，不需要改任何界面代码。新的口径逻辑写在 `compute` 里。
 *
 * 约定：`compute` 返回 **null 表示"没有数据"**（界面显示 `—`），
 * 与"支出为 0 元"在视觉上区分开。
 */
class LedgerMetric(
    val id: String,
    val displayName: String,
    val compute: (List<TransactionEntity>, Long) -> Long?,
) {
    companion object {

        /** 一笔支出都没有时，所有指标都返回 null。 */
        private fun expenseSum(
            records: List<TransactionEntity>,
            rangeStart: Long,
            rangeEnd: Long,
        ): Long? {
            if (rangeStart > rangeEnd) return null
            val matched = records.filter {
                it.type == TransactionEntity.TYPE_EXPENSE &&
                    it.timestamp in rangeStart until rangeEnd
            }
            return if (matched.isEmpty()) null else matched.sumOf { it.amountCents }
        }

        private fun startOfToday(now: Long): Long = calendarAt(now) {
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        private fun startOfMonth(now: Long): Long = calendarAt(now) {
            it.set(Calendar.DAY_OF_MONTH, 1)
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        private fun startOfYear(now: Long): Long = calendarAt(now) {
            it.set(Calendar.DAY_OF_YEAR, 1)
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        private fun startOfMonthsAgo(now: Long, months: Int): Long = calendarAt(now) {
            it.add(Calendar.MONTH, -months)
            it.set(Calendar.DAY_OF_MONTH, 1)
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        private fun startOfYearsAgo(now: Long, years: Int): Long = calendarAt(now) {
            it.add(Calendar.YEAR, -years)
            it.set(Calendar.HOUR_OF_DAY, 0)
            it.set(Calendar.MINUTE, 0)
            it.set(Calendar.SECOND, 0)
            it.set(Calendar.MILLISECOND, 0)
        }

        /** 本地时区的 Calendar 构造：避免夏令时/时区导致"今天"算错。 */
        private fun calendarAt(timeMillis: Long, adjust: (Calendar) -> Unit): Long {
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.timeInMillis = timeMillis
            adjust(cal)
            return cal.timeInMillis
        }

        val all: List<LedgerMetric> = listOf(
            LedgerMetric("this_month", "本月支出") { records, now ->
                expenseSum(records, startOfMonth(now), Long.MAX_VALUE)
            },
            LedgerMetric("last_month", "上月支出") { records, now ->
                expenseSum(records, startOfMonthsAgo(now, 1), startOfMonth(now))
            },
            LedgerMetric("today", "今日支出") { records, now ->
                expenseSum(records, startOfToday(now), Long.MAX_VALUE)
            },
            LedgerMetric("yesterday", "昨日支出") { records, now ->
                expenseSum(records, startOfToday(now) - DAY_MILLIS, startOfToday(now))
            },
            LedgerMetric("this_year", "本年支出") { records, now ->
                expenseSum(records, startOfYear(now), Long.MAX_VALUE)
            },
            LedgerMetric("last_year", "最近一年支出") { records, now ->
                // "最近一年" = 滚动 12 个月，而不是"去年"；
                // 这样它永远是"过去一年"，不会在每年 1 月 1 日突然清零。
                expenseSum(records, startOfYearsAgo(now, 1), Long.MAX_VALUE)
            },
        )

        fun byId(id: String): LedgerMetric? = all.firstOrNull { it.id == id }

        fun defaultEnabledIds(): Set<String> = setOf("this_month", "last_month", "this_year")

        /** 默认不显示的指标，顺序沿用注册表的定义顺序。 */
        fun defaultDisabledIds(): List<String> =
            all.map { it.id }.filterNot { it in defaultEnabledIds() }

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
