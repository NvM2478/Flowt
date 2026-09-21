package com.flowt.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.SavedTheme
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.ColorPalette
import com.flowt.app.ui.theme.RoleGroup
import com.flowt.app.ui.theme.RoleKey
import com.flowt.app.ui.theme.THEME_ID_SYSTEM_DYNAMIC
import com.flowt.app.ui.theme.ThemeVariant
import com.flowt.app.ui.theme.resolveRoleColor
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.launch

/**
 * 外观设置。
 *
 * 结构（按已定的设计）：
 * 1. 配色方案 —— 浅色组 / 深色组 / 我的方案，单选。**浅暗不再是独立设置项**，
 *    而是拆成两套并列的方案，用户能直接看到"暗色版长什么样"再选。
 * 2. 自定义颜色 —— 在所选方案之上微调单个角色。
 * 3. 底部固定可见的「恢复默认颜色」—— 颜色**不跟随主题**，保证任何配色下都看得见。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    // 只影响「支出金额」的预览色：它不跟随主题，但分深浅两版
    val isDark = isSystemInDarkTheme()

    var editingRole by remember { mutableStateOf<RoleKey?>(null) }
    var pendingSwitch by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedTheme?>(null) }

    val hasUnsaved = prefs.roleOverrides.isNotEmpty()

    BackHandler { onBack() }

    /** 切换方案：有未保存的自定义时先弹确认。 */
    fun requestSwitch(selectionId: String) {
        if (selectionId == prefs.selectedThemeId) return
        if (hasUnsaved) {
            pendingSwitch = selectionId
        } else {
            scope.launch { vm.prefsRepo.setSelectedTheme(selectionId) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
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
            item(key = "scheme_title") { SectionLabel("配色方案") }

            item(key = "system") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SchemeRow(
                        name = "跟随系统取色",
                        previewColors = null,
                        selected = prefs.selectedThemeId == THEME_ID_SYSTEM_DYNAMIC,
                        onClick = { requestSwitch(THEME_ID_SYSTEM_DYNAMIC) },
                    )
                }
            }

            // 内置方案：按浅色 / 深色分组
            listOf(false to "浅色", true to "深色").forEach { (dark, label) ->
                item(key = "group_${label}") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            GroupLabel(label)
                            ThemeVariant.builtIn
                                .filter { it.isDark == dark }
                                .forEach { variant ->
                                    SchemeRow(
                                        name = variant.displayName.substringBefore(" · "),
                                        previewColors = variant.previewColors(),
                                        selected = prefs.selectedThemeId == variant.id,
                                        onClick = { requestSwitch(variant.id) },
                                    )
                                }
                        }
                    }
                }
            }

            // 用户保存的方案
            if (prefs.savedThemes.isNotEmpty()) {
                item(key = "group_saved") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            GroupLabel("我的方案")
                            prefs.savedThemes.forEach { saved ->
                                SchemeRow(
                                    name = saved.name,
                                    previewColors = previewOfSaved(saved, scheme),
                                    selected = prefs.selectedThemeId ==
                                        "${com.flowt.app.data.PrefsRepository.SAVED_PREFIX}${saved.name}",
                                    onClick = {
                                        requestSwitch(
                                            "${com.flowt.app.data.PrefsRepository.SAVED_PREFIX}${saved.name}"
                                        )
                                    },
                                    trailing = {
                                        IconButton(onClick = { deleteTarget = saved }) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除方案",
                                                tint = subtleTextColor(),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // --- 自定义颜色 ---
            item(key = "roles_title") { SectionLabel("自定义颜色") }
            item(key = "roles_hint") {
                Text(
                    text = "在所选方案之上微调单个颜色。没改的继续跟随方案；" +
                        "成对的颜色（如底色与文字）建议一起调，只改一个可能看不清。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            RoleGroup.entries.forEach { group ->
                item(key = "role_group_${group.name}") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            GroupLabel(group.displayName)
                            RoleKey.entries
                                .filter { it.group == group }
                                .forEach { role ->
                                    RoleColorRow(
                                        role = role,
                                        color = resolveRoleColor(
                                            role = role,
                                            base = scheme,
                                            overrides = prefs.roleOverrides,
                                            dark = isDark,
                                        ),
                                        overridden = prefs.roleOverrides.containsKey(role.name),
                                        onClick = { editingRole = role },
                                    )
                                }
                        }
                    }
                }
            }

            // --- 底部操作 ---
            if (hasUnsaved) {
                item(key = "save") {
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("保存为新方案")
                    }
                }
            }

            item(key = "reset") {
                // 刻意用固定配色（深底白字），不跟随主题：
                // 用户可能把配色改得一团糟，这个按钮必须在这种状态下依然清晰可见。
                Button(
                    onClick = { requestSwitch(THEME_ID_SYSTEM_DYNAMIC) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xE61C1B1F),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text("恢复默认颜色")
                }
            }

            item(key = "reset_hint") {
                Text(
                    text = "恢复默认会把配色切回「跟随系统取色」，并清掉当前的自定义颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    // 切换方案时的二次确认
    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("放弃未保存的颜色？") },
            text = {
                Text(
                    "你对当前配色做了修改但还没保存。切换方案会清空这些修改。\n\n" +
                        "想保留的话，先取消，然后点「保存为新方案」。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { vm.prefsRepo.setSelectedTheme(target) }
                        pendingSwitch = null
                    },
                ) {
                    Text("放弃并切换", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwitch = null }) { Text("取消") }
            },
        )
    }

    if (showSaveDialog) {
        SaveThemeDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                scope.launch {
                    // 存"完整的 8 个角色值"而不是只存差异：这样方案自包含，
                    // 删除它不影响任何东西，切回来也不会依赖当时的基底是什么。
                    val complete = RoleKey.entries.associate { role ->
                        role.name to resolveRoleColor(role, scheme, prefs.roleOverrides).value.toInt()
                    }
                    vm.prefsRepo.saveCurrentAsTheme(name, complete)
                }
                showSaveDialog = false
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("只删除这个保存的方案。如果你正在使用它，会切回「跟随系统取色」。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { vm.prefsRepo.deleteSavedTheme(target.name) }
                        deleteTarget = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    editingRole?.let { role ->
        RoleColorPickerSheet(
            role = role,
            currentColor = resolveRoleColor(
                role = role,
                base = scheme,
                overrides = prefs.roleOverrides,
                dark = isDark,
            ),
            canReset = prefs.roleOverrides.containsKey(role.name),
            onPick = { argb ->
                scope.launch { vm.prefsRepo.setRoleOverride(role.name, argb) }
                editingRole = null
            },
            onReset = {
                scope.launch { vm.prefsRepo.clearRoleOverride(role.name) }
                editingRole = null
            },
            onDismiss = { editingRole = null },
        )
    }
}

/** 一行方案：色块预览 + 名称 + 选中标记（可选尾部操作）。 */
@Composable
private fun SchemeRow(
    name: String,
    previewColors: List<Color>?,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (previewColors == null) {
            // "跟随系统取色"没法预览（取决于壁纸），用中性圆点占位
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                previewColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = subtleTextColor(),
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** 一行角色：名称 + 当前色块 + 是否被自定义过。 */
@Composable
private fun RoleColorRow(
    role: RoleKey,
    color: Color,
    overridden: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(role.displayName, style = MaterialTheme.typography.bodyLarge)
                if (overridden) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "已修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            role.pairHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = subtleTextColor(),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SaveThemeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("我的配色") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存为新方案") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("方案名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "会保存当前的全部颜色值，之后可在「我的方案」里选用或删除。同名会覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 保存方案的预览色：直接从它自己存的角色值里取（取四个最能代表观感的角色）。 */
private fun previewOfSaved(
    saved: SavedTheme,
    fallback: androidx.compose.material3.ColorScheme,
): List<Color> {
    fun pick(role: RoleKey) = resolveRoleColor(role, fallback, saved.overrides)
    return listOf(
        pick(RoleKey.Primary),
        pick(RoleKey.PrimaryContainer),
        pick(RoleKey.EntryPageSurface),
        pick(RoleKey.PageBackground),
    )
}
