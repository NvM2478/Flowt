package com.flowt.app.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.ClearResult
import com.flowt.app.data.ImportedFileRecord
import com.flowt.app.data.bill.CategoryLayout
import com.flowt.app.data.bill.ExportFormat
import com.flowt.app.data.bill.ExportRange
import com.flowt.app.data.bill.ImportMode
import com.flowt.app.data.bill.localDayEnd
import com.flowt.app.data.bill.localDayStart
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 数据管理（二级页）：导入与导出的入口。
 *
 * 「导入账单」点开后**原地展开**来源列表 —— 目前就两个来源，为它们再跳一层页面是浪费。
 * 选定来源才进三级页做具体的导入。这个来源列表就是扩展点：加一款软件的标准格式，
 * 往这儿加一行即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onStartImport: (ImportMode) -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()

    // 导入记录按时间倒序，第一条就是上一次导入
    val lastImport = remember(prefs) { prefs.importedFiles.firstOrNull() }

    var sourcesExpanded by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "import") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        EntryHeader(
                            title = "导入账单",
                            subtitle = "从其他记账软件或 Flowt 备份导入",
                            expanded = sourcesExpanded,
                            onClick = { sourcesExpanded = !sourcesExpanded },
                        )
                        AnimatedVisibility(visible = sourcesExpanded) {
                            Column {
                                HorizontalDivider()
                                SourceRow(
                                    title = "二级分类结构账单",
                                    subtitle = "其他记账软件的导出文件 · 支持 CSV 与 Excel",
                                    onClick = { onStartImport(ImportMode.ExternalBill) },
                                )
                                SourceRow(
                                    title = "Flowt 备份",
                                    subtitle = "本应用导出的备份文件，可直接恢复",
                                    onClick = { onStartImport(ImportMode.SelfBackup) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "export") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EntryHeader(
                        title = "导出备份",
                        subtitle = "导出为 CSV 或 Excel，可再导入回来",
                        onClick = { showExportSheet = true },
                    )
                }
            }

            item(key = "undo") {
                // 旧版本留下的记录没有批次号，认不出那批流水 —— 一律按"无可撤销"处理
                val undoable = lastImport?.takeIf { it.batchSource.isNotEmpty() }
                Card(modifier = Modifier.fillMaxWidth()) {
                    EntryHeader(
                        title = "撤销上次导入",
                        subtitle = undoable?.let { "「${it.fileName}」· ${it.count} 笔" }
                            ?: "暂无可撤销的导入",
                        enabled = undoable != null,
                        onClick = { showUndoDialog = true },
                    )
                }
            }

            item(key = "clear") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EntryHeader(
                        title = "清空数据",
                        subtitle = "删除全部流水，可选择是否连分类一起清空",
                        danger = true,
                        onClick = { showClearDialog = true },
                    )
                }
            }
        }
    }

    if (showExportSheet) {
        ExportSettingSheet(vm = vm, onDismiss = { showExportSheet = false })
    }

    if (showUndoDialog && lastImport != null) {
        UndoImportDialog(
            record = lastImport,
            onDismiss = { showUndoDialog = false },
            onConfirm = {
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { vm.importService.undoLastImport() }
                    }
                    showUndoDialog = false
                    result.onSuccess { undone ->
                        Toast.makeText(
                            context,
                            if (undone == null) {
                                "没有可撤销的导入"
                            } else {
                                "已撤销「${undone.fileName}」，删除 ${undone.deleted} 笔流水"
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }

    if (showClearDialog) {
        ClearDataDialog(
            vm = vm,
            onDismiss = { showClearDialog = false },
            onCleared = { result ->
                showClearDialog = false
                Toast.makeText(
                    context,
                    buildString {
                        append("已清空 ${result.transactions} 笔流水")
                        if (result.categories > 0) append("、${result.categories} 个分类")
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
}

/** 设置项的行头。右侧只在"这一项会展开"时才出现指示箭头。 */
@Composable
private fun EntryHeader(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    /** 非 null 时显示展开 / 收起指示；null（默认）表示既不跳页也不展开，右侧留空。 */
    expanded: Boolean? = null,
    /** 危险操作：标题用警示色，让人点之前先停一下。 */
    danger: Boolean = false,
    /** 不可用时整行置灰、不响应点击 —— 但**仍然显示**，让用户知道有这项功能。 */
    enabled: Boolean = true,
) {
    // Material 的禁用态：内容色降到 38% 不透明度，与系统其他禁用控件一致
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val titleColor = when {
        !enabled -> disabledColor
        danger -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }
    val subtitleColor = if (enabled) subtleTextColor() else disabledColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
        val indicatorColor = if (enabled) subtleTextColor() else disabledColor
        when (expanded) {
            true -> Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = indicatorColor)
            false -> Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = indicatorColor)
            // 没有下钻、也不展开的项：右边什么都不放
            null -> Unit
        }
    }
}

@Composable
private fun SourceRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtleTextColor(),
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = subtleTextColor())
    }
}

/**
 * 导出设置。三个选项：格式、时间范围、分类列形式。
 *
 * 每一项后面带上条数，用户选之前就知道会导出多少 —— 这比导出完再看结果友好。
 * 不做内容预览：导出没有需要逐条拍板的分支（导入之所以要预览，是因为列绑定可能错、
 * 分类去向要人工定），选完这三项和保存位置就已经表达了全部意图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSettingSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var format by remember { mutableStateOf(ExportFormat.Csv) }
    var layout by remember { mutableStateOf(CategoryLayout.Expanded) }
    var customRange by remember { mutableStateOf(false) }
    var startDay by remember { mutableStateOf<Long?>(null) }
    var endDay by remember { mutableStateOf<Long?>(null) }
    var totalCount by remember { mutableStateOf<Int?>(null) }
    var customCount by remember { mutableStateOf<Int?>(null) }
    var exporting by remember { mutableStateOf(false) }

    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        totalCount = withContext(Dispatchers.IO) {
            vm.exportService.count(ExportRange.All)
        }
    }

    LaunchedEffect(startDay, endDay) {
        val start = startDay
        val end = endDay
        customCount = if (start != null && end != null) {
            withContext(Dispatchers.IO) {
                vm.exportService.count(
                    ExportRange.Between(localDayStart(start), localDayEnd(end)),
                )
            }
        } else {
            null
        }
    }

    fun currentRange(): ExportRange? {
        if (!customRange) return ExportRange.All
        val start = startDay ?: return null
        val end = endDay ?: return null
        return ExportRange.Between(localDayStart(start), localDayEnd(end))
    }

    fun writeTo(uri: Uri) {
        val range = currentRange() ?: return
        val chosenFormat = format
        val chosenLayout = layout
        scope.launch {
            exporting = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val table = vm.exportService.buildTable(range, chosenLayout)
                    val bytes = vm.exportService.encode(table, chosenFormat)
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法写入所选位置")
                    table.size - 1 // 去掉表头
                }
            }
            exporting = false
            result
                .onSuccess { count ->
                    onDismiss()
                    Toast.makeText(context, "已导出 $count 笔", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // MIME 在创建时就固定了，所以两种格式各建一个 launcher，点导出时按选择派发
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.Csv.mimeType),
    ) { uri -> if (uri != null) writeTo(uri) }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.Xlsx.mimeType),
    ) { uri -> if (uri != null) writeTo(uri) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "导出备份",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            SectionLabel("格式")
            ExportFormat.entries.forEach { option ->
                RadioRow(
                    title = option.displayName,
                    subtitle = option.hint,
                    selected = format == option,
                    onSelect = { format = option },
                )
            }

            SectionLabel("时间范围")
            RadioRow(
                title = "全部",
                subtitle = totalCount?.let { "$it 笔" }.orEmpty(),
                selected = !customRange,
                onSelect = { customRange = false },
            )
            RadioRow(
                title = "自定义",
                subtitle = customCount?.let { "$it 笔" } ?: "选择起止日期",
                selected = customRange,
                onSelect = { customRange = true },
            )
            if (customRange) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { pickingStart = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(startDay?.let(::formatDay) ?: "起始日")
                    }
                    OutlinedButton(
                        onClick = { pickingEnd = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(endDay?.let(::formatDay) ?: "结束日")
                    }
                }
            }

            SectionLabel("分类列形式")
            CategoryLayout.entries.forEach { option ->
                RadioRow(
                    title = option.displayName,
                    subtitle = option.hint,
                    selected = layout == option,
                    onSelect = { layout = option },
                )
            }

            Spacer(Modifier.height(12.dp))

            val range = currentRange()
            Button(
                onClick = {
                    val name = vm.exportService.defaultFileName(format)
                    when (format) {
                        ExportFormat.Csv -> csvLauncher.launch(name)
                        ExportFormat.Xlsx -> xlsxLauncher.launch(name)
                    }
                },
                enabled = !exporting && range != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (customRange && range == null) "请先选择起止日期" else "导出")
                }
            }
        }
    }

    if (pickingStart) {
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startPickerState.selectedDateMillis?.let { startDay = it }
                        pickingStart = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = startPickerState)
        }
    }

    if (pickingEnd) {
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endPickerState.selectedDateMillis?.let { endDay = it }
                        pickingEnd = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = endPickerState)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = subtleTextColor(),
    )
}

@Composable
private fun RadioRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        }
    }
}

private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

/** 日期选择器给的"UTC 当天零点" → 本地日期。别拿它格式化普通时间戳，会差一天。 */
private fun formatDay(utcMillis: Long): String =
    DAY_FORMAT.format(Date(localDayStart(utcMillis)))

/** epoch 毫秒 → 本地日期。 */
private fun formatDate(millis: Long): String = DAY_FORMAT.format(Date(millis))

/** 撤销上次导入按钮的倒计时秒数。比清空数据短：影响面小，但仍不该一按就删。 */
private const val UNDO_COUNTDOWN_SECONDS = 3

/**
 * 撤销上次导入的确认。
 *
 * 倒计时比「清空数据」短 —— 撤销删的是"上一批导入"，不是全部账目 ——
 * 但仍是不可回退的批量删除，照样拦一下手滑。
 */
@Composable
private fun UndoImportDialog(
    record: ImportedFileRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var countdown by remember { mutableStateOf(UNDO_COUNTDOWN_SECONDS) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1_000.milliseconds)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("撤销上次导入？") },
        text = {
            Column {
                Text(
                    text = "将删除「${record.fileName}」在 ${formatDate(record.importedAt)} " +
                        "导入的 ${record.count} 笔流水。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "导入时自动创建的分类会保留",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = countdown == 0,
                onClick = onConfirm,
            ) {
                Text(
                    // 倒计时期间用次要色，能按下去时才变红 —— 与「清空数据」保持一致
                    text = if (countdown > 0) "撤销（$countdown）" else "撤销",
                    color = if (countdown > 0) {
                        subtleTextColor()
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 清空数据按钮的倒计时秒数。 */
private const val CLEAR_COUNTDOWN_SECONDS = 5

/**
 * 清空数据确认。
 *
 * 5 秒倒计时不是为了拖时间，而是防误触 —— 这个按钮删的是全部账目，
 * 必须"看着它数完"才能按下去；手滑连点两下就清空是不可接受的。
 *
 * 分类是否一起清由用户勾选，默认**不勾**：流水是主体，分类树是用户一条条建起来的，
 * 顺带删掉一个他未必想删的东西，比少删更糟。
 */
@Composable
private fun ClearDataDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onCleared: (ClearResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()

    var includeCategories by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(CLEAR_COUNTDOWN_SECONDS) }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1_000.milliseconds)
            countdown--
        }
    }

    AlertDialog(
        onDismissRequest = { if (!clearing) onDismiss() },
        title = { Text("清空数据？") },
        text = {
            Column {
                Text(
                    text = "将删除全部 ${transactions.size} 笔流水，此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeCategories = !includeCategories },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeCategories,
                        onCheckedChange = { includeCategories = it },
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = "同时删除全部分类",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${categories.size} 个分类会一起清空，之后需要重新建立",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtleTextColor(),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "清空前建议先导出一份备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = countdown == 0 && !clearing,
                onClick = {
                    scope.launch {
                        clearing = true
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                vm.clearAllData(includeCategories)
                            }
                        }
                        clearing = false
                        result.onSuccess(onCleared)
                    }
                },
            ) {
                Text(
                    // 倒计时期间用次要色，能按下去时才变红 —— 否则看着像"现在就能点"
                    text = if (countdown > 0) "清空（$countdown）" else "清空",
                    color = if (countdown > 0) {
                        subtleTextColor()
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
