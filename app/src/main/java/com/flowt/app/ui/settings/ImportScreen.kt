package com.flowt.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flowt.app.data.bill.BindingCheck
import com.flowt.app.data.bill.CategoryMapping
import com.flowt.app.data.bill.ColumnBinding
import com.flowt.app.data.bill.ImportField
import com.flowt.app.data.bill.ImportMode
import com.flowt.app.data.bill.ImportOutcome
import com.flowt.app.data.bill.ImportPlan
import com.flowt.app.data.bill.MappingTarget
import com.flowt.app.data.bill.ParsedBill
import com.flowt.app.data.bill.ParsedFile
import com.flowt.app.data.bill.PreviewRow
import com.flowt.app.data.bill.TableReaders
import com.flowt.app.metrics.formatAmount
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.components.CategoryPickerSheet
import com.flowt.app.ui.components.childrenByParentOf
import com.flowt.app.ui.theme.FlowtColors
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 三级页内部的阶段。 */
private sealed interface Stage {
    data object PickFile : Stage
    data object Parsing : Stage
    data object Preview : Stage
    data object Importing : Stage
    data class Failed(val message: String) : Stage
    data class Done(val outcome: ImportOutcome) : Stage
}

/**
 * 导入（三级页）：选文件 → 解析 → 预览 → 结果。
 *
 * 模式由进来的入口决定，页面本身只按模式调整"要用户操多少心"：
 * 自家备份的两个确认区默认折叠，换机恢复是零操作；外部账单则都要过目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    vm: AppViewModel,
    mode: ImportMode,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf<Stage>(Stage.PickFile) }
    var file by remember { mutableStateOf<ParsedFile?>(null) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var mappings by remember { mutableStateOf<List<CategoryMapping>>(emptyList()) }

    var bindingExpanded by remember { mutableStateOf(false) }
    var mappingExpanded by remember { mutableStateOf(mode == ImportMode.ExternalBill) }

    var dropConfirm by remember { mutableStateOf(false) }
    var repeatConfirm by remember { mutableStateOf(false) }
    var mappingEditor by remember { mutableStateOf<CategoryMapping?>(null) }
    var showInvalid by remember { mutableStateOf(false) }

    fun load(uri: Uri) {
        scope.launch {
            stage = Stage.Parsing
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = queryFileName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("读不到这个文件")
                    val parsedFile = vm.importService.parseFile(bytes, name, mode)
                    parsedFile to vm.importService.buildPlan(parsedFile, mode)
                }
            }

            result
                .onSuccess { (parsedFile, builtPlan) ->
                    file = parsedFile
                    plan = builtPlan
                    mappings = builtPlan.mappings
                    // 自动识别不完整时直接把绑定区摊开，省得用户还要找入口
                    bindingExpanded = builtPlan.needsAttention
                    stage = Stage.Preview
                }
                .onFailure { stage = Stage.Failed(it.message ?: "解析失败") }
        }
    }

    fun applyBinding(binding: ColumnBinding) {
        val current = file ?: return
        scope.launch {
            val rebuilt = withContext(Dispatchers.IO) {
                vm.importService.rebuild(current, mode, binding, mappings)
            }
            plan = rebuilt
            mappings = rebuilt.mappings
        }
    }

    fun applyMapping(target: CategoryMapping, newTarget: MappingTarget) {
        val updated = mappings.map {
            if (it.sourcePath == target.sourcePath) it.copy(target = newTarget) else it
        }
        mappings = updated
        val rows = plan?.parsed?.rows ?: return
        scope.launch {
            val duplicates = withContext(Dispatchers.IO) {
                vm.importService.recountDuplicates(rows, updated)
            }
            plan = plan?.copy(mappings = updated, duplicateRowIndexes = duplicates)
        }
    }

    fun doImport() {
        val current = plan ?: return
        scope.launch {
            stage = Stage.Importing
            val result = runCatching {
                withContext(Dispatchers.IO) { vm.importService.execute(current, mappings) }
            }
            result
                .onSuccess { stage = Stage.Done(it) }
                .onFailure { stage = Stage.Failed(it.message ?: "导入失败") }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(::load) }

    BackHandler {
        when (stage) {
            Stage.Preview -> dropConfirm = true
            // 解析/导入进行中不接受返回：半途中断只会留下说不清的状态
            Stage.Parsing, Stage.Importing -> Unit
            else -> onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FlowtColors.current.navigationBarBackground,
                    titleContentColor = FlowtColors.current.navigationBarText,
                    navigationIconContentColor = FlowtColors.current.navigationBarText,
                ),
                title = { Text(screenTitle(mode)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (stage == Stage.Preview) dropConfirm = true else onClose()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            val current = plan
            if (stage == Stage.Preview && current != null) {
                ConfirmBar(
                    plan = current,
                    binding = current.parsed.binding,
                    // 自家备份的按钮文案换成"恢复"，让用户知道点下去是把账拿回来
                    label = if (mode == ImportMode.SelfBackup) {
                        "恢复 ${current.importableCount} 笔流水"
                    } else {
                        "确认导入 ${current.importableCount} 笔"
                    },
                    onConfirm = {
                        if (current.previousImport != null) repeatConfirm = true else doImport()
                    },
                )
            }
        },
    ) { padding ->
        val insets = Modifier
            .fillMaxSize()
            .padding(padding)

        when (val current = stage) {
            Stage.PickFile -> PickFileContent(mode = mode) { picker.launch(MIME_TYPES) }

            Stage.Parsing -> LoadingContent("正在解析…")
            Stage.Importing -> LoadingContent("正在导入…")

            is Stage.Failed -> FailedContent(
                message = current.message,
                onRetry = { stage = Stage.PickFile },
                onClose = onClose,
            )

            is Stage.Done -> DoneContent(
                outcome = current.outcome,
                onDone = onClose,
                modifier = insets,
            )

            Stage.Preview -> plan?.let { preview ->
                PreviewContent(
                    plan = preview,
                    mappings = mappings,
                    bindingExpanded = bindingExpanded,
                    mappingExpanded = mappingExpanded,
                    onToggleBinding = { bindingExpanded = !bindingExpanded },
                    onToggleMapping = { mappingExpanded = !mappingExpanded },
                    onBindingChange = { field, column ->
                        applyBinding(
                            preview.parsed.binding.copy(
                                single = preview.parsed.binding.single.toMutableMap().apply {
                                    if (column == null) remove(field) else put(field, column)
                                },
                            ),
                        )
                    },
                    onCategoryColumnChange = { level, column ->
                        val columns = preview.parsed.binding.categories.toMutableList()
                        if (column == null) {
                            if (level < columns.size) columns.removeAt(level)
                        } else if (level < columns.size) {
                            columns[level] = column
                        } else {
                            columns.add(column)
                        }
                        applyBinding(preview.parsed.binding.copy(categories = columns))
                    },
                    onEditMapping = { mappingEditor = it },
                    onShowInvalid = { showInvalid = true },
                    modifier = insets,
                )
            }
        }
    }

    // --- 对话框 ---

    if (dropConfirm) {
        AlertDialog(
            onDismissRequest = { dropConfirm = false },
            title = { Text("放弃本次导入？") },
            text = { Text("已经解析出来的内容不会保存，分类去向的选择也会丢掉。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dropConfirm = false
                        onClose()
                    },
                ) { Text("放弃", color = FlowtColors.current.dangerAccent) }
            },
            dismissButton = {
                TextButton(onClick = { dropConfirm = false }) { Text("继续导入") }
            },
        )
    }

    plan?.previousImport?.let { previous ->
        if (repeatConfirm) {
            AlertDialog(
                onDismissRequest = { repeatConfirm = false },
                title = { Text("这个文件导入过") },
                text = {
                    Text(
                        "「${previous.fileName}」在 ${formatDay(previous.importedAt)} " +
                            "导入过 ${previous.count} 笔。\n\n" +
                            "如果只是想改分类去向，重复导入会把同一批流水记两遍。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repeatConfirm = false
                            doImport()
                        },
                    ) { Text("仍然导入") }
                },
                dismissButton = {
                    TextButton(onClick = { repeatConfirm = false }) { Text("取消") }
                },
            )
        }
    }

    mappingEditor?.let { editing ->
        MappingEditorDialog(
            vm = vm,
            mapping = editing,
            onDismiss = { mappingEditor = null },
            onPick = { target ->
                applyMapping(editing, target)
                mappingEditor = null
            },
        )
    }

    val invalidRows = plan?.parsed?.invalidRows.orEmpty()
    if (showInvalid && invalidRows.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showInvalid = false },
            title = { Text("这 ${invalidRows.size} 行没能解析") },
            text = {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(invalidRows) { row ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = "第 ${row.lineNumber} 行 · ${row.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = row.raw.filter { it.isNotBlank() }.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = subtleTextColor(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInvalid = false }) { Text("知道了") }
            },
        )
    }
}

// --- 各阶段的内容 ---

@Composable
private fun PickFileContent(mode: ImportMode, onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (mode) {
                ImportMode.SelfBackup -> "选择 Flowt 导出的备份文件"
                ImportMode.ExternalBill -> "选择账单文件"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (mode) {
                ImportMode.SelfBackup -> "换机或重装后，用它把账目完整拿回来"
                ImportMode.ExternalBill -> "从其他记账软件导出的账单，${TableReaders.SUPPORTED_HINT}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = subtleTextColor(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick) { Text("选择文件") }
    }
}

@Composable
private fun LoadingContent(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = subtleTextColor())
    }
}

@Composable
private fun FailedContent(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = FlowtColors.current.dangerAccent,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text("返回") }
            Button(onClick = onRetry) { Text("重新选择文件") }
        }
    }
}

@Composable
private fun PreviewContent(
    plan: ImportPlan,
    mappings: List<CategoryMapping>,
    bindingExpanded: Boolean,
    mappingExpanded: Boolean,
    onToggleBinding: () -> Unit,
    onToggleMapping: () -> Unit,
    onBindingChange: (ImportField, Int?) -> Unit,
    onCategoryColumnChange: (Int, Int?) -> Unit,
    onEditMapping: (CategoryMapping) -> Unit,
    onShowInvalid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = plan.parsed
    // 这三样都要在整份账单上过滤 + 排序，只在计划变化时算一次，别跟着每次重组重跑
    val samples = remember(plan) { plan.previewRows() }
    val importableTotal = remember(plan) { plan.importableCount }
    val visibleMappings = remember(plan) { plan.visibleMappings() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        plan.previousImport?.let { previous ->
            item(key = "repeat") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "这个文件导入过",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = FlowtColors.current.dangerAccent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "「${previous.fileName}」在 ${formatDay(previous.importedAt)} " +
                                "导入过 ${previous.count} 笔。继续导入可能记成两遍。",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtleTextColor(),
                        )
                    }
                }
            }
        }

        item(key = "summary") {
            SummaryCard(plan = plan, onShowInvalid = onShowInvalid)
        }

        item(key = "binding") {
            BindingSection(
                parsed = parsed,
                expanded = bindingExpanded,
                onToggle = onToggleBinding,
                onBindingChange = onBindingChange,
                onCategoryColumnChange = onCategoryColumnChange,
            )
        }

        item(key = "mapping") {
            MappingSection(
                mappings = visibleMappings,
                expanded = mappingExpanded,
                onToggle = onToggleMapping,
                onEdit = onEditMapping,
            )
        }

        item(key = "samples") {
            SampleCard(rows = samples, total = importableTotal)
        }
    }
}

@Composable
private fun SummaryCard(plan: ImportPlan, onShowInvalid: () -> Unit) {
    val parsed = plan.parsed
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "共 ${parsed.dataRows.size} 行 · 可导入 ${plan.importableCount} 笔",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            if (plan.duplicateWithExisting > 0) {
                SummaryLine("其中 ${plan.duplicateWithExisting} 笔与已有记录重复，将自动跳过")
            }
            if (parsed.duplicateGroupsInFile > 0) {
                SummaryLine("文件内有 ${parsed.duplicateGroupsInFile} 组完全相同的记录")
            }
            if (parsed.skippedIncome > 0) {
                SummaryLine("收入 ${parsed.skippedIncome} 笔将跳过（当前版本只记支出）")
            }
            if (parsed.invalidRows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowInvalid),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${parsed.invalidRows.size} 行无法解析",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlowtColors.current.dangerAccent,
                        modifier = Modifier.weight(1f),
                    )
                    Text("查看", style = MaterialTheme.typography.bodySmall, color = subtleTextColor())
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = subtleTextColor(),
    )
}

@Composable
private fun BindingSection(
    parsed: ParsedBill,
    expanded: Boolean,
    onToggle: () -> Unit,
    onBindingChange: (ImportField, Int?) -> Unit,
    onCategoryColumnChange: (Int, Int?) -> Unit,
) {
    val binding = parsed.binding
    val check = parsed.check

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader(
                title = "列绑定",
                summary = if (binding.missingRequired.isEmpty()) {
                    "已识别 ${binding.single.size + binding.categories.size} 列"
                } else {
                    "缺少「${binding.missingRequired.joinToString("、") { it.displayName }}」"
                },
                hasProblem = binding.missingRequired.isNotEmpty() ||
                    check.amountAllFailed || check.timestampAllFailed,
                expanded = expanded,
                onToggle = onToggle,
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    ImportField.entries.forEach { field ->
                        BindingRow(
                            label = field.displayName,
                            headers = parsed.headers,
                            selected = binding.single[field],
                            status = statusOf(field, check),
                            onSelect = { onBindingChange(field, it) },
                        )
                    }
                    binding.categories.forEachIndexed { level, column ->
                        BindingRow(
                            label = "${levelLabel(level + 1)}分类",
                            headers = parsed.headers,
                            selected = column,
                            status = null,
                            onSelect = { onCategoryColumnChange(level, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingRow(
    label: String,
    headers: List<String>,
    selected: Int?,
    status: BindingStatus?,
    onSelect: (Int?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(84.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            TextButton(onClick = { menuOpen = true }) {
                Text(
                    text = selected?.let { headers.getOrNull(it) ?: "第 ${it + 1} 列" } ?: "不使用",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                headers.forEachIndexed { index, header ->
                    DropdownMenuItem(
                        text = { Text(header.ifBlank { "第 ${index + 1} 列" }) },
                        onClick = {
                            onSelect(index)
                            menuOpen = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("不使用") },
                    onClick = {
                        onSelect(null)
                        menuOpen = false
                    },
                )
            }
        }

        if (status != null) BindingStatusBadge(status)
    }
}

/** 类型校验的三级反馈：通过 / 部分失败 / 整列失败。 */
private sealed interface BindingStatus {
    data object Ok : BindingStatus
    data class Partial(val failures: Int, val sampled: Int) : BindingStatus
    data object AllFailed : BindingStatus
}

