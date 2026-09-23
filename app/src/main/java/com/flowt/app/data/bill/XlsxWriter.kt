package com.flowt.app.data.bill

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 二维表格 → XLSX 字节。**最小实现**：只生成 xlsx 必需的 5 个部件。
 *
 * 两个刻意的简化，都是为了"能读回来"这件事：
 * - 文本一律用 `inlineStr` 单元格，**不生成 sharedStrings.xml** —— 省掉字符串表
 *   这一整块复杂度，Excel 完全支持这种写法
 * - 日期**按字符串写**（`2026-09-04 13:40:09`）而不是日期序列号 + 格式：
 *   ISO 字符串在 Excel 里的排序结果与时间排序一致，而且这样写出去的内容能被
 *   我们自己的解析器读回来，导出→导入的闭环才成立
 */
object XlsxWriter {

    fun write(table: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putText(CONTENT_TYPES, contentTypes())
            zip.putText("_rels/.rels", rootRels())
            zip.putText("xl/workbook.xml", workbook())
            zip.putText("xl/_rels/workbook.xml.rels", workbookRels())
            zip.putText("xl/worksheets/sheet1.xml", sheet(table))
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.putText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // --- 部件 ---

    private fun contentTypes(): String = XML_DECLARATION +
        """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
        """<Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
        "</Types>"

    private fun rootRels(): String = XML_DECLARATION +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
        "</Relationships>"

    private fun workbook(): String = XML_DECLARATION +
        """<workbook xmlns="$MAIN_NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
        """<sheets><sheet name="$SHEET_NAME" sheetId="1" r:id="rId1"/></sheets>""" +
        "</workbook>"

    private fun workbookRels(): String = XML_DECLARATION +
        """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
        "</Relationships>"

    private fun sheet(table: List<List<String>>): String = buildString {
        append(XML_DECLARATION)
        append("""<worksheet xmlns="$MAIN_NS"><sheetData>""")

        table.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            append("""<row r="$rowNumber">""")
            row.forEachIndexed { columnIndex, value ->
                // 空单元格直接省略：xlsx 本来就允许缺格，靠 r 属性定位
                if (value.isEmpty()) return@forEachIndexed
                val ref = columnName(columnIndex) + rowNumber
                append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">""")
                append(escapeXml(value))
                append("</t></is></c>")
            }
            append("</row>")
        }

        append("</sheetData></worksheet>")
    }

    // --- 工具 ---

    /** 0 → A、25 → Z、26 → AA。 */
    fun columnName(index: Int): String {
        var remaining = index
        val builder = StringBuilder()
        while (true) {
            builder.append(('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return builder.reverse().toString()
    }

    /** XML 转义，并丢掉 XML 1.0 不允许的控制字符（从别处粘来的备注里可能有）。 */
    fun escapeXml(value: String): String = buildString {
        value.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\'' -> append("&apos;")
                // 除制表、换行、回车外，C0 控制字符在 XML 1.0 里是非法的
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private const val XML_DECLARATION = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val SHEET_NAME = "流水"
    private const val CONTENT_TYPES = "[Content_Types].xml"
}
