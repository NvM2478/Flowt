package com.flowt.app.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.components.CategoryPickerSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 记账页。
 *
 * 目标路径：打开即弹数字键盘 → 输金额 → 点分类（常用分类一格直达）→ 保存。
 * 日期默认"现在"、备注选填、无账户 —— 都在这个页面里一步到位。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    vm: AppViewModel,
    categories: List<Category>,
    onDone: () -> Unit,
    /** 是否让金额输入框自动获取焦点（触发数字键盘）。
     *  揭场动画期间必须为 false —— 否则键盘会在点击瞬间弹出，抢在圆扩大之前。 */
    autoFocusAmount: Boolean = true,
    /** 非空表示"编辑已有流水"，为空表示"新建"。两者共用这一页，只是预填与按钮不同。 */
    editTarget: TransactionEntity? = null,
    /**
     * 是否为编辑模式。默认由 [editTarget] 推导，但调用方可以显式传入 ——
     * 关闭动画期间 FlowtApp 会把 editTarget 立刻置空，这一页却仍挂载着，
     * 若不冻结这个标志，它会误判为"新建"：输入框全部重置、还弹出键盘。
     */
    isEditing: Boolean = editTarget != null,
    /** 编辑模式下点删除时回调；新建模式不显示删除按钮。 */
    onDeleteRequest: () -> Unit = {},
) {

    // 用 editTarget 作为 remember 的 key：每次打开都按目标重新初始化（编辑时预填旧值）。
    var amountInput by remember(editTarget) {
        mutableStateOf(editTarget?.let { formatCentsForInput(it.amountCents) } ?: "")
    }
    var selectedCategoryId by remember(editTarget) { mutableStateOf(editTarget?.categoryId) }
    var noteInput by remember(editTarget) { mutableStateOf(editTarget?.note ?: "") }
    var timestamp by remember(editTarget) {
        mutableStateOf(editTarget?.timestamp ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var frequentIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // 打开即聚焦金额输入框 → 数字键盘自动弹出。
    // 但**编辑时不能弹键盘**：用户点进一笔账往往只是想改分类，键盘会盖住大半个屏幕。
    // 注意这里对编辑态调 clearFocus()：只把 autoFocus 设为 false 不够 ——
    // 输入框可能还留着上一次的焦点，键盘同样会弹出来。
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocusAmount, isEditing) {
        if (isEditing) {
            focusManager.clearFocus()
        } else if (autoFocusAmount) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(Unit) {
        frequentIds = runCatching { vm.categoryRepo.getFrequentCategories(8).map { it.categoryId } }
            .getOrDefault(emptyList())
    }

    BackHandler { onDone() }

    val pathById = remember(categories) { categories.associate { it.id to it.path } }
    val childrenByParent = remember(categories) { categories.groupBy { it.parentId } }
    val selectedPath = selectedCategoryId?.let { pathById[it] }
    val amountCents = parseCents(amountInput)
    val canSave = amountCents != null && amountCents > 0 && selectedCategoryId != null

    val sheetState = rememberModalBottomSheetState()

    // 记账页底色 = FAB 的容器色（primaryContainer）。
    // 注意别用 primary：那是"主色"，比 FAB 的容器色深一大截，两者看起来不是同一个东西。
    val pageBackground = MaterialTheme.colorScheme.primaryContainer
    val onPageColor = MaterialTheme.colorScheme.onPrimaryContainer
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = onPageColor,
        unfocusedTextColor = onPageColor,
        focusedBorderColor = onPageColor,
        unfocusedBorderColor = onPageColor.copy(alpha = 0.6f),
        focusedLabelColor = onPageColor,
        unfocusedLabelColor = onPageColor.copy(alpha = 0.8f),
        cursorColor = onPageColor,
        focusedPrefixColor = onPageColor,
        unfocusedPrefixColor = onPageColor,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(if (isEditing) "编辑" else "记一笔", color = onPageColor) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = onPageColor,
                    navigationIconContentColor = onPageColor,
                ),
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw -> sanitizeAmountInput(raw)?.let { amountInput = it } },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    keyboardActions = KeyboardActions(),
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                CategorySelector(
                    selectedPath = selectedPath,
                    frequentIds = frequentIds,
                    pathById = pathById,
                    onPick = { selectedCategoryId = it },
                    onMore = { showCategorySheet = true },
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 日期：点一下弹系统日期选择器。
                // 这里不用 OutlinedTextField —— 它在 enabled=false 时不响应点击，readOnly 又会吞掉点击，
                // 要靠覆盖一堆 disabled* 颜色才能既可点又不发灰。自己拼一个一行高的框更直接。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(
                            width = 1.dp,
                            color = onPageColor.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = formatDateTime(timestamp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = onPageColor,
                    )
                    Text(
                        text = "日期",
                        style = MaterialTheme.typography.labelSmall,
                        color = onPageColor.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(pageBackground)
                            .padding(horizontal = 4.dp)
                            .offset(y = (-8).dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isEditing) {
                        OutlinedButton(
                            onClick = onDeleteRequest,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("删除")
                        }
                    }

                    Button(
                        onClick = {
                            val cents = amountCents ?: return@Button
                            val categoryId = selectedCategoryId ?: return@Button
                            val note = noteInput.trim().ifBlank { null }
                            scope.launch {
                                if (editTarget == null) {
                                    vm.txRepo.insert(
                                        TransactionEntity(
                                            amountCents = cents,
                                            timestamp = timestamp,
                                            categoryId = categoryId,
                                            note = note,
                                        )
                                    )
                                } else {
                                    // 编辑：id 与 source 保持不变，只替换被修改的字段
                                    vm.txRepo.update(
                                        editTarget.copy(
                                            amountCents = cents,
                                            timestamp = timestamp,
                                            categoryId = categoryId,
                                            note = note,
                                        )
                                    )
                                }
                                onDone()
                            }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            // 深色 primary 按钮压在浅色 primaryContainer 底上：对比最强，最像"主要动作"
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.weight(if (isEditing) 1.4f else 1f),
                    ) {
                        Text("保存")
                    }
                }
            }
        }

        if (showDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pickerState.selectedDateMillis?.let { picked ->
                                timestamp = mergeDateKeepingTime(picked, timestamp)
                            }
                            showDatePicker = false
                        },
                    ) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                },
            ) {
                DatePicker(state = pickerState)
            }
        }

        // 分类下钻：放在 Box 里而不是 Column 里，避免被 weight 挤压
        if (showCategorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showCategorySheet = false },
                sheetState = sheetState,
            ) {
                CategoryPickerSheet(
                    categories = categories,
                    childrenByParent = childrenByParent,
                    onPick = {
                        selectedCategoryId = it
                        showCategorySheet = false
                    },
                )
            }
        }
    }
}

