package com.flowt.app.data.bill

import com.flowt.app.data.ImportedFileRecord

/**
 * 导入模式 —— **由用户在数据管理页选的入口决定**，不靠猜文件内容。
 *
 * 这一层是"对接其他数据结构"的扩展落点：将来要支持某款软件的标准格式
 * （列名固定、分类映射已知），来源列表加一行 + 这里加一个模式即可，
 * 导入流程与落库逻辑都不用动。
 */
enum class ImportMode {
    /** 外部账单：列绑定 + 分类去向两道确认都要过。 */
    ExternalBill,

    /** 自家备份：列名与分类路径都是我们自己写出去的，两道确认都免了。 */
    SelfBackup,
}

/**
 * 可绑定的单值字段。
 *
 * 同义词表是**"软件差异"的唯一落点**：换一款记账软件，多数情况下只是往这里
 * 补几个词，解析逻辑一行都不用改。
 *
 * 分类列不在这里 —— 它可能有多列、层数不定（树有几层就有几列），
 * 单独用 [ColumnBinding.categories] 表达。
 */
enum class ImportField(val displayName: String, val synonyms: List<String>) {
    Timestamp(
        displayName = "时间",
        synonyms = listOf("账单日期", "日期", "交易时间", "交易日期", "记账日期", "发生时间"),
    ),
    Amount(
        displayName = "金额",
        synonyms = listOf("金额", "交易金额", "金额(元)", "发生额", "收支金额"),
    ),
    Note(
        displayName = "备注",
        synonyms = listOf("备注", "说明", "交易说明", "商品说明"),
    ),
    Kind(
        displayName = "收支类型",
        synonyms = listOf("收支类型", "收支", "类型", "收/支"),
    ),
}

/**
 * 一次导入的列绑定：字段 → 列下标。
 *
 * [categories] 的第 i 项是第 i+1 级分类所在的列，长度即这份账单的分类层数。
 * 用列表而不是 Map，是因为层级顺序本身就是语义。
 */
data class ColumnBinding(
    val single: Map<ImportField, Int> = emptyMap(),
    val categories: List<Int> = emptyList(),
) {
    /** 时间与金额缺一不可，否则这份账单没法变成流水。 */
    val missingRequired: List<ImportField>
        get() = REQUIRED.filterNot { it in single }

    companion object {
        val REQUIRED = listOf(ImportField.Timestamp, ImportField.Amount)
    }
}

/** 与来源软件无关的一行。未识别的列原样进 [extras]，不丢数据也不阻断导入。 */
data class ImportedRow(
    val timestamp: Long,
    val amountCents: Long,
    /** V1 恒为 expense；收入行会被跳过，但先把信息留着。 */
    val type: String,
    /** "住房/电费"；单列账单里的 "餐饮/午餐" 也会被拆成路径。 */
    val categoryPath: String?,
    val note: String?,
    val extras: Map<String, String> = emptyMap(),
)

/**
 * 预览用的一行：流水本身，加上它在这次导入里**实际会落到**哪个分类。
 *
 * 用户可以把某个账单分类改指到别的已有分类，所以只显示原始路径会与实际结果对不上。
 */
data class PreviewRow(
    val row: ImportedRow,
    val targetPath: String,
)

/** 一行没能解析出来：给用户看行号与原文，好定位是哪一行。 */
data class InvalidRow(
    /** 文件里的行号，从 1 开始（含表头与前置说明行），与用户在 Excel 里看到的一致。 */
    val lineNumber: Int,
    val raw: List<String>,
    val reason: String,
)

/**
 * 绑定类型校验的结果。
 *
 * 只按列名配对是不够的：用户手滑把"金额"指到日期列，整个导入会变成一堆垃圾。
 * 所以拿绑定去试着解析前若干行，按字段类型看失败多少。
 */
data class BindingCheck(
    val sampled: Int = 0,
    val amountFailures: Int = 0,
    val timestampFailures: Int = 0,
) {
    /** 金额列整列都解析不出钱 = 绑错了，此时应当拦住导入。 */
    val amountAllFailed: Boolean get() = sampled > 0 && amountFailures == sampled

    val timestampAllFailed: Boolean get() = sampled > 0 && timestampFailures == sampled

    val amountHasFailures: Boolean get() = amountFailures > 0
    val timestampHasFailures: Boolean get() = timestampFailures > 0
}

/** 解析一张表的结果。改绑定时用它重算，源表格一直在内存里。 */
data class ParsedBill(
    /** 表头所在行号（1 基），有些账单前面有说明行，不是从第 1 行开始。 */
    val headerLineNumber: Int,
    val headers: List<String>,
    /** 表头之后的全部数据行（原始单元格）。 */
    val dataRows: List<List<String>>,
    /** 数据行在文件里的起始行号，用于回推每一行的真实行号。 */
    val dataStartLineNumber: Int,
    val binding: ColumnBinding,
    val check: BindingCheck,
    val rows: List<ImportedRow>,
    val skippedIncome: Int,
    val invalidRows: List<InvalidRow>,
    /** 文件内完全相同的记录组数（组内重复会保留，这里只是让用户知情）。 */
    val duplicateGroupsInFile: Int,
)

