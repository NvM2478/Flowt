package com.flowt.app.data.bill

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 账单字段的解析工具。全是纯函数 —— 不碰 Android API、不碰数据库，
 * 所以整层都能在 JVM 单测里跑，不用真机。
 */

// --- 金额 ---

/**
 * 金额文本 → 整数分；解析不出来返回 null。
 *
 * 用 BigDecimal 而不是 Double：金额绝不能用浮点（数据层的铁律），
 * 而 `movePointRight(2)` 恰好就是"元 → 分"，比手写字符串拆分可靠。
 *
 * 容忍各家的写法差异：`¥150.98`、`1,234.56`、`150.98元`、`-21.08`、`(150.98)`。
 */
fun parseAmountToCents(raw: String): Long? {
    var text = raw.trim()
    if (text.isEmpty()) return null

    var negative = false
    // 括号是财务软件里常见的负数写法
    if (text.startsWith("(") && text.endsWith(")")) {
        negative = true
        text = text.substring(1, text.length - 1)
    }

    text = text
        .replace("¥", "").replace("￥", "").replace("$", "")
        .replace(",", "").replace("，", "")
        .replace("元", "")
        .trim()

    when {
        text.startsWith("-") -> {
            negative = !negative
            text = text.substring(1).trim()
        }

        text.startsWith("+") -> text = text.substring(1).trim()
    }
    if (text.isEmpty()) return null

    return runCatching {
        val cents = BigDecimal(text)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        if (negative) -cents else cents
    }.getOrNull()
}

// --- 时间 ---

/**
 * 时间文本 → epoch 毫秒（本地时区）；解析不出来返回 null。
 *
 * 只有日期没有时间时取当天 00:00。xlsx 里以数字形式存在的日期（序列号）
 * 也能认出来，见 [xlsxSerialToMillis]。
 */
fun parseTimestamp(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    for (pattern in DATE_PATTERNS) {
        tryParse(text, pattern)?.let { return it.time }
    }

    // 纯数字且落在合理范围内 → 当作 xlsx 的日期序列号
    val serial = text.toDoubleOrNull()
    if (serial != null && serial > 0 && serial < MAX_SERIAL) return xlsxSerialToMillis(serial)

    return null
}

/**
 * 按某个格式解析，且要求**整串都被消费掉**。
 *
 * SimpleDateFormat 默认是前缀匹配：用 `yyyyMMdd` 去解析 `20260904134009`
 * 会安静地得出 2026-09-04 并丢掉后面六位。必须自己检查 ParsePosition。
 */
private fun tryParse(text: String, pattern: String): java.util.Date? {
    // 刻意每次新建：SimpleDateFormat 不是线程安全的，共享实例在并发导入时会串味
    val format = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = format.parse(text, position) ?: return null
    return if (position.index == text.length) parsed else null
}

/**
 * xlsx 日期序列号 → epoch 毫秒。
 *
 * 基准是 1899-12-30（Excel 把 1900 年当成闰年的历史 bug 已经含在 25569 这个偏移里）。
 * 序列号表达的是"本地墙上时间"，所以最后要减掉时区偏移 —— 直接用 UTC 会整体偏几个小时。
 */
fun xlsxSerialToMillis(serial: Double): Long {
    val wallClock = ((serial - EXCEL_EPOCH_SERIAL) * MILLIS_PER_DAY).toLong()
    return wallClock - TimeZone.getDefault().getOffset(wallClock)
}

// --- 列名 ---

/**
 * 列名归一化：去空格、全角转半角、丢掉括号里的内容（通常是单位）、统一小写。
 *
 * 「金额(元)」「金额（元）」「 金额 」都会归到「金额」，所以同义词表里
 * 只需要写一种写法。
 */
fun normalizeHeader(raw: String): String {
    val builder = StringBuilder()
    var depth = 0
    for (ch in raw.trim()) {
        when {
            ch == '(' || ch == '（' -> depth++
            ch == ')' || ch == '）' -> if (depth > 0) depth--
            depth > 0 -> Unit
            ch.isWhitespace() -> Unit
            else -> builder.append(ch.toHalfWidth())
        }
    }
    return builder.toString().lowercase()
}

private fun Char.toHalfWidth(): Char = when {
    this == '　' -> ' '
    this in '！'..'～' -> (code - 0xFEE0).toChar()
    else -> this
}

