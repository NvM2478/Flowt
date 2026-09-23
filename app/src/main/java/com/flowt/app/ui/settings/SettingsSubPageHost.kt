package com.flowt.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flowt.app.data.bill.ImportMode
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.Transitions

/** 设置页内部可以进入的二级页面。 */
enum class SettingsPage {
    Root,
    CategoryManage,
    Metrics,
    Appearance,
    Data,
}

/**
 * 设置二级页的宿主。
 *
 * 为什么要在 [com.flowt.app.ui.FlowtApp] 这一层渲染，而不是在设置页内部：
 * 设置页本身被放在 Scaffold 的 content 槽里，它的"天花板"就是内容区 ——
 * 在它内部做的任何覆盖层都盖不住底部导航栏和 FAB。
 * 把二级页提到 Scaffold 外面，它才能真正全屏覆盖。
 *
 * 转场是**叠加式**：底层（设置首页、导航栏）静止不动，
 * 二级页像一张纸从右边盖上来；返回时向右滑走，把下面露出来。
 *
 * 三级页（导入）叠在二级页**之上**，用的是同一份 [Transitions] 规格 ——
 * 两层的动效必须一致，否则从数据管理点进导入时会明显感到"换了一套动画"。
 * 分层叠放也决定了返回的顺序：先收起三级页，回到数据管理，而不是一路弹回设置首页。
 */
@Composable
fun SettingsSubPageHost(
    vm: AppViewModel,
    page: SettingsPage,
    onClose: () -> Unit,
) {
    var overlayPage by remember { mutableStateOf(page) }

    // 三级页。模式由入口决定，所以它同时也是"这次要做什么"的载体。
    var importOpen by remember { mutableStateOf(false) }
    var importMode by remember { mutableStateOf(ImportMode.ExternalBill) }

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }

    LaunchedEffect(page) {
        if (page != SettingsPage.Root) {
            overlayPage = page
        } else {
            // 二级页整个关掉时，三级页不该还留在屏幕上
            importOpen = false
        }
    }

    // 三级页打开时，返回由它自己按阶段处理（放弃导入要先确认）
    BackHandler(enabled = page != SettingsPage.Root && !importOpen) { onClose() }

    AnimatedVisibility(
        visible = page != SettingsPage.Root,
        enter = Transitions.slideInFromRight(screenWidthPx),
        exit = Transitions.slideOutToRight(screenWidthPx),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (overlayPage) {
            SettingsPage.CategoryManage -> CategoryManageScreen(vm = vm, onBack = onClose)
            SettingsPage.Metrics -> MetricsSettingsScreen(vm = vm, onBack = onClose)
            SettingsPage.Appearance -> AppearanceSettingsScreen(vm = vm, onBack = onClose)

            SettingsPage.Data -> DataSettingsScreen(
                vm = vm,
                onBack = onClose,
                onStartImport = { mode ->
                    importMode = mode
                    importOpen = true
                },
            )

            SettingsPage.Root -> Unit
        }
    }

    // 三级页：导入。内容用 importMode 渲染，所以收起动画期间页面还在（而不是空白）
    AnimatedVisibility(
        visible = importOpen,
        enter = Transitions.slideInFromRight(screenWidthPx),
        exit = Transitions.slideOutToRight(screenWidthPx),
        modifier = Modifier.fillMaxSize(),
    ) {
        ImportScreen(
            vm = vm,
            mode = importMode,
            onClose = { importOpen = false },
        )
    }
}
