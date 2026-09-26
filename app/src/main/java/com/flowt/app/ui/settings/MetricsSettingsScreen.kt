package com.flowt.app.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.flowt.app.metrics.LedgerMetric
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.components.MetricCard
import com.flowt.app.ui.theme.FlowtColors
import com.flowt.app.ui.theme.subtleTextColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.time.Duration.Companion.milliseconds

private const val HINT_KEY = "hint"
private const val SHOWN_HEADER_KEY = "header_shown"
private const val HIDDEN_HEADER_KEY = "header_hidden"

/**
 * 网格下标 → 卡片序号的偏移。
 *
 * "显示在首页"这张卡片**之前**有两个占满整行的项：提示行 + 分区标题。
 * 所以第一张卡片的网格下标是 2、序号是 0 —— 偏移量必须是 **2**。
 * （这个值曾经被误写成 1，导致换位目标整体偏一格，表现是拖动跨位时卡片跳离手指。）
 */
private const val ITEM_INDEX_OFFSET = 2

/** 长按托起后的放大倍数：要能明显看出"它被拿起来了"。 */
private const val DRAG_SCALE = 1.08f

/**
 * 徽标翻转前的延迟。
 *
 * 设成 0：渐变本身就是"平滑过渡"，不再需要靠延迟去对齐换位动画的时机。
 * （之前设 260ms 反而让人以为没有动画 —— 在它开始之前观感就是"没反应"。）
 */
private const val BADGE_FLIP_DELAY_MS = 0L