@Composable
private fun BindingStatusBadge(status: BindingStatus) {
    val (text, color) = when (status) {
        BindingStatus.Ok -> "✓" to Color(0xFF2E7D32)
        is BindingStatus.Partial -> "${status.failures}/${status.sampled} 行认不出" to Color(0xFFE65100)
        BindingStatus.AllFailed -> "认不出来" to FlowtColors.current.dangerAccent
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
private fun MappingSection(
    mappings: List<CategoryMapping>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (CategoryMapping) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            SectionHeader(
                title = "分类去向",
                summary = "${mappings.size} 个分类",
                hasProblem = false,
                expanded = expanded,
                onToggle = onToggle,
            )

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    mappings.forEach { mapping ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEdit(mapping) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mapping.sourcePath,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${mapping.rowCount} 笔",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtleTextColor(),
                                )
                            }
                            Text(
                                text = targetLabel(mapping.target),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mapping.target is MappingTarget.Skip) {
                                    subtleTextColor()
                                } else {
                                    FlowtColors.current.accentInlineText
                                },
                            )
                            Text(
                                text = "›",
                                style = MaterialTheme.typography.titleLarge,
                                color = subtleTextColor(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCard(rows: List<PreviewRow>, total: Int) {
    if (rows.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                // 有效流水不多时全部都在这里了，再写"最近 N 条"反而让人以为还有没显示的
                text = if (rows.size < total) "最近 ${rows.size} 条预览" else "预览",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            // 导入前最后一道人工校验：绑定的列对不对，看时间、分类、备注、金额一眼就知道。
            // 分类显示的是**去向落定之后**的结果 —— 用户改过去向，这里就跟着变，
            // 否则看到的和导进去的会对不上
            rows.forEach { preview ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SAMPLE_TIME_FORMAT.format(Date(preview.row.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = subtleTextColor(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = formatAmount(preview.row.amountCents),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = buildString {
                            append(preview.targetPath)
                            preview.row.note?.takeIf { it.isNotBlank() }
                                ?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmBar(
    plan: ImportPlan,
    binding: ColumnBinding,
    label: String,
    onConfirm: () -> Unit,
) {
    val blocked = binding.missingRequired.isNotEmpty() ||
        plan.parsed.check.amountAllFailed ||
        plan.parsed.check.timestampAllFailed ||
        plan.importableCount <= 0

    Column(modifier = Modifier.padding(16.dp)) {
        if (blocked) {
            Text(
                text = when {
                    binding.missingRequired.isNotEmpty() ->
                        "请先指定「${binding.missingRequired.joinToString("、") { it.displayName }}」对应哪一列"

                    plan.parsed.check.amountAllFailed -> "金额列认不出数字，请重新指定"
                    plan.parsed.check.timestampAllFailed -> "时间列认不出日期，请重新指定"
                    plan.parsed.rows.isEmpty() -> "这个文件里没有可导入的流水"
                    else -> "没有可导入的流水"
                },
                style = MaterialTheme.typography.bodySmall,
                color = FlowtColors.current.dangerAccent,
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onConfirm,
            enabled = !blocked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun DoneContent(
    outcome: ImportOutcome,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("导入完成", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "新增 ${outcome.imported} 笔",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildString {
                if (outcome.skippedDuplicate > 0) append("跳过重复 ${outcome.skippedDuplicate} 笔 · ")
                if (outcome.skippedIncome > 0) append("忽略收入 ${outcome.skippedIncome} 笔 · ")
                if (outcome.skippedByMapping > 0) append("按去向跳过 ${outcome.skippedByMapping} 笔 · ")
                if (outcome.createdCategories > 0) append("新建分类 ${outcome.createdCategories} 个")
            }.trimEnd(' ', '·'),
            style = MaterialTheme.typography.bodySmall,
            color = subtleTextColor(),
        )

        Spacer(Modifier.height(20.dp))

        // 撤销入口不在这里：用户得先回主页看过实际效果，才谈得上要不要撤。
        // 所以这一页只负责告诉他去哪儿撤 —— 这正是旧版把按钮放这儿却没人用的原因。
        Text(
            text = "想撤销这次导入，可到「设置 → 数据管理 → 撤销上次导入」。",
            style = MaterialTheme.typography.bodySmall,
            color = subtleTextColor(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}

/** 分类去向编辑器：新建、跳过，或从已有分类里挑一个。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingEditorDialog(
    vm: AppViewModel,
    mapping: CategoryMapping,
    onDismiss: () -> Unit,
    onPick: (MappingTarget) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsState()

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            CategoryPickerSheet(
                categories = categories,
                childrenByParent = remember(categories) { childrenByParentOf(categories) },
                onPick = { id ->
                    val path = categories.firstOrNull { it.id == id }?.path.orEmpty()
                    onPick(MappingTarget.Existing(id, path))
                },
            )
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${mapping.sourcePath}」的去向") },
        text = {
            Column {
                Text(
                    text = "账单里有 ${mapping.rowCount} 笔属于这个分类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { onPick(MappingTarget.Create) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("按原名新建分类", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("改用已有分类…", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = { onPick(MappingTarget.Skip) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "跳过（这些流水不导入）",
                        color = FlowtColors.current.dangerAccent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// --- 小组件与工具 ---

@Composable
private fun SectionHeader(
    title: String,
    summary: String,
    hasProblem: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasProblem) FlowtColors.current.dangerAccent else subtleTextColor(),
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = subtleTextColor(),
        )
    }
}

private fun statusOf(field: ImportField, check: BindingCheck): BindingStatus? = when (field) {
    ImportField.Timestamp -> when {
        check.timestampAllFailed -> BindingStatus.AllFailed
        check.timestampHasFailures -> BindingStatus.Partial(check.timestampFailures, check.sampled)
        else -> BindingStatus.Ok
    }

    ImportField.Amount -> when {
        check.amountAllFailed -> BindingStatus.AllFailed
        check.amountHasFailures -> BindingStatus.Partial(check.amountFailures, check.sampled)
        else -> BindingStatus.Ok
    }

    // 分类、备注、收支类型都是自由文本，没有"格式对不对"这回事
    ImportField.Note, ImportField.Kind -> null
}

private fun targetLabel(target: MappingTarget): String = when (target) {
    is MappingTarget.Existing -> target.path
    MappingTarget.Create -> "新建"
    MappingTarget.Skip -> "跳过"
}

private fun levelLabel(level: Int): String =
    if (level in 1..10) CN_NUMBERS[level - 1] else level.toString()

private fun screenTitle(mode: ImportMode): String = when (mode) {
    ImportMode.ExternalBill -> "从账单文件导入"
    ImportMode.SelfBackup -> "恢复 Flowt 备份"
}

/** 从 content Uri 上问出文件名；问不到就退回路径末段。 */
private fun queryFileName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "账单文件"
}

/**
 * MIME 列表。Android 上文件的 MIME 很不可靠（有的文件管理器把 .csv 报成
 * application/octet-stream），所以这里收得宽，选定后再按文件名后缀校验一次。
 */
private val MIME_TYPES = arrayOf(
    "text/*",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "application/octet-stream",
)

/** 带上年份与秒：判重是按毫秒精确的，预览显示到秒，用户才能理解"为什么这两笔不算重复"。 */
private val SAMPLE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
private val DIALOG_DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

private fun formatDay(millis: Long): String = DIALOG_DAY_FORMAT.format(Date(millis))

private val CN_NUMBERS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

/** 自动识别不完整（缺必需列、或整列认不出类型）时，预览页应当直接把绑定区摊开。 */
private val ImportPlan.needsAttention: Boolean
    get() = parsed.binding.missingRequired.isNotEmpty() ||
        parsed.check.amountAllFailed ||
        parsed.check.timestampAllFailed
