package com.flowt.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.BrightnessMode
import com.flowt.app.data.SavedTheme
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.CONTRAST_READABLE
import com.flowt.app.ui.theme.FlowtColors
import com.flowt.app.ui.theme.LocalSystemDark
import com.flowt.app.ui.theme.LocalWallpaperEpoch
import com.flowt.app.ui.theme.PresetTheme
import com.flowt.app.ui.theme.RoleGroup
import com.flowt.app.ui.theme.RoleKey
import com.flowt.app.ui.theme.RoleKind
import com.flowt.app.ui.theme.THEME_ID_SYSTEM_DYNAMIC
import com.flowt.app.ui.theme.baseValueOf
import com.flowt.app.ui.theme.contrastRatio
import com.flowt.app.ui.theme.dynamicSchemeFromWallpaper
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.launch

/** 改底色时发现配对文字被用户自定义过、且新底色下对比度不足 → 保护提示。 */
private data class PairConflict(val onRole: RoleKey, val ratio: Double)

/** 待确认的切换：配色方案或明暗模式，两者共用同一条「放弃未保存」确认链路。 */
private sealed interface PendingSwitch {
    data class Scheme(val id: String) : PendingSwitch
    data class Brightness(val mode: BrightnessMode) : PendingSwitch
}

/**
 * 外观设置。
 *
 * 结构（按已定的设计）：
 * 1. 主题 —— 明暗模式（跟随系统 / 浅色 / 深色），下拉单选；
 * 2. 配色方案 —— 「自动」（壁纸取色）与内置预设，单选；切换任意方案都会
 *    清掉未保存的自定义覆盖，这就是回到默认的途径；
 * 3. 自定义颜色 —— 在所选方案之上微调单个角色。底色（BASE）随便改；
 *    配对文字（ON）默认自动匹配底色深浅，手动改过且对比度不足时，
 *    应用后会浮出全局待决胶囊供浏览与撤销。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    val colors = FlowtColors.current

    var editingRole by remember { mutableStateOf<RoleKey?>(null) }
    var pendingSwitch by remember { mutableStateOf<String?>(null) }
    // 明暗切换与方案切换共用同一条「放弃未保存」确认链路
    var pendingBrightness by remember { mutableStateOf<BrightnessMode?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var pairConflict by remember { mutableStateOf<PairConflict?>(null) }

    val hasUnsaved = prefs.roleOverrides.isNotEmpty()
    // 「我的方案」是完整自包含的固定颜色，不分明暗 —— 主题卡片此时置灰
    val savedSelected = prefs.selectedThemeId.startsWith(
        com.flowt.app.data.PrefsRepository.SAVED_PREFIX,
    )
    // 方案预览跟随生效明暗：主题为「跟随系统」时取系统当前值
    val effectiveDark = when (prefs.brightnessMode) {
        BrightnessMode.LIGHT -> false
        BrightnessMode.DARK -> true
        BrightnessMode.SYSTEM -> LocalSystemDark.current
    }

    BackHandler { onBack() }

    /**
     * 应用一个角色颜色，附两道一致性/安全逻辑：
     * - 选中的颜色 == 该角色的默认色 → 等同于撤销修改，清覆盖而非写入
     *   （否则默认色会被固化成覆盖值，出现假的"已修改"标记）；
     * - 配对文字（ON）且对比度不足 → 照常应用，浮出全局待决胶囊供浏览与撤销；
     * - 底色（BASE）被改时，若它的某个配对文字处于覆盖态且新底色下对比度不足 →
     *   弹提示让用户选择"保持自定义 / 恢复自动匹配"。派生态的配对文字自动重算，无需处理。
     */
    fun applyRoleColor(role: RoleKey, argb: Int) {
        editingRole = null
        scope.launch {
            if (argb == colors.defaultRole(role).toArgb()) {
                // 选回默认 = 撤销修改：默认色必然达标，无需任何检查
                vm.prefsRepo.clearRoleOverride(role.name)
                return@launch
            }
            vm.prefsRepo.setRoleOverride(role.name, argb)
            when (role.kind) {
                RoleKind.ON -> {
                    val base = role.pairsWith?.let { colors.role(it) }
                    if (base != null && contrastRatio(Color(argb), base) < CONTRAST_READABLE) {
                        // 应用后浮出全局待决胶囊：用户带着它浏览各页面，保留或撤销
                        vm.showLowContrastNotice(role.name)
                    }
                }

                RoleKind.BASE -> {
                    val conflict = RoleKey.entries
                        .filter { it.kind == RoleKind.ON && it.pairsWith == role }
                        .mapNotNull { onRole ->
                            val overriddenArgb = prefs.roleOverrides[onRole.name] ?: return@mapNotNull null
                            onRole to contrastRatio(Color(overriddenArgb), Color(argb))
                        }
                        .filter { (_, ratio) -> ratio < CONTRAST_READABLE }
                        .minByOrNull { (_, ratio) -> ratio }
                    if (conflict != null) pairConflict = PairConflict(conflict.first, conflict.second)
                }

                RoleKind.FREE -> Unit
            }
        }
    }

    /** 切换方案：有未保存的自定义时先弹确认。 */
    fun requestSwitch(selectionId: String) {
        if (selectionId == prefs.selectedThemeId) return
        if (hasUnsaved) {
            pendingSwitch = selectionId
        } else {
            scope.launch { vm.prefsRepo.setSelectedTheme(selectionId) }
        }
    }

    /**
     * 切换明暗：与方案切换同一确认链路，但只在**实际明暗变化**时才需要放弃微调——
     * 跟随系统切浅色且系统本是浅色 = 什么都没变，微调保留、不打扰。
     */
    fun requestBrightness(mode: BrightnessMode) {
        if (mode == prefs.brightnessMode) return
        // effectiveDark 在「跟随系统」时就是系统明暗，直接复用组合作用域里已算好的值
        val darkAfter = when (mode) {
            BrightnessMode.LIGHT -> false
            BrightnessMode.DARK -> true
            BrightnessMode.SYSTEM -> effectiveDark
        }
        if (darkAfter == effectiveDark) {
            scope.launch { vm.prefsRepo.setBrightnessMode(mode, clearUnsaved = false) }
            return
        }
        if (hasUnsaved) {
            pendingBrightness = mode
        } else {
            scope.launch { vm.prefsRepo.setBrightnessMode(mode, clearUnsaved = true) }
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
                title = { Text("外观") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 「保存为新方案」常驻顶栏：有未保存的修改才可点，否则置灰
                    TextButton(
                        onClick = { showSaveDialog = true },
                        enabled = hasUnsaved,
                    ) {
                        Text(
                            text = "保存",
                            color = if (hasUnsaved) {
                                FlowtColors.current.navigationBarText
                            } else {
                                FlowtColors.current.navigationBarText.copy(alpha = 0.4f)
                            },
                        )
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
            item(key = "scheme_title") { SectionLabel("主题") }

            // 卡片一：明暗模式，单行 + 下拉选择。选中「我的方案」时置灰 —— 固定颜色不分明暗
            item(key = "theme_card") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .alpha(if (savedSelected) 0.45f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("明暗模式", style = MaterialTheme.typography.bodyLarge)
                            // 选中「我的方案」时解释置灰原因：固定颜色不分明暗
                            if (savedSelected) {
                                Text(
                                    text = "「我的方案」保存的是固定颜色，不受明暗模式影响",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtleTextColor(),
                                )
                            }
                        }

                        var themeMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { if (!savedSelected) themeMenuOpen = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    when (prefs.brightnessMode) {
                                        BrightnessMode.SYSTEM -> "跟随系统"
                                        BrightnessMode.LIGHT -> "浅色"
                                        BrightnessMode.DARK -> "深色"
                                    },
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择明暗模式")
                            }
                            DropdownMenu(
                                expanded = themeMenuOpen,
                                onDismissRequest = { themeMenuOpen = false },
                            ) {
                                listOf(
                                    BrightnessMode.SYSTEM to "跟随系统",
                                    BrightnessMode.LIGHT to "浅色",
                                    BrightnessMode.DARK to "深色",
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            themeMenuOpen = false
                                            requestBrightness(mode)
                                        },
                                        trailingIcon = if (prefs.brightnessMode == mode) {
                                            { Icon(Icons.Filled.Check, contentDescription = "已选中") }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "palette_title") { SectionLabel("配色方案") }

            // 卡片二：配色方案（明暗由上面的主题决定，浅色/深色不再重复列出）
            item(key = "palette_card") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val context = LocalContext.current
                        // 「自动」= 跟随系统取色。预览与实际应用共用同一个取色函数
                        // （壁纸三色自推导 + 雾化），色块即所得；壁纸版本变化时重新推导
                        val dynamicScheme = remember(
                            effectiveDark,
                            LocalWallpaperEpoch.current,
                        ) {
                            dynamicSchemeFromWallpaper(context, effectiveDark)
                        }
                        SchemeRow(
                            name = "自动",
                            previewColors = previewColorsOf(dynamicScheme),
                            selected = prefs.selectedThemeId == THEME_ID_SYSTEM_DYNAMIC,
                            onClick = { requestSwitch(THEME_ID_SYSTEM_DYNAMIC) },
                        )
                        PresetTheme.all.forEach { preset ->
                            SchemeRow(
                                name = preset.displayName,
                                previewColors = previewColorsOf(
                                    if (effectiveDark) preset.dark else preset.light,
                                ),
                                selected = prefs.selectedThemeId == preset.id,
                                onClick = { requestSwitch(preset.id) },
                            )
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
                                    previewColors = previewOfSaved(saved, colors),
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
                    text = "在所选方案之上微调。底色随便改，配对的文字会自动匹配深浅；" +
                        "手动改过的文字若与新底色对比不足，会先展示对比度再请你确认。",
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
                                        color = colors.role(role),
                                        overridden = prefs.roleOverrides.containsKey(role.name),
                                        onClick = { editingRole = role },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }

    // 切换方案 / 明暗时的二次确认（两者都会放弃未保存的微调）
    val pendingTarget = pendingSwitch?.let { PendingSwitch.Scheme(it) }
        ?: pendingBrightness?.let { PendingSwitch.Brightness(it) }
    pendingTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                pendingSwitch = null
                pendingBrightness = null
            },
            title = { Text("放弃未保存的颜色？") },
            text = {
                Text(
                    "你对当前配色做了修改但还没保存。切换会清空这些修改。\n\n" +
                        "想保留的话，先取消，然后点击右上角「保存」。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            when (target) {
                                is PendingSwitch.Scheme -> vm.prefsRepo.setSelectedTheme(target.id)
                                is PendingSwitch.Brightness -> vm.prefsRepo.setBrightnessMode(target.mode, clearUnsaved = true)
                            }
                            pendingSwitch = null
                            pendingBrightness = null
                        }
                    },
                ) {
                    Text("放弃并切换", color = FlowtColors.current.dangerAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSwitch = null
                        pendingBrightness = null
                    },
                ) { Text("取消") }
            },
        )
    }

    if (showSaveDialog) {
        SaveThemeDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                scope.launch {
                    // 存"全部角色的生效值"而不是只存差异：这样方案自包含，
                    // 删除它不影响任何东西，切回来也不会依赖当时的基底是什么。
                    // 派生态的配对文字另外记录角色名 —— 应用方案时它们继续自动匹配。
                    val complete = RoleKey.entries.associate { role ->
                        role.name to colors.role(role).toArgb()
                    }
                    val derived = RoleKey.entries
                        .filter { it.kind == RoleKind.ON && !prefs.roleOverrides.containsKey(it.name) }
                        .map { it.name }
                        .toSet()
                    vm.prefsRepo.saveCurrentAsTheme(name, complete, derived)
                }
                showSaveDialog = false
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("只删除这个保存的方案。如果你正在使用它，会切回「自动」配色方案。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { vm.prefsRepo.deleteSavedTheme(target.name) }
                        deleteTarget = null
                    },
                ) {
                    Text("删除", color = FlowtColors.current.dangerAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 改底色撞上自定义过的配对文字：给用户选择，绝不悄悄覆盖
    pairConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pairConflict = null },
            title = { Text("「${conflict.onRole.displayName}」可能看不清") },
            text = {
                Text(
                    "你自定义过「${conflict.onRole.displayName}」，" +
                        "它在刚设置的底色下对比度只有 ${"%.1f".format(conflict.ratio)}:1。\n\n" +
                        "恢复自动匹配会让它跟着新底色重新配色；保持自定义则维持你选的颜色。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { vm.prefsRepo.clearRoleOverride(conflict.onRole.name) }
                        pairConflict = null
                    },
                ) { Text("恢复自动匹配") }
            },
            dismissButton = {
                TextButton(onClick = { pairConflict = null }) { Text("保持自定义") }
            },
        )
    }

    editingRole?.let { role ->
        RoleColorPickerSheet(
            role = role,
            dark = effectiveDark,
            currentColor = colors.role(role),
            defaultColor = colors.defaultRole(role),
            onPick = { argb -> applyRoleColor(role, argb) },
            onDismiss = { editingRole = null },
        )
    }
}