/** 分类选择区：常用分类一格直达 + 「选择分类」入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySelector(
    selectedPath: String?,
    frequentIds: List<Long>,
    pathById: Map<Long, String>,
    onPick: (Long) -> Unit,
    onMore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val onPageColor = MaterialTheme.colorScheme.onPrimaryContainer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = selectedPath?.let { "分类：$it" } ?: "分类：未选择",
                style = MaterialTheme.typography.titleMedium,
                color = if (selectedPath == null) onPageColor.copy(alpha = 0.7f) else onPageColor,
            )
            TextButton(onClick = onMore) { Text("选择分类") }
        }

        if (frequentIds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                frequentIds.forEach { id ->
                    val path = pathById[id] ?: return@forEach
                    FilledTonalButton(
                        onClick = { onPick(id) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(path, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

// --- 输入处理 ---

/**
 * 金额输入的合法模式：数字 + 最多一个小数点 + 最多两位小数。
 * 提到文件级是为了避免每次按键都重新编译正则 —— 写在函数里等于每输入一个字符 new 一个 Regex。
 */
private val AMOUNT_INPUT_PATTERN = Regex("^\\d*\\.?\\d{0,2}$")

private val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

/** 时间戳 → "2026-09-21 13:40"，用于日期字段的展示。 */
private fun formatDateTime(timestamp: Long): String = DATE_TIME_FORMAT.format(Date(timestamp))

/** 分 → 输入框文本："1250" → "12.50"（编辑时预填金额）。 */
private fun formatCentsForInput(cents: Long): String {
    val yuan = cents / 100
    val remainder = cents % 100
    return if (remainder == 0L) yuan.toString() else "$yuan.${remainder.toString().padStart(2, '0')}"
}

/**
 * 把日期选择器返回的"当天 UTC 零点"与原有的时分秒合并，得到新的本地时间戳。
 * 不能直接用选择器的值：那个值代表 UTC 当天零点，直接存会让日期偏移一天。
 */
private fun mergeDateKeepingTime(pickedDateMillis: Long, originalTimestamp: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = pickedDateMillis
    }
    val original = Calendar.getInstance().apply { timeInMillis = originalTimestamp }
    return Calendar.getInstance().apply {
        set(
            picked.get(Calendar.YEAR),
            picked.get(Calendar.MONTH),
            picked.get(Calendar.DAY_OF_MONTH),
            original.get(Calendar.HOUR_OF_DAY),
            original.get(Calendar.MINUTE),
            original.get(Calendar.SECOND),
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/**
 * 金额输入的净化：只允许数字和最多一个小数点，小数最多两位。
 * 返回 null 表示这次输入应该被忽略。
 * 从源头拦截非法输入，比事后校验对用户友好 —— 输入框里根本打不出错误内容。
 */
private fun sanitizeAmountInput(raw: String): String? {
    if (raw.isEmpty()) return ""
    if (!raw.matches(AMOUNT_INPUT_PATTERN)) return null
    if (raw.length > 12) return null
    return raw
}

/** "12.5" → 1250（分）。无法解析或超出安全范围返回 null。 */
private fun parseCents(input: String): Long? {
    if (input.isBlank() || input == ".") return null
    val parts = input.split(".")
    val yuan = parts[0].ifBlank { "0" }.toLongOrNull() ?: return null
    val cents = if (parts.size > 1) {
        parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    } else {
        0L
    }
    val total = yuan * 100 + cents
    if (total < 0) return null
    return total
}
