package com.flowt.app.data.bill

import com.flowt.app.data.db.TransactionEntity
import kotlin.math.abs

/**
 * 二维表格 → [ImportedRow]。这是**字段层**。
 *
 * 与文件格式无关，也与账单来自哪款软件无关 —— 软件之间的差异全部收敛在
 * `ImportField.synonyms` 那张同义词表里，这里的逻辑对谁都一样。
 */
object BillTable {

    /** 表头最多往下找这么多行：有的账单前面是导出时间、账户名之类的说明。 */
    private const val HEADER_SEARCH_LIMIT = 20

    /**
     * 定位表头行。
     *
     * 判据是"这一行同时能认出时间列与金额列"，而不是"第一行" ——
     * 导出的账单常带前置说明行，写死第一行会把说明行当表头。
     */
    fun locateHeader(rows: List<List<String>>): Int {
        val limit = minOf(rows.size, HEADER_SEARCH_LIMIT)
        for (index in 0 until limit) {
            val fields = autoBindFields(rows[index])
            if (ImportField.Timestamp in fields && ImportField.Amount in fields) return index
        }
        // 认不出来就退回第一个非空行，由用户在预览页手动指定列
        return rows.indexOfFirst { row -> row.any { it.isNotBlank() } }.coerceAtLeast(0)
    }

    /**
     * 解析整张表。
     *
     * [binding] 传 null 表示按同义词表自动识别；用户在预览页改了绑定后，
     * 把新绑定传进来重算即可 —— 源表格一直在内存里，几千行也是毫秒级。
     */
    fun parse(
        rows: List<List<String>>,
        binding: ColumnBinding? = null,
    ): ParsedBill {
        if (rows.isEmpty()) throw BillFormatException("这个文件里没有任何内容")

        val headerIndex = locateHeader(rows)
        val headers = rows[headerIndex]
        val dataRows = rows.drop(headerIndex + 1)

        val resolved = binding ?: ColumnBinding(
            single = autoBindFields(headers),
            categories = autoBindCategories(headers),
        )
        val check = checkBinding(resolved, dataRows)

        val headerLineNumber = headerIndex + 1
        val dataStartLineNumber = headerIndex + 2

        val timestampColumn = resolved.single[ImportField.Timestamp]
        val amountColumn = resolved.single[ImportField.Amount]

        // 必需列没认出来时不报错退出：把空结果交回去，让预览页提示用户手动指定
        if (timestampColumn == null || amountColumn == null) {
            return ParsedBill(
                headerLineNumber = headerLineNumber,
                headers = headers,
                dataRows = dataRows,
                dataStartLineNumber = dataStartLineNumber,
                binding = resolved,
                check = BindingCheck(),
                rows = emptyList(),
                skippedIncome = 0,
                invalidRows = emptyList(),
                duplicateGroupsInFile = 0,
            )
        }

        val kindColumn = resolved.single[ImportField.Kind]
        val noteColumn = resolved.single[ImportField.Note]

        // 没被任何字段占用的列 → 原样留进 extras，将来实体加字段时直接接上
        val boundColumns = resolved.single.values.toSet() + resolved.categories.toSet()
        val extraColumns = headers.indices.filter { it !in boundColumns }

        val parsedRows = mutableListOf<ImportedRow>()
        val invalidRows = mutableListOf<InvalidRow>()
        var skippedIncome = 0

        dataRows.forEachIndexed { offset, row ->
            val lineNumber = dataStartLineNumber + offset
            // 空行（整行都空）直接跳过，不算解析失败 —— 账单末尾常有空行
            if (row.all { it.isBlank() }) return@forEachIndexed

            val amountCents = parseAmountToCents(row.getOrElse(amountColumn) { "" })
            val timestamp = parseTimestamp(row.getOrElse(timestampColumn) { "" })

            if (amountCents == null || timestamp == null) {
                invalidRows += InvalidRow(
                    lineNumber = lineNumber,
                    raw = row,
                    reason = when {
                        timestamp == null && amountCents == null -> "时间和金额都认不出来"
                        timestamp == null -> "时间「${row.getOrElse(timestampColumn) { "" }}」认不出来"
                        else -> "金额「${row.getOrElse(amountColumn) { "" }}」认不出来"
                    },
                )
                return@forEachIndexed
            }

            // V1 只记支出，收入行跳过并统计（type 字段留着，将来放开即可）
            if (kindColumn != null && isIncome(row.getOrElse(kindColumn) { "" })) {
                skippedIncome++
                return@forEachIndexed
            }

            val note = noteColumn?.let { index ->
                row.getOrElse(index) { "" }.trim().takeIf { it.isNotEmpty() }
            }

            val extras = extraColumns.mapNotNull { index ->
                val header = headers.getOrNull(index)?.trim().orEmpty()
                val value = row.getOrElse(index) { "" }.trim()
                if (header.isEmpty() || value.isEmpty()) null else header to value
            }.toMap()

            parsedRows += ImportedRow(
                timestamp = timestamp,
                // 金额恒存正数，方向由收支类型决定
                amountCents = abs(amountCents),
                type = TransactionEntity.TYPE_EXPENSE,
                categoryPath = buildCategoryPath(resolved.categories, row),
                note = note,
                extras = extras,
            )
        }

        return ParsedBill(
            headerLineNumber = headerLineNumber,
            headers = headers,
            dataRows = dataRows,
            dataStartLineNumber = dataStartLineNumber,
            binding = resolved,
            check = check,
            rows = parsedRows,
            skippedIncome = skippedIncome,
            invalidRows = invalidRows,
            duplicateGroupsInFile = countDuplicateGroups(parsedRows),
        )
    }

    /**
     * 文件内完全相同的记录有几组。
     *
     * 这些**不会**被去掉（文件里两行一样，更可能是用户真的消费了两次），
     * 只是让用户在预览页知情。
     */
    private fun countDuplicateGroups(rows: List<ImportedRow>): Int =
        rows.groupingBy { listOf(it.timestamp, it.amountCents, it.categoryPath, it.note) }
            .eachCount()
            .count { it.value > 1 }

    /** 收支方向。认不出来时按支出处理 —— V1 接的账单绝大多数是支出。 */
    private fun isIncome(raw: String): Boolean {
        val text = normalizeHeader(raw)
        if (text.isEmpty()) return false
        return text.contains("收入") ||
            text.contains("income") ||
            (text.contains("收") && !text.contains("支"))
    }
}
