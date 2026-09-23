package com.flowt.app.data.bill

import com.flowt.app.data.CategoryRepository
import com.flowt.app.data.TxRepository
import com.flowt.app.data.db.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** 导出格式。MIME 给系统的保存对话框用。 */
enum class ExportFormat(
    val displayName: String,
    val hint: String,
    val extension: String,
    val mimeType: String,
) {
    Csv(
        displayName = "CSV",
        hint = "通用，任何工具都能读",
        extension = "csv",
        mimeType = "text/csv",
    ),
    Xlsx(
        displayName = "Excel",
        hint = "双击直接用 Excel 打开",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ),
}

/** 导出范围。自定义区间的毫秒值由 UI 用 [localDayStart] / [localDayEnd] 换算好。 */
sealed interface ExportRange {
    data object All : ExportRange

    data class Between(val startMillis: Long, val endMillis: Long) : ExportRange

    fun contains(timestamp: Long): Boolean = when (this) {
        All -> true
        is Between -> timestamp in startMillis..endMillis
    }
}

/** 分类在表格里的呈现方式。 */
enum class CategoryLayout(val displayName: String, val hint: String) {
    Expanded(displayName = "展开为多列", hint = "树有几层就展开几列，适合导给别的软件"),
    SinglePath(displayName = "单列完整路径", hint = "一列装「餐饮/外卖/午餐」，紧凑无损"),
}

/**
 * 导出：把流水组成表格，再按所选格式编码成字节。
 *
 * 列名与导入侧的同义词表**对齐**，所以导出的文件能直接再导入回来 ——
 * 换手机、重装应用后用它恢复，同时也天然证明了备份是完整的。
 */
class ExportService(
    private val txRepo: TxRepository,
    private val categoryRepo: CategoryRepository,
) {

    /** 某个范围内有多少笔，供设置面板直接标出"会导出多少"。 */
    suspend fun count(range: ExportRange): Int =
        txRepo.getAll().count { range.contains(it.timestamp) }

    suspend fun buildTable(range: ExportRange, layout: CategoryLayout): List<List<String>> {
        val transactions = txRepo.getAll().filter { range.contains(it.timestamp) }
        val pathById = categoryRepo.getAll().associate { it.id to it.path }
        return ExportTable.build(transactions, pathById, layout)
    }

    /** 文件名带上日期，免得同一天导两次分不清先后。 */
    fun defaultFileName(format: ExportFormat, now: Long = System.currentTimeMillis()): String {
        val date = SimpleDateFormat(FILE_DATE_PATTERN, Locale.CHINA).format(Date(now))
        return "Flowt备份_$date.${format.extension}"
    }

    fun encode(table: List<List<String>>, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.Csv -> CsvWriter.write(table)
        ExportFormat.Xlsx -> XlsxWriter.write(table)
    }

    private companion object {
        const val FILE_DATE_PATTERN = "yyyy-MM-dd"
    }
}

/**
 * 表格的组成逻辑。
 *
 * 做成纯函数（不碰数据库）是为了能脱离 Android 环境单测 ——
 * "导出的列名必须能被导入侧认回来"这条约束值得一个自动化检查。
 */
internal object ExportTable {

    fun build(
        transactions: List<TransactionEntity>,
        pathById: Map<Long, String>,
        layout: CategoryLayout,
    ): List<List<String>> {
        val paths = transactions.map { pathById[it.categoryId].orEmpty() }

        val depth = if (layout == CategoryLayout.Expanded) {
            paths.maxOfOrNull { it.split(PATH_SEPARATOR).size }?.coerceAtLeast(1) ?: 1
        } else {
            1
        }

        return buildList {
            add(header(layout, depth))
            transactions.forEachIndexed { index, entity ->
                add(row(entity, paths[index], layout, depth))
            }
        }
    }

    private fun header(layout: CategoryLayout, depth: Int): List<String> = buildList {
        add(BackupColumns.TIME)
        when (layout) {
            CategoryLayout.SinglePath -> add(BackupColumns.CATEGORY)
            // 树有几层就展开几列，与导入侧的动态层级识别对称
            CategoryLayout.Expanded -> repeat(depth) { add(BackupColumns.categoryLevel(it + 1)) }
        }
        add(BackupColumns.KIND)
        add(BackupColumns.NOTE)
        add(BackupColumns.AMOUNT)
    }

    private fun row(
        entity: TransactionEntity,
        path: String,
        layout: CategoryLayout,
        depth: Int,
    ): List<String> = buildList {
        add(formatTime(entity.timestamp))

        when (layout) {
            CategoryLayout.SinglePath -> add(path)
            CategoryLayout.Expanded -> {
                // 层级不足的行留空：四级树里的一条两级流水，后两列就是空的
                val segments = path.split(PATH_SEPARATOR)
                repeat(depth) { index -> add(segments.getOrElse(index) { "" }) }
            }
        }

        add(if (entity.type == TransactionEntity.TYPE_EXPENSE) LABEL_EXPENSE else LABEL_INCOME)
        add(entity.note.orEmpty())
        add(formatCents(entity.amountCents))
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat(TIME_PATTERN, Locale.CHINA).format(Date(timestamp))

    /** 分 → "150.98"。刻意不带货币符号，好让别的软件也能直接吃进去。 */
    private fun formatCents(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss"

    private const val LABEL_EXPENSE = "支出"
    private const val LABEL_INCOME = "收入"
}

/**
 * 日期选择器给的是"UTC 当天零点"，直接拿来算区间会让日期偏一天 ——
 * 这里换算成本地时区的当天起点。与记账页处理日期字段是同一个道理。
 */
fun localDayStart(pickedUtcMillis: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = pickedUtcMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            picked.get(Calendar.YEAR),
            picked.get(Calendar.MONTH),
            picked.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/** 本地时区的当天终点（23:59:59.999），保证"结束日当天"的记录也在范围内。 */
fun localDayEnd(pickedUtcMillis: Long): Long =
    localDayStart(pickedUtcMillis) + MILLIS_PER_DAY - 1

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
