package com.flowt.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.db.Category
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.launch

/** 树形展示用的行：分类 + 缩进层级。 */
private data class CategoryRow(
    val category: Category,
    val depth: Int,
)

/** 新建/重命名对话框的状态。 */
private sealed interface EntryDialog {
    data object None : EntryDialog
    data class AddRoot(val parentId: Long?) : EntryDialog
    data class Rename(val target: Category) : EntryDialog
}

/** 删除确认的状态。 */
private data class DeleteConfirm(
    val category: Category,
    val subtreeIds: List<Long>,
    val transactionCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManageScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    var dialog by remember { mutableStateOf<EntryDialog>(EntryDialog.None) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf<DeleteConfirm?>(null) }

    val scope = rememberCoroutineScope()
    val rows = remember(categories) { buildRows(categories) }

    // 有对话框打开时先关对话框，否则返回会跳过它直接退页面 —— 这符合 Android 的返回栈直觉
    BackHandler(enabled = dialog is EntryDialog.None && deleteConfirm == null) {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogError = null
                    dialog = EntryDialog.AddRoot(parentId = null)
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建一级分类")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                Text("还没有任何分类", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点右下角 + 建一级分类。进到分类里可以继续加子分类，" +
                        "层数不限，比如「餐饮 / 外卖 / 午餐」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtleTextColor(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(rows, key = { it.category.id }) { row ->
                    CategoryRowItem(
                        row = row,
                        onRename = {
                            dialogError = null
                            dialog = EntryDialog.Rename(row.category)
                        },
                        onAddChild = {
                            dialogError = null
                            dialog = EntryDialog.AddRoot(parentId = row.category.id)
                        },
                        onDelete = {
                            scope.launch {
                                val ids = subtreeIds(categories, row.category)
                                val count = vm.txRepo.countInCategories(ids)
                                deleteConfirm = DeleteConfirm(
                                    category = row.category,
                                    subtreeIds = ids,
                                    transactionCount = count,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    when (val current = dialog) {
        is EntryDialog.None -> Unit

        is EntryDialog.AddRoot -> {
            val parentPath = current.parentId?.let { id ->
                categories.firstOrNull { it.id == id }?.path
            }
            CategoryNameDialog(
                title = if (parentPath == null) "新建一级分类" else "在「$parentPath」下新建子分类",
                initialName = "",
                errorMessage = dialogError,
                confirmLabel = "创建",
                onDismiss = {
                    dialog = EntryDialog.None
                    dialogError = null
                },
                onConfirm = { name ->
                    scope.launch {
                        runCatching {
                            vm.categoryRepo.create(parentId = current.parentId, name = name)
                        }
                            .onSuccess {
                                dialog = EntryDialog.None
                                dialogError = null
                            }
                            .onFailure { dialogError = it.message ?: "创建失败" }
                    }
                },
            )
        }

        is EntryDialog.Rename -> CategoryNameDialog(
            title = "重命名「${current.target.path}」",
            initialName = current.target.name,
            errorMessage = dialogError,
            confirmLabel = "保存",
            onDismiss = {
                dialog = EntryDialog.None
                dialogError = null
            },
            onConfirm = { name ->
                scope.launch {
                    runCatching { vm.categoryRepo.rename(current.target.id, name) }
                        .onSuccess {
                            dialog = EntryDialog.None
                            dialogError = null
                        }
                        .onFailure { dialogError = it.message ?: "重命名失败" }
                }
            },
        )
    }

    deleteConfirm?.let { confirm ->
        DeleteConfirmDialog(
            confirm = confirm,
            allCategories = categories,
            onDismiss = { deleteConfirm = null },
            onDeleteTransactions = {
                scope.launch {
                    runCatching {
                        vm.categoryRepo.deleteTransactionsOf(confirm.subtreeIds)
                        vm.categoryRepo.delete(confirm.category.id)
                    }
                    deleteConfirm = null
                }
            },
            onMigrateTo = { targetCategoryId ->
                scope.launch {
                    runCatching {
                        vm.categoryRepo.moveTransactions(confirm.subtreeIds, targetCategoryId)
                        vm.categoryRepo.delete(confirm.category.id)
                    }
                    deleteConfirm = null
                }
            },
        )
    }
}

@Composable
private fun CategoryRowItem(
    row: CategoryRow,
    onRename: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (12 + row.depth * 20).dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (row.depth == 0) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAddChild) {
                Icon(Icons.Filled.Add, contentDescription = "加子分类", tint = subtleTextColor())
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "重命名", tint = subtleTextColor())
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = subtleTextColor())
            }
        }
    }
}

@Composable
private fun CategoryNameDialog(
    title: String,
    initialName: String,
    errorMessage: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名") },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "分类名不能包含「/」，它被用作层级的路径分隔符。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 删除确认。
 *
 * 因为有"一笔流水必须绑定一个分类"这条硬规则，删除分类时流水不能失去分类，
 * 所以有流水的情况下必须让用户明确选择去向：迁移到别的分类，或者连同流水一起删。
 */
@Composable
private fun DeleteConfirmDialog(
    confirm: DeleteConfirm,
    allCategories: List<Category>,
    onDismiss: () -> Unit,
    onDeleteTransactions: () -> Unit,
    onMigrateTo: (Long) -> Unit,
) {
    // 候选目标：所有分类（迁移到自己的子分类会被仓库层拒绝，这里不做二次过滤）
    val candidates = remember(allCategories) { allCategories.sortedBy { it.path } }

    if (confirm.transactionCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("删除「${confirm.category.path}」？") },
            text = { Text("该分类及其子分类下没有流水，可以安全删除。") },
            confirmButton = {
                TextButton(onClick = onDeleteTransactions) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            },
        )
        return
    }

    var migrateTarget by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${confirm.category.path}」下还有 ${confirm.transactionCount} 笔流水") },
        text = {
            Column {
                Text(
                    text = "这些流水的分类必须先有去处 —— 你的规则是「一笔流水必须绑定一个分类」。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = migrateTarget?.let { "迁移到：${it.path}" } ?: "点下面的分类选择迁移目标：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(candidates, key = { it.id }) { candidate ->
                        val isSelf = candidate.id == confirm.category.id
                        val inSubtree = candidate.id in confirm.subtreeIds
                        TextButton(
                            onClick = { if (!inSubtree) migrateTarget = candidate },
                            enabled = !inSubtree,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (inSubtree) "${candidate.path}（将被删除）" else candidate.path,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { migrateTarget?.let { onMigrateTo(it.id) } },
                enabled = migrateTarget != null,
            ) { Text("迁移并删除分类") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDeleteTransactions) {
                    Text("连同流水一起删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

// --- 工具 ---

/** 把扁平分类列表按 parentId 关系展平成带缩进的行（深度优先）。 */
private fun buildRows(categories: List<Category>): List<CategoryRow> {
    val childrenByParent = categories.groupBy { it.parentId }
    val rows = mutableListOf<CategoryRow>()

    fun walk(parentId: Long?, depth: Int) {
        val children = childrenByParent[parentId].orEmpty()
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        children.forEach { node ->
            rows += CategoryRow(category = node, depth = depth)
            walk(node.id, depth + 1)
        }
    }

    walk(null, 0)
    return rows
}

/** 取某分类及其整棵子树的 id。 */
private fun subtreeIds(all: List<Category>, root: Category): List<Long> {
    val result = mutableListOf(root.id)
    var frontier = listOf(root.id)
    while (frontier.isNotEmpty()) {
        val next = all.filter { it.parentId in frontier }.map { it.id }
        result += next
        frontier = next
    }
    return result
}