/**
 * 识别一个表头是不是分类列、是第几级；不是分类列返回 null。
 *
 * 分类列可以有多列、层数不定（树有几层就有几列），所以它不在 [ImportField]
 * 枚举里，靠这个函数动态认出来。认「一级分类」「分类2」「三级类别」这些写法，
 * 于是自己导出的任意深度文件都读得回来 —— 闭环在任意层级下都成立。
 */
fun categoryLevelOf(header: String): Int? {
    val name = normalizeHeader(header)
    if (name.isEmpty()) return null

    LEVEL1_NAMES[name]?.let { return it }
    LEVEL2_NAMES[name]?.let { return it }

    // 「三级分类」「3级类别」
    PREFIX_LEVEL.find(name)?.let { match ->
        return cnNumberToInt(match.groupValues[1])
    }
    // 「分类3」「类别二」
    SUFFIX_LEVEL.find(name)?.let { match ->
        return cnNumberToInt(match.groupValues[2])
    }
    return null
}

/** 中文数字 → 整数；认不出来返回 null。只做到"十"级别，分类树不会更深。 */
fun cnNumberToInt(text: String): Int? {
    text.toIntOrNull()?.let { return it }
    if (text.isEmpty()) return null
    if (text.length == 1) return CN_DIGITS[text[0]]

    val tenIndex = text.indexOf('十')
    if (tenIndex < 0) return null
    val tens = if (tenIndex == 0) 1 else CN_DIGITS[text[tenIndex - 1]] ?: return null
    val ones = if (tenIndex == text.length - 1) 0 else CN_DIGITS[text[tenIndex + 1]] ?: return null
    return tens * 10 + ones
}

/** 按同义词表自动匹配单值字段。一列只归一个字段，先到先得。 */
fun autoBindFields(headers: List<String>): Map<ImportField, Int> {
    val normalized = headers.map(::normalizeHeader)
    val taken = mutableSetOf<Int>()
    val result = mutableMapOf<ImportField, Int>()

    ImportField.entries.forEach { field ->
        val synonyms = field.synonyms.map(::normalizeHeader).toSet()
        val index = normalized.indices.firstOrNull { it !in taken && normalized[it] in synonyms }
        if (index != null) {
            result[field] = index
            taken += index
        }
    }
    return result
}

/** 自动识别分类列，按层级排好序：第 i 项就是第 i+1 级分类所在的列。 */
fun autoBindCategories(headers: List<String>): List<Int> {
    val byLevel = mutableMapOf<Int, Int>()
    headers.forEachIndexed { index, header ->
        val level = categoryLevelOf(header) ?: return@forEachIndexed
        // 同一层级出现多列时取最先出现的那列
        byLevel.putIfAbsent(level, index)
    }
    return byLevel.entries.sortedBy { it.key }.map { it.value }
}