/** 一条账单分类的去向。 */
data class CategoryMapping(
    /** 账单里的分类路径，如 "住房/电费"。 */
    val sourcePath: String,
    val target: MappingTarget,
    /** 该分类下有多少笔流水，让用户知道改它的影响面。 */
    val rowCount: Int,
)

sealed interface MappingTarget {
    /** 绑到已有分类。 */
    data class Existing(val categoryId: Long, val path: String) : MappingTarget

    /** 本地没有同名分类，导入时按路径逐级创建。 */
    data object Create : MappingTarget

    /** 这个分类下的流水不导入。 */
    data object Skip : MappingTarget
}

/** 预览页要展示的全部内容。 */
data class ImportPlan(
    val mode: ImportMode,
    val fileName: String,
    val fileHash: String,
    val parsed: ParsedBill,
    val mappings: List<CategoryMapping>,
    /**
     * 与库里已有记录重复、将被跳过的**行下标**（导入前就算出来）。
     *
     * 存下标而不只是数量，是因为预览区要把这些行排除掉 —— 只报"会跳过 3 笔"
     * 而预览里还摆着那 3 笔，用户会以为它们会被导入。
     */
    val duplicateRowIndexes: Set<Int> = emptySet(),
    /** 这个文件此前导入过的话，给出那条记录。 */
    val previousImport: ImportedFileRecord?,
) {
    /** 与已有记录重复的笔数。 */
    val duplicateWithExisting: Int get() = duplicateRowIndexes.size

    /**
     * 真正会导入的行下标：排除与已有记录重复的、以及分类去向被设为「跳过」的。
     *
     * 摘要里的"可导入 N 笔"、按钮文案、预览、分类去向列表都从这一处出发 ——
     * 各自分头算，早晚会在边界上对不上（比如某个分类既被跳过、其流水又重复时，
     * 简单相减会把同一笔扣两次）。
     */
    private fun importableIndexes(): List<Int> {
        val skippedPaths = mappings.asSequence()
            .filter { it.target is MappingTarget.Skip }
            .map { it.sourcePath }
            .toSet()

        return parsed.rows.indices.filter { index ->
            index !in duplicateRowIndexes &&
                (parsed.rows[index].categoryPath ?: UNCATEGORIZED_PATH) !in skippedPaths
        }
    }

    /** 按当前分类去向，这次实际会导入多少笔。 */
    val importableCount: Int get() = importableIndexes().size

    /**
     * 预览区展示的行：只取**真正会导入**的，按时间倒序（最新的在前）。
     *
     * 预览的意义是"让你看见即将发生什么"，所以两点都要做到：不会进来的行不摆出来，
     * 摆出来的行显示它**实际会落到哪个分类**（用户改过去向就不该再显示原始路径）。
     */
    fun previewRows(limit: Int = PREVIEW_LIMIT): List<PreviewRow> {
        val targetPathOf = mappings.associate { mapping ->
            mapping.sourcePath to when (val target = mapping.target) {
                is MappingTarget.Existing -> target.path
                // 新建的仍用原路径，跳过的行压根不会出现在预览里
                MappingTarget.Create, MappingTarget.Skip -> mapping.sourcePath
            }
        }

        return importableIndexes()
            .map { parsed.rows[it] }
            .sortedByDescending { it.timestamp }
            .take(limit)
            .map { row ->
                val sourcePath = row.categoryPath ?: UNCATEGORIZED_PATH
                PreviewRow(row = row, targetPath = targetPathOf[sourcePath] ?: sourcePath)
            }
    }

    /**
     * 分类去向里值得列出来的分类：至少还有一条流水**没被判重跳过**的那些。
     *
     * 一个分类下的流水若全是重复的，它选"新建"还是"跳过"结果都一样，摆出来只是让列表变长。
     *
     * 注意基准是"没被判重"，而**不是** [importableIndexes]（那还会排除被跳过的分类）——
     * 否则用户一把某分类设为跳过，它就从列表里消失，再也改不回来了。
     */
    fun visibleMappings(): List<CategoryMapping> {
        val effectivePaths = parsed.rows.indices
            .filter { it !in duplicateRowIndexes }
            .mapTo(mutableSetOf()) { parsed.rows[it].categoryPath ?: UNCATEGORIZED_PATH }
        return mappings.filter { it.sourcePath in effectivePaths }
    }

    companion object {
        const val PREVIEW_LIMIT = 10
    }
}

/** 一次导入的结果，结果页据此显示统计。 */
data class ImportOutcome(
    val imported: Int,
    val skippedDuplicate: Int,
    val skippedIncome: Int,
    val skippedInvalid: Int,
    val skippedByMapping: Int,
    val createdCategories: Int,
)

/**
 * 撤销上次导入的结果。
 *
 * 撤销的入口在数据管理页、不在导入流程里，所以它**不依赖** [ImportOutcome] ——
 * 用户可以在导入几天之后、看过实际效果再回来撤销。
 */
data class UndoResult(
    val fileName: String,
    val importedAt: Long,
    /** 实际删掉的笔数：用户可能已经手动删过其中几条，所以不一定等于当初导入的条数。 */
    val deleted: Int,
)
