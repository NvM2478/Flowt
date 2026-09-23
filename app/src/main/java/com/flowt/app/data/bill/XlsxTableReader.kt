package com.flowt.app.data.bill

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * XLSX 读取。
 *
 * xlsx 本质是一个 zip，里面装着几个 XML。读一份账单只需要其中两样：
 * `xl/sharedStrings.xml`（字符串表）与第一个工作表的 XML。
 *
 * 刻意不引第三方库：Apache POI 在 Android 上不能直接用（缺 java.awt 与完整 StAX，
 * 且 poi-ooxml-schemas 单包就撞 65K 方法上限），各种 Android 移植版又都停在多年前、
 * 还要 multidex 与 2MB+ 体积。只读表格数据的话，标准库几十行比背一个依赖划算。
 *
 * 边界（会明确报错，不静默出错）：只支持 .xlsx，不支持 2003 版的 .xls；
 * 只读第一个工作表；单元格取缓存值，不计算公式。
 */
class XlsxTableReader : TableReader {

    override fun canRead(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").equals("xlsx", ignoreCase = true)

    override fun read(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        return try {
            val sharedStrings = parts.sharedStrings?.let(::parseSharedStrings).orEmpty()
            val sheet = parts.sheet
                ?: throw BillFormatException("这个 xlsx 里没有工作表，可能不是账单文件")
            parseSheet(sheet, sharedStrings)
        } catch (e: BillFormatException) {
            throw e
        } catch (e: Exception) {
            throw BillFormatException("xlsx 内部结构读取失败，文件可能已损坏", e)
        }
    }

    // --- zip ---

    private class Parts(val sharedStrings: ByteArray?, val sheet: ByteArray?)

    private fun unzip(bytes: ByteArray): Parts {
        var sharedStrings: ByteArray? = null
        var firstSheet: ByteArray? = null
        var anySheet: ByteArray? = null

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == SHARED_STRINGS -> sharedStrings = zip.readBytes()
                        entry.name == FIRST_SHEET -> firstSheet = zip.readBytes()
                        // ponytail: 不解析 workbook.xml + rels 去定位"第一个工作表"。
                        // 导出的账单都是单表，sheet1.xml 缺席时取任意一个也等价；
                        // 真遇到多表且顺序有讲究的文件，再补 rels 解析。
                        entry.name.startsWith("xl/worksheets/") &&
                            entry.name.endsWith(".xml") && anySheet == null -> anySheet = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            throw BillFormatException("不是有效的 xlsx，或文件已损坏", e)
        }

        return Parts(sharedStrings, firstSheet ?: anySheet)
    }

    // --- XML ---

    private fun parseXml(xml: ByteArray, handler: DefaultHandler) {
        // 不启用命名空间：xlsx 用的是默认命名空间，按 qName（"c" / "v" / "t"）匹配更直接
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    /** 字符串表。一个 `<si>` 是一个字符串，内部的多个 `<t>` 是富文本分片，要拼起来。 */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val buffer = StringBuilder()
        var inItem = false
        var inText = false

        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                when (qName) {
                    "si" -> {
                        inItem = true
                        buffer.setLength(0)
                    }

                    "t" -> if (inItem) inText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) buffer.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "t" -> inText = false
                    "si" -> {
                        strings.add(buffer.toString())
                        inItem = false
                    }
                }
            }
        })

        return strings
    }

    /**
     * 工作表。单元格的列位置取自 `r` 属性（"C5" → 第 3 列），
     * 因为 xlsx 会省略空单元格 —— 只按出现顺序追加会把列错位。
     */
    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        var cellType: String? = null
        var cellColumn = -1
        val value = StringBuilder()
        var inValue = false
        var inInlineText = false

        parseXml(xml, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                when (qName) {
                    "row" -> current = mutableListOf()

                    "c" -> {
                        cellType = attributes?.getValue("t")
                        cellColumn = columnIndex(attributes?.getValue("r").orEmpty())
                        value.setLength(0)
                        inValue = false
                        inInlineText = false
                    }

                    "v" -> inValue = true
                    "t" -> if (cellType == TYPE_INLINE) inInlineText = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue || inInlineText) value.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false

                    "c" -> {
                        val row = current
                        if (row != null && cellColumn >= 0) {
                            while (row.size <= cellColumn) row.add("")
                            row[cellColumn] = cellText(cellType, value.toString(), sharedStrings)
                        }
                        value.setLength(0)
                        cellType = null
                        cellColumn = -1
                    }

                    "row" -> {
                        current?.let { rows.add(it) }
                        current = null
                    }
                }
            }
        })

        return rows
    }

    private fun cellText(type: String?, raw: String, sharedStrings: List<String>): String = when (type) {
        TYPE_SHARED -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
        TYPE_INLINE, TYPE_STRING -> raw
        TYPE_BOOLEAN -> if (raw == "1") "TRUE" else "FALSE"
        // 数字；日期也是以序列号的形式躺在这里，换算交给字段层（BillParsing）
        else -> raw
    }

    /** "C5" → 2（0 基）。取不出列名时返回 -1，该单元格被忽略。 */
    private fun columnIndex(ref: String): Int {
        var value = 0
        var found = false
        for (ch in ref) {
            val digit = when (ch) {
                in 'A'..'Z' -> ch - 'A' + 1
                in 'a'..'z' -> ch - 'a' + 1
                else -> break
            }
            value = value * 26 + digit
            found = true
        }
        return if (found) value - 1 else -1
    }

    private companion object {
        const val SHARED_STRINGS = "xl/sharedStrings.xml"
        const val FIRST_SHEET = "xl/worksheets/sheet1.xml"

        const val TYPE_SHARED = "s"
        const val TYPE_INLINE = "inlineStr"
        const val TYPE_STRING = "str"
        const val TYPE_BOOLEAN = "b"
    }
}
