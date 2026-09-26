package com.flowt.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.ui.entry.EntryRevealHost
import com.flowt.app.ui.ledger.LedgerScreen
import com.flowt.app.ui.report.ReportScreen
import com.flowt.app.ui.settings.SettingsPage
import com.flowt.app.ui.settings.SettingsScreen
import com.flowt.app.ui.settings.SettingsSubPageHost
import com.flowt.app.ui.theme.FlowtColors
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
    // 二级页关闭后该落回哪个 tab。
    // 从首页长按指标卡进来时记录的是"流水"，从设置 tab 进来时是"设置" ——
    // 这样无论从哪进，关掉都回到出发点，不会把用户甩到别的页面。
    var returnTabOnClose by remember { mutableStateOf<AppTab?>(null) }

    val transactions by vm.transactions.collectAsState()
    val categories by vm.categories.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val lowContrastNotice by vm.lowContrastNotice.collectAsState()
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
                NavigationBar(
                    containerColor = FlowtColors.current.navigationBarBackground,
                ) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = {
                                scope.launch { pagerState.scrollToTab(index) }
                            },
                            icon = {},
                            label = { Text(tab.label) },
                            // 字色统一由「导航栏文字」角色控制：未选中自动淡化，
                            // 选中实色（落在选中块上，与「导航栏选中块」搭配调整）
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FlowtColors.current.navigationBarText,
                                selectedTextColor = FlowtColors.current.navigationBarText,
                                unselectedIconColor = FlowtColors.current.navigationBarText.copy(alpha = 0.6f),
                                unselectedTextColor = FlowtColors.current.navigationBarText.copy(alpha = 0.6f),
                                indicatorColor = FlowtColors.current.navigationIndicator,
                            ),
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
                    // 记账页/流水条目中性化后，主色不再有默认消费槽 —— FAB 显式用主色，
                    // 让它成为界面上稳定的强调点（现代记账 app 的惯例）
                    containerColor = FlowtColors.current.accentBackground,
                    contentColor = FlowtColors.current.accentButtonText,
                    // Material 3 的 FAB 默认是圆角方形；这里强制正圆，
                    // 与"记账页从 FAB 位置长成一个圆"的揭示动画形状一致。
                    shape = CircleShape,
                    modifier = Modifier
                        .semantics { contentDescription = "记一笔" }
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInRoot()
                            fabCenterInRoot = Offset(
                                x = (bounds.left + bounds.right) / 2f,
                                y = (bounds.top + bounds.bottom) / 2f,
                            )
                        },
                ) {
                    // 加号刻意用记账页底色：揭场圆从 FAB 中心长出来时，最先被圆盖住的就是
                    // 加号 —— 两者同色，加号无缝"溶"进圆里，起跳零跳变（真机实测确认的衔接方案）
                    PlusGlyph(color = FlowtColors.current.entryPageBackground)
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
                        // 长按指标卡直达「首页指标」设置。
                        // 记录出发点（流水页），关闭二级页时回到这里 ——
                        // 刻意**不改底部 tab**：用户只是打开一个二级菜单，
                        // 不该顺手把主页面也切走。
                        onLongPressMetric = {
                            returnTabOnClose = AppTab.Ledger
                            settingsPage = SettingsPage.Metrics
                        },
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
                        onOpenAbout = { settingsPage = SettingsPage.About },
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
                        Text("删除", color = FlowtColors.current.dangerAccent)
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
            onClose = {
                settingsPage = SettingsPage.Root
                // 回到进入二级页之前的那个 tab（从首页长按进来的就回流水页）。
                // 清掉标记，避免影响下一次从设置 tab 正常进入的情况。
                returnTabOnClose?.let { tab ->
                    scope.launch { pagerState.scrollToTab(tab.ordinal) }
                }
                returnTabOnClose = null
            },
        )

        // 低对比度颜色的待决胶囊：悬浮于一切页面之上（含二级页），
        // 用户可以带着它浏览各页面的实际效果，表态前不消失
        lowContrastNotice?.let {
            LowContrastBanner(
                onKeep = vm::keepLowContrastColor,
                onRevert = vm::revertLowContrastColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * FAB 的加号：Material 图标是笔画固定的矢量，没有"调粗"的余地，所以自绘。
 * 尺寸（26dp）和线宽（2.5dp）都可控，圆头端点与 Material 图标的观感一致。
 */
@Composable
private fun PlusGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val strokeWidth = 3.dp.toPx()
        val half = (size.minDimension - strokeWidth) / 2f
        drawLine(
            color = color,
            start = Offset(center.x - half, center.y),
            end = Offset(center.x + half, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, center.y - half),
            end = Offset(center.x, center.y + half),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
