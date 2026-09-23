package com.flowt.app.data.bill

/**
 * 二维表格 → CSV 字节。
 *
 * **带 UTF-8 BOM**：不带的话，中文 Windows 上 Excel 双击打开就是乱码。
 * 这是"导出的备份能不能被用户直接看懂"的分水岭，不是可选项。
 */
object CsvWriter {

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun write(table: List<List<String>>): ByteArray {
        val text = table.joinToString("\r\n") { row ->
            row.joinToString(",") { escape(it) }
        }
        return BOM + text.toByteArray(Charsets.UTF_8)
    }

    /** 含逗号、引号或换行的字段要用双引号包起来，内部的引号翻倍。 */
    fun escape(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
