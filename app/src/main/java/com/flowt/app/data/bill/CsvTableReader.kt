package com.flowt.app.data.bill

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * CSV / TSV 读取。
 *
 * 两件"猜"的事都在这里做掉，因为导出的账单五花八门：
 * - **编码**：先看 BOM；没有 BOM 就严格地试 UTF-8，失败回退 GBK（国内软件导出常见）
 * - **分隔符**：逗号、制表符、分号里挑一个 —— Excel 另存为、从表格复制粘贴、
 *   各家软件自己导出，用的都不是同一种
 */
class CsvTableReader : TableReader {

    override fun canRead(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS

    override fun read(bytes: ByteArray): List<List<String>> {
        if (bytes.isEmpty()) return emptyList()
        val text = decode(bytes)
        return parse(text, detectDelimiter(text))
    }

    // --- 编码 ---

    private fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> {
            String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }

        bytes.startsWith(0xFF, 0xFE) -> {
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }

        bytes.startsWith(0xFE, 0xFF) -> {
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        else -> runCatching { strictDecode(bytes, StandardCharsets.UTF_8) }
            .recoverCatching { strictDecode(bytes, gbk()) }
            .getOrElse {
                throw BillFormatException("认不出这个文件的编码（既不是 UTF-8 也不是 GBK）", it)
            }
    }

    /**
     * 严格解码：遇到非法字节就抛异常，而不是悄悄替换成 "?"。
     * 这正是"是不是 UTF-8"的判据 —— GBK 的中文按 UTF-8 解几乎必然非法。
     */
    private fun strictDecode(bytes: ByteArray, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun gbk(): Charset =
        runCatching { Charset.forName("GBK") }.getOrDefault(StandardCharsets.UTF_8)

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }

    // --- 分隔符 ---

    /** 取前几行里出现次数最多的候选分隔符；一个都没有就按逗号处理。 */
    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(SAMPLE_LINES).joinToString("\n")
        return CANDIDATES
            .map { it to sample.count { c -> c == it } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
            ?: CANDIDATES.first()
    }

    // --- 逐字符解析 ---

    private fun parse(text: String, delimiter: Char): List<List<String>> {
        // 先把换行统一，后面的状态机就只用管 '\n'
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (i < normalized.length) {
            val c = normalized[i]
            when {
                // 引号内的 "" 表示一个字面引号
                inQuotes && c == '"' && i + 1 < normalized.length && normalized[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }

                inQuotes && c == '"' -> inQuotes = false
                // 引号内的分隔符与换行都属于字段内容
                inQuotes -> field.append(c)
                c == '"' -> inQuotes = true
                c == delimiter -> endField()
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }

        // 最后一行没有换行符结尾时补收一次
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        return rows
    }

    private companion object {
        val EXTENSIONS = setOf("csv", "tsv", "txt")
        val CANDIDATES = listOf(',', '\t', ';')
        const val SAMPLE_LINES = 5
    }
}
