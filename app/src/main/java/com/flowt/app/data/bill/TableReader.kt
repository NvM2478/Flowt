package com.flowt.app.data.bill

/**
 * 账单文件 → 二维表格。这是**格式层**。
 *
 * 加一种文件格式（旧版 .xls、JSON 备份、剪贴板文本……）只需新增一个实现
 * 并在 [TableReaders.all] 里注册一行，之后的字段映射与落库完全不用动。
 */
interface TableReader {

    /** 按文件名判断能否处理。 */
    fun canRead(fileName: String): Boolean

    /** 读成「行 × 列」的文本表格。表头只是其中一行，怎么认由字段层决定。 */
    fun read(bytes: ByteArray): List<List<String>>
}

/** 文件读不出来（损坏、结构缺失、格式不支持）时抛这个；消息会直接展示给用户。 */
class BillFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object TableReaders {

    private val all: List<TableReader> = listOf(CsvTableReader(), XlsxTableReader())

    /**
     * 支持的后缀。MIME 类型在 Android 上不可靠（有的文件管理器给 .csv 的是
     * `application/octet-stream`），所以选完文件后一律按后缀再校验一次。
     */
    val supportedExtensions = setOf("csv", "tsv", "txt", "xlsx")

    fun byFileName(fileName: String): TableReader? = all.firstOrNull { it.canRead(fileName) }

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in supportedExtensions

    /** 给用户看的格式说明。 */
    const val SUPPORTED_HINT = "支持 .csv / .tsv / .xlsx"
}