/** 一行方案：色块预览 + 名称 + 选中标记（可选尾部操作）。 */
@Composable
private fun SchemeRow(
    name: String,
    previewColors: List<Color>,
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
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            previewColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, FlowtColors.current.outlineBorder, CircleShape),
                )
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

/** 一行角色：名称 + 当前色块 + 状态徽标（已修改 / 自动匹配中）。 */
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
                Spacer(Modifier.size(6.dp))
                when {
                    overridden -> Text(
                        text = "已修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = FlowtColors.current.accentInlineText,
                    )

                    role.kind == RoleKind.ON -> Text(
                        text = "自动匹配中",
                        style = MaterialTheme.typography.labelSmall,
                        color = subtleTextColor(),
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
                .border(1.dp, FlowtColors.current.outlineBorder, CircleShape),
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

/**
 * 方案行预览色的**代表角色顺序表** —— 自动 / 内置 / 自定义三类方案共用。
 * 想调整方案行展示哪几个角色、按什么顺序展示，只改这一张表。
 */
private val previewRoles = listOf(
    RoleKey.Primary,                 // 强调
    RoleKey.PageBackground,          // 页面底
    RoleKey.CardSurface,             // 卡片底
    RoleKey.EntryPageSurface,        // 记账页 / 条目底
    RoleKey.PrimaryContainer,        // 指标卡底
    RoleKey.NavigationBarBackground, // 导航栏底
)

/** 从一份配色方案（ColorScheme）按代表角色表取预览色。 */
private fun previewColorsOf(scheme: androidx.compose.material3.ColorScheme): List<Color> =
    previewRoles.map { role -> baseValueOf(role, scheme, dark = false) }

/** 保存方案的预览色：优先用方案自己存的角色值，缺失（旧数据）回退当前生效色。 */
private fun previewOfSaved(saved: SavedTheme, colors: FlowtColors): List<Color> =
    previewRoles.map { role -> saved.overrides[role.name]?.let { Color(it) } ?: colors.role(role) }
