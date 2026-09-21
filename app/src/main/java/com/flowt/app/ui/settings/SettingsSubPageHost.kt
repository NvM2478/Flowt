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
 * 二级设置页的宿主。
 *
 * 为什么要在 [com.flowt.app.ui.FlowtApp] 这一层渲染，而不是在设置页内部：
 * 设置页本身被放在 Scaffold 的 content 槽里，它的"天花板"就是内容区 ——
 * 在它内部做的任何覆盖层都盖不住底部导航栏和 FAB。
 * 把二级页提到 Scaffold 外面，它才能真正全屏覆盖。
 *
 * 转场是**叠加式**：底层（设置首页、导航栏）静止不动，
 * 二级页像一张纸从右边盖上来；返回时向右滑走，把下面露出来。
 */
@Composable
fun SettingsSubPageHost(
    vm: AppViewModel,
    page: SettingsPage,
    onClose: () -> Unit,
) {
    var overlayPage by remember { mutableStateOf(page) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }

    LaunchedEffect(page) {
        if (page != SettingsPage.Root) overlayPage = page
    }

    BackHandler(enabled = page != SettingsPage.Root) { onClose() }

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
            SettingsPage.Data -> DataSettingsScreen(onBack = onClose)
            SettingsPage.Root -> Unit
        }
    }
}