/** 按分类列拼出路径；各列都为空时返回 null。 */
fun buildCategoryPath(columns: List<Int>, row: List<String>): String? {
    val parts = columns.mapNotNull { index ->
        row.getOrElse(index) { "" }.trim().takeIf { it.isNotEmpty() }
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(PATH_SEPARATOR)
}

/**
 * 按字段类型抽样校验绑定：金额列要能解析出钱、时间列要能解析出日期。
 *
 * 只按列名配对是不够的 —— 用户手滑把「金额」指到日期列，整批数据就成了垃圾。
 * 抽样而非全量：前 20 行足够判断绑没绑错，几千行全试不值当。
 */
fun checkBinding(binding: ColumnBinding, dataRows: List<List<String>>): BindingCheck {
    if (binding.missingRequired.isNotEmpty()) return BindingCheck()

    val amountColumn = binding.single[ImportField.Amount] ?: return BindingCheck()
    val timestampColumn = binding.single[ImportField.Timestamp] ?: return BindingCheck()

    val sample = dataRows.asSequence()
        .filter { row -> row.any { it.isNotBlank() } }
        .take(CHECK_SAMPLE_SIZE)
        .toList()
    if (sample.isEmpty()) return BindingCheck()

    var amountFailures = 0
    var timestampFailures = 0
    sample.forEach { row ->
        if (parseAmountToCents(row.getOrElse(amountColumn) { "" }) == null) amountFailures++
        if (parseTimestamp(row.getOrElse(timestampColumn) { "" }) == null) timestampFailures++
    }

    return BindingCheck(
        sampled = sample.size,
        amountFailures = amountFailures,
        timestampFailures = timestampFailures,
    )
}

// --- 自家备份的列名与识别 ---

/**
 * 导出时写出去的列名，同时也是「自家备份」的识别依据。
 *
 * 两侧共用同一组常量、而不是各写一份字符串 —— 这正是"导出→导入闭环"成立的前提：
 * 将来改了导出列名，识别会跟着改，不会出现"改了一边、另一边悄悄失效"。
 *
 * 四种导出组合（CSV/XLSX × 多列/单列）都由 [looksLikeFlowtBackup] 覆盖。
 */
object BackupColumns {
    const val TIME = "账单日期"
    const val CATEGORY = "记账分类"
    const val KIND = "收支类型"
    const val NOTE = "备注"
    const val AMOUNT = "金额"

    /** 多列形式下第 [level] 级分类的列名（从 1 起）：「一级分类」「二级分类」…… */
    fun categoryLevel(level: Int): String = "${cnNumber(level)}级分类"
}

/**
 * 认出"这是 Flowt 自己导出的文件"。
 *
 * 判据只看**表头名**，所以与文件格式、与分类列形式都无关：CSV 与 XLSX 共用同一套列名；
 * 多列形式有「一级分类」、单列形式有「记账分类」，两种都命中。
 */
fun looksLikeFlowtBackup(headers: List<String>): Boolean {
    val normalized = headers.map(::normalizeHeader).toSet()
    val hasTime = normalizeHeader(BackupColumns.TIME) in normalized
    val hasAmount = normalizeHeader(BackupColumns.AMOUNT) in normalized
    val hasCategory = normalizeHeader(BackupColumns.CATEGORY) in normalized ||
        normalizeHeader(BackupColumns.categoryLevel(1)) in normalized
    return hasTime && hasAmount && hasCategory
}

/** 1 → 「一」、11 → 「11」。分类层级到十级足够，更深的一律用阿拉伯数字。 */
fun cnNumber(value: Int): String =
    if (value in 1..10) CN_NUMBERS[value - 1] else value.toString()

// --- 常量 ---

/** 分类路径的分隔符；分类名里禁止出现它（[com.flowt.app.data.CategoryRepository] 保证）。 */
const val PATH_SEPARATOR = "/"

private const val CHECK_SAMPLE_SIZE = 20
private const val MILLIS_PER_DAY = 86_400_000.0
private const val EXCEL_EPOCH_SERIAL = 25569.0

/** 序列号上界：2119 年左右。再大的数字不可能是日期，多半是金额被误绑到了时间列。 */
private const val MAX_SERIAL = 80_000.0

/** 从长到短排列：先试带秒的，再试只有分钟的，最后试只有日期的。 */
private val DATE_PATTERNS = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd HH:mm",
    "yyyy-MM-dd",
    "yyyy/M/d HH:mm:ss",
    "yyyy/M/d HH:mm",
    "yyyy/M/d",
    "yyyy.M.d HH:mm:ss",
    "yyyy.M.d",
    "yyyy年M月d日 HH:mm:ss",
    "yyyy年M月d日 HH:mm",
    "yyyy年M月d日",
    "yyyyMMddHHmmss",
    "yyyyMMdd",
)

/** 一级分类的常见叫法。 */
private val LEVEL1_NAMES = mapOf(
    "分类" to 1, "记账分类" to 1, "账单分类" to 1, "一级分类" to 1,
    "类别" to 1, "消费分类" to 1, "类目" to 1,
)

/** 二级分类的常见叫法。 */
private val LEVEL2_NAMES = mapOf(
    "分类子类" to 2, "子分类" to 2, "二级分类" to 2,
    "类别2" to 2, "子类别" to 2, "二级类别" to 2,
)

private val PREFIX_LEVEL = Regex("""(\d+|[一二三四五六七八九十]+)级?(?:分类|类别)""")
private val SUFFIX_LEVEL = Regex("""(分类|类别)(\d+|[一二三四五六七八九十]+)""")

private val CN_DIGITS = mapOf(
    '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5,
    '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
)

/** 数字 → 中文，给导出的层级列名用（「一级分类」）。与 [CN_DIGITS] 互为反向。 */
private val CN_NUMBERS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
