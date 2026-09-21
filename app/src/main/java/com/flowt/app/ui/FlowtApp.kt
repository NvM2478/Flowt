package com.flowt.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.ui.entry.EntryRevealHost
import com.flowt.app.ui.ledger.LedgerScreen
import com.flowt.app.ui.report.ReportScreen
import com.flowt.app.ui.settings.SettingsPage
import com.flowt.app.ui.settings.SettingsScreen
import com.flowt.app.ui.settings.SettingsSubPageHost
import kotlinx.coroutines.launch

private enum class AppTab(val label: String) {
    Ledger("流水"),
    Report("报表"),
    Settings("设置"),
}

/**
 * 把取景框平移到目标页。
 *
 * 动画规格来自 [Transitions]，和设置页二级菜单共用同一份 ——
 * 两处必须一致，否则会明显感到"一个快一个慢"。
 */
private suspend fun PagerState.scrollToTab(index: Int) {
    animateScrollToPage(page = index, animationSpec = Transitions.slide())
}

/**
 * 应用骨架。
 *
 * 结构：三个 tab（流水 / 报表 / 设置）+ 右下角 FAB 打开记账页。
 * 记账页不是 tab，而是一个全屏动作页 —— 这就是"四个页面如何平衡"的答案。
 *
 * 主页切换用 [HorizontalPager]：三个页面横向排成一条，屏幕相当于一个取景框，
 * 切换 tab 就是平移取景框。相比"两页各自做动画"，pager 的中间态是连续的条带，
 * 所以页面之间不会出现重叠或错位。
 */
@Composable
fun FlowtApp(vm: AppViewModel) {
    // 记账页/编辑页是同一个全屏动作页，靠 editTarget 区分：
    // null = 新建（从 FAB 进入），非 null = 编辑（点某张流水卡片进入）。
    var entryOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    // 揭示圆的圆心。它是"哪个元素被点"的直接结果：
    // 点 FAB 就是从 FAB 长出来，点某张卡片就是从那张卡片长出来。
    // 必须用 onGloballyPositioned 抓屏幕坐标 —— FAB 在 Scaffold 里、
    // 记账页在 Scaffold 外，两者布局容器不同，坐标系不共享。
    var revealCenter by remember { mutableStateOf<Offset?>(null) }
    // FAB 自身的屏幕坐标，点击时作为 revealCenter 的来源
    var fabCenterInRoot by remember { mutableStateOf<Offset?>(null) }

    // 设置页的二级页面状态提升到这里：它必须渲染在 Scaffold 外面才能盖住底部导航栏。
    var settingsPage by remember { mutableStateOf(SettingsPage.Root) }

    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { AppTab.entries.size })
    // pagerState.currentPage 本身就是 Compose 状态：手指滑动或点击 tab 让它变化时，
    // 这里会自动重组，导航栏高亮因此天然跟随，不需要任何手动同步。
    val currentTab = AppTab.entries[pagerState.currentPage]

    // 在非首页 tab 上按返回（侧滑/返回键）先回到「流水」，再按才退出应用。
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.scrollToTab(0) }
    }

    // 主界面始终挂载、永不淡出：揭示的圆直接盖在它上面。
    // 关键点是**不能卸载 Scaffold** —— 早期版本 showEntry 一变就 return，
    // 导致"点击瞬间首页全没了，然后圆才开始扩散"，底层空空如也。

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                scope.launch { pagerState.scrollToTab(index) }
                            },
                            icon = {},
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editTarget = null
                        // 圆心取 FAB 自身的位置
                        fabCenterInRoot?.let { revealCenter = it }
                        entryOpen = true
                    },
                    // Material 3 的 FAB 默认是圆角方形；这里强制正圆，
                    // 与"记账页从 FAB 位置长成一个圆"的揭示动画形状一致。
                    shape = CircleShape,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val bounds = coords.boundsInRoot()
                        fabCenterInRoot = Offset(
                            x = (bounds.left + bounds.right) / 2f,
                            y = (bounds.top + bounds.bottom) / 2f,
                        )
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "记一笔")
                }
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                // 只额外预组合相邻页，保持滑动流畅又不浪费
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { page ->
                when (AppTab.entries[page]) {
                    AppTab.Ledger -> LedgerScreen(
                        transactions = transactions,
                        categories = categories,
                        metrics = metrics,
                        onEdit = { transaction, cardCenter ->
                            editTarget = transaction
                            revealCenter = cardCenter
                            entryOpen = true
                        },
                        onLongPress = { transaction -> pendingDelete = transaction },
                    )

                    AppTab.Report -> ReportScreen(
                        transactions = transactions,
                        categories = categories,
                    )

                    AppTab.Settings -> SettingsScreen(
                        vm = vm,
                        onOpenCategoryManage = { settingsPage = SettingsPage.CategoryManage },
                        onOpenMetrics = { settingsPage = SettingsPage.Metrics },
                        onOpenAppearance = { settingsPage = SettingsPage.Appearance },
                        onOpenData = { settingsPage = SettingsPage.Data },
                    )
                }
            }
        }

        // 记账 / 编辑页的圆形揭示：在 Scaffold 之上，所以能看到圆盖在首页上扩散。
        EntryRevealHost(
            vm = vm,
            categories = categories,
            visible = entryOpen,
            revealCenter = revealCenter,
            editTarget = editTarget,
            onDeleteRequest = {
                // 从编辑页里点删除：关掉编辑页，再弹确认
                val target = editTarget
                entryOpen = false
                pendingDelete = target
            },
            onDismiss = {
                entryOpen = false
                editTarget = null
            },
        )

        // 长按流水卡片后的删除确认。
        // 这里不需要像删除分类那样处理"流水去向" —— 删的就是流水本身。
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除这笔流水？") },
                text = {
                    Text(
                        text = "删除后无法恢复。如果只是想改金额或分类，选「编辑」更合适。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                vm.txRepo.delete(target)
                                pendingDelete = null
                            }
                        },
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                },
            )
        }

        // 二级设置页：全屏覆盖层，在 Scaffold 之上 —— 所以能盖住底部导航栏和 FAB。
        SettingsSubPageHost(
            vm = vm,
            page = settingsPage,
            onClose = { settingsPage = SettingsPage.Root },
        )
    }
}