/**
 * 首页指标设置。
 *
 * 交互：
 * - **上方区（显示在首页）**：长按可拖动换位（仅区内）；点一下 → 下方区**最后一个**位置；
 * - **下方区（未显示）**：不可拖动；点一下 → 上方区**最后一个**位置。
 *
 * 三个踩过的坑记在这里，免得重蹈：
 *
 * 1. **索引偏移常量必须是 2**（见 [ITEM_INDEX_OFFSET]）。写错会让换位目标整体偏一格，
 *    表现是拖动跨位时卡片突然跳离手指、此后再也跟不上 —— 看着像"库有 bug"，
 *    其实是我自己算错了目标位置。
 * 2. **不要在这张卡片上再叠 `graphicsLayer` 的缩放/阴影**：库已经在 ReorderableItem
 *    那一层用 graphicsLayer 做拖动位移，嵌套坐标变换会造成偏移。拖动反馈交给震动。
 * 3. **不要尝试让下方区也可拖动（跨区拖动）**。试过一次，结果是乱跳、不跟手。
 *    原因是库的追踪公式 `draggingItemDraggedDelta + (初始槽位 - 当前槽位)` 建立在
 *    "被拖项始终属于同一个集合"之上；跨区会让它换到另一个 items() 块，
 *    那个槽位差值失去意义，而库里 reorderableKeys 是两个区共用的一份状态，
 *    跨区会同时扰动两边的追踪。这是追踪模型层面的限制，改参数解决不了。
 *    两区之间的搬运交给点击，规则明确且不会出错。
 *
 * 拖拽换位用社区库 `sh.calvin.reorderable`，不手搓 —— 让位动画、边缘自动滚动、
 * 长按触发、拖动提升层都由它负责。
 *
 * 库文档明确警告的约束：[rememberReorderableLazyGridState] 的 onMove 回调
 * **必须同步改完列表再返回**，不能包在 launch 里，否则拖动项会闪烁跳动。
 * 因此持久化写在拖动结束时（[ReorderableItem] 的 onDragStopped），而不是 onMove 里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsSettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    val metrics by vm.allMetrics.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val amountById = remember(metrics) { metrics.associate { it.id to it.amountCents } }

    val enabled = remember { mutableStateListOf<String>() }
    val disabled = remember { mutableStateListOf<String>() }

    LaunchedEffect(prefs.enabledMetrics, prefs.disabledMetrics) {
        if (enabled.toList() != prefs.enabledMetrics) {
            enabled.clear()
            enabled.addAll(prefs.enabledMetrics)
        }
        if (disabled.toList() != prefs.disabledMetrics) {
            disabled.clear()
            disabled.addAll(prefs.disabledMetrics)
        }
    }

    fun persist() {
        scope.launch { vm.prefsRepo.setMetricOrder(enabled.toList(), disabled.toList()) }
    }

    /**
     * 分区标题是否已经可以显示。
     *
     * 首次进入时，卡片项要经过两趟测量才定下高度；而标题在第一趟就被摆好了，
     * 于是会先停在偏上的位置、第二趟再被推到正确位置 —— 观感就是"标题飘一下"。
     * 这里让标题等两帧（等布局稳定）再显形。
     *
     * 注意是**淡入而不是延迟挂载**：标题始终占位，只是透明。
     * 若改成"晚一点才渲染"，它出现时会把下面的卡片整体顶下去，反而多出一次跳动。
     */
    // 刻意不用 by 委托：这个文件里 by + getValue 的解析反复出问题
    // （K2 报 getValue 多载歧义）。直接持有 MutableState、读写 .value 最稳。
    val headerVisible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 等两帧（约 32ms）让卡片完成测量、网格布局稳定后再显形。
        delay(32.milliseconds)
        headerVisible.value = true
    }
    val headerAlpha by animateFloatAsState(
        targetValue = if (headerVisible.value) 1f else 0f,
        animationSpec = tween(durationMillis = 100),
        label = "header_alpha",
    )

    /**
     * 每个指标当前**显示**的徽标状态，按 id 保存。
     *
     * 为什么必须提升到这里、而不能放在卡片内部用 remember：
     * 点击搬运时卡片会从上方区的 items() 块移动到下方区的 items() 块，
     * Lazy 会把它当成"销毁 + 新建"，那张卡片内部的 remember 会被重置 ——
     * 徽标直接以新值初始化，**从旧值到新值的变化过程根本不存在，动画自然不会播**。
     * 放在这里，状态的生命周期就跟着指标 id 走，跨块移动也不受影响。
     */
    val badgeStates = remember { mutableStateMapOf<String, BadgeKind>() }
    // 让"显示的徽标"逐渐追上"实际的徽标"。
    //
    // 首次进入时必须**立即**写正、不能延迟：那时 map 还是空的，
    // 若也等 BADGE_FLIP_DELAY_MS，徽标会先按兜底值显示、再翻一次，出现无谓的闪动。
    // 只有"已经在页面上、归属发生变化"才需要延迟，让渐变接在换位动画之后。
    LaunchedEffect(enabled.toList(), disabled.toList()) {
        val firstSync = badgeStates.isEmpty()
        if (!firstSync) delay(BADGE_FLIP_DELAY_MS.milliseconds)
        enabled.forEach { badgeStates[it] = BadgeKind.Remove }
        disabled.forEach { badgeStates[it] = BadgeKind.Add }
    }

    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        val toIndex = to.index - ITEM_INDEX_OFFSET
        val fromId = from.key as? String ?: return@rememberReorderableLazyGridState

        // 只处理"显示区内换位"：两端都必须在这个列表里。
        // 落点落在提示行 / 分区标题 / 未显示区时一律忽略（那会让 toIndex 越界或指向别的区）。
        if (toIndex < 0 || toIndex >= enabled.size) return@rememberReorderableLazyGridState

        val fromIdx = enabled.indexOf(fromId)
        if (fromIdx >= 0 && fromIdx != toIndex) {
            enabled.add(toIndex, enabled.removeAt(fromIdx))
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
                title = { Text("首页指标") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = HINT_KEY, span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "上方可长按拖动排序；点一下在两区之间搬运。",
                    style = MaterialTheme.typography.bodySmall,
                    color = subtleTextColor(),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            item(key = SHOWN_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                // animateItem 只在布局稳定后才挂。
                //
                // 首次进入时卡片要两趟测量才定高，标题会先落在偏上的位置、再被推下来。
                // 若这时挂着 animateItem，那次位移就成了一段 120ms 的平移动画 ——
                // 即便标题是透明的，这段"飘"依然看得见（就是之前反复没修掉的那个现象）。
                // 不挂的话位移是瞬移，透明期间完成，看不见；稳定后再挂上，
                // 之后点击/拖动引起的行数变化仍有平滑动画。
                SectionHeader(
                    title = "显示在首页",
                    count = enabled.size,
                    modifier = Modifier
                        .graphicsLayer { alpha = headerAlpha }
                        .then(
                            if (headerVisible.value) {
                                Modifier.animateItem(placementSpec = tween(durationMillis = 120))
                            } else {
                                Modifier
                            }
                        ),
                )
            }

            items(enabled.toList(), key = { it }) { id ->
                ReorderableItem(reorderableState, key = id) { isDragging ->
                    MetricGridCell(
                        id = id,
                        amountCents = amountById[id],
                        // 状态来自父组件的 map：跨 items() 块移动时不会被重置，
                        // 所以"旧值 → 新值"的过程真实存在，渐变才有得播
                        shownBadge = badgeStates[id] ?: BadgeKind.Remove,
                        // 拖动中的浮起由库负责，这里只需把它报告的 isDragging 传下去，
                        // 用来屏蔽"拖动过程中被误判为点击"
                        isDragging = isDragging,
                        modifier = Modifier.longPressDraggableHandle(
                            // 长按成功、开始拖动时震一下。
                            // 这是长按拖拽最关键的反馈：手指正按着卡片，
                            // 视觉被手指挡住，"拿起来了"基本靠触觉传达。
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        ),
                        onClick = {
                            enabled.remove(id)
                            // 挪到下方区**最后一个**位置
                            disabled.add(id)
                            persist()
                        },
                        onDragStopped = { persist() },
                    )
                }
            }

            item(key = HIDDEN_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) {
                // 同「显示在首页」标题：首次布局期间不挂 animateItem，避免那段落位平移动画
                SectionHeader(
                    title = "未显示",
                    count = disabled.size,
                    modifier = Modifier
                        .graphicsLayer { alpha = headerAlpha }
                        .then(
                            if (headerVisible.value) {
                                Modifier.animateItem(placementSpec = tween(durationMillis = 120))
                            } else {
                                Modifier
                            }
                        ),
                )
            }

            items(disabled.toList(), key = { it }) { id ->
                // 下方区**不可拖动**（enabled = false），但仍套 ReorderableItem ——
                // 纯粹为了拿到它默认挂载的 Modifier.animateItem()：
                // 点击"搬上去"时这张卡片才有补间动画，而不是硬跳。
                // enabled = false 也让库知道它不参与拖动、不必为别的卡片让位。
                ReorderableItem(reorderableState, key = id, enabled = false) { isDragging ->
                    MetricGridCell(
                        id = id,
                        amountCents = amountById[id],
                        shownBadge = badgeStates[id] ?: BadgeKind.Add,
                        isDragging = isDragging,
                        // 不挂拖动处理器：它只是备选池
                        modifier = Modifier,
                        onClick = {
                            disabled.remove(id)
                            enabled.add(id)
                            persist()
                        },
                        onDragStopped = { },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$title（$count）",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** 徽标语义：`−` 表示点一下会隐藏，`+` 表示点一下会显示。 */
private enum class BadgeKind { Remove, Add }

/**
 * 网格里的一张指标卡：复用首页的 [MetricCard]，右上角叠一个 `+` / `−` 徽标。
 *
 * [modifier] 由调用方传入：上方区传拖动处理器（长按可拖），下方区传空 ——
 * 这样"哪一区可拖动"由调用点一眼可见，而不是藏在参数里。
 *
 * 徽标只是视觉提示，**点击热区是整张卡片** —— 18dp 的徽标太小，只点它很难按中。
 */
@Composable
private fun MetricGridCell(
    id: String,
    amountCents: Long?,
    /** 当前**显示**的徽标。由父组件持有，所以跨 items() 块移动时不会被重置。 */
    shownBadge: BadgeKind,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDragStopped: () -> Unit,
) {
    val metric = LedgerMetric.byId(id) ?: return

    // 阴影的形状必须与卡片一致，否则投影按矩形画 —— 圆角卡片的阴影会变成方块，
    // 看起来像打在"格子"上而不是卡片上。
    val cardShape = MaterialTheme.shapes.medium

    // 拖动结束（isDragging 由 true 回落为 false）时落盘一次。
    // 每次进入这个 Composable 都会以 isDragging=false 触发一次空写，
    // 代价很小且幂等，换来的是"不用自己判断是不是真的拖过"。
    LaunchedEffect(isDragging) {
        if (!isDragging) onDragStopped()
    }

    // 拖动时的反馈：提层级 + 放大。
    //
    // 放大用 Modifier.scale 而不是 graphicsLayer —— graphicsLayer 只做缩放虽然也不该建离屏层，
    // 但 Modifier.scale 语义更窄、更不容易牵动渲染层的边界计算（徽标有约 3/4 画在卡片外，
    // 一旦渲染层边界按组件尺寸算，那部分就会被裁）。
    // 网格项已预留了 6dp 的溢出空间，放大后徽标仍落在项内。
    val dragElevation = if (isDragging) 1f else 0f
    val dragScale = if (isDragging) DRAG_SCALE else 1f
    val dragShadow = if (isDragging) 12.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 为凸出的徽标预留空间。
            //
            // 徽标往右上各溢出 5.5dp（18dp 尺寸 + 5dp 偏移的一半），而 Compose 的 Lazy 项
            // 默认不绘制超出自身边界的部分 —— 那 5.5dp 正好落在项外，于是被裁掉，
            // 看起来就是"徽标只显示了内侧一小块"。
            // 顶部与右侧各留 6dp，徽标就落到项内、同时视觉上依然凸出卡片。
            // 左侧与底部不需要留（徽标不往那边溢），避免无谓地缩小卡片。
            .padding(top = 6.dp, end = 6.dp)
            // shadow 要在 scale 之前：先按卡片形状画投影，再整体缩放，投影跟着一起变大
            .shadow(elevation = dragShadow, shape = cardShape, clip = false)
            .scale(dragScale)
            .zIndex(dragElevation)
            .then(modifier),
    ) {
        // 点击处理放在 MetricCard 内部（Card(onClick=...)）：
        // 那样涟漪会跟随卡片的圆角形状；若在外层 Box 上 clickable，涟漪是个矩形，
        // 会溢出圆角，与卡片形状对不上。
        // onClick 传 null 表示"拖动进行中"——长按拖起后松手不该被当成点一下搬运。
        MetricCard(
            title = metric.displayName,
            amountCents = amountCents,
            onClick = if (isDragging) null else onClick,
        )

        // 徽标：背景色与符号颜色都做补间，符号本身见下面的交叉淡化
        val badgeColor by animateColorAsState(
            targetValue = when (shownBadge) {
                BadgeKind.Remove -> FlowtColors.current.dangerBackground
                BadgeKind.Add -> FlowtColors.current.accentBackground
            },
            animationSpec = tween(durationMillis = 400),
            label = "badge_color",
        )
        val badgeContentColor by animateColorAsState(
            targetValue = when (shownBadge) {
                BadgeKind.Remove -> FlowtColors.current.dangerOnText
                BadgeKind.Add -> FlowtColors.current.accentButtonText
            },
            animationSpec = tween(durationMillis = 400),
            label = "badge_content_color",
        )

        // 两个符号各用一个静态 Text，用透明度做交叉淡化，而不用 AnimatedContent。
        //
        // 原因：AnimatedContent 内部会裁剪到自身边界。徽标有一部分画在它那 18dp 框**之外**
        // （凸出卡片的那四分之三），而它的 SizeTransform 一开始把尺寸收缩得比 18dp 小，
        // 框外的内容就被裁掉，要等尺寸动画跑完才出现 —— 就是"过一会儿才显示"的来源。
        // 静态 Text + 透明度既没有尺寸变化、也不裁剪，同时保留渐变观感。
        val minusAlpha by animateFloatAsState(
            targetValue = if (shownBadge == BadgeKind.Remove) 1f else 0f,
            animationSpec = tween(durationMillis = 200),
            label = "badge_minus_alpha",
        )
        val plusAlpha by animateFloatAsState(
            targetValue = if (shownBadge == BadgeKind.Add) 1f else 0f,
            animationSpec = tween(durationMillis = 200, delayMillis = 150),
            label = "badge_plus_alpha",
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 刻意让徽标凸出卡片：约四分之三在卡片外，是设计上要的角标效果
                .offset(x = 5.dp, y = (-5).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeContentColor,
                modifier = Modifier.graphicsLayer { alpha = minusAlpha },
            )
            Text(
                text = "+",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeContentColor,
                modifier = Modifier.graphicsLayer { alpha = plusAlpha },
            )
        }
    }
}
