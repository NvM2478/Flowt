package com.flowt.app.ui.entry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntSize
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.theme.FlowtColors
import kotlin.math.hypot

/**
 * 圆的扩散 / 收回时长 —— **两个方向必须用同一个值**。
 *
 * 之前是 600 / 450（进入稍慢、退出稍快，Material 的常规做法），但那会让"同一个圆的
 * 长大与缩小"看起来像两种不同的运动。既然这个动画的语义是"同一个圆的尺寸变化"，
 * 两个方向就该严格对称：同时长 + 同一条缓动曲线（FastOutSlowInEasing）。
 */
private const val CIRCLE_ANIM_MS = 400

/**
 * 记账 / 编辑页的揭场动画：以某个点为圆心的圆扩大铺满全屏，然后露出页面内容。
 *
 * 圆心是参数化传入的 [revealCenter] —— 点 FAB 时从 FAB 长出来，点某张流水卡片时
 * 就从那张卡片长出来。"哪个元素被点，动画就从哪里开始"是统一的规则。
 *
 * 三个必须记住的实现要点（都是踩过的坑）：
 *
 * 1. **底色要画在 clipPath 里面**。写成 `Modifier.background(...)` + `drawWithContent{clipPath{...}}`
 *    会让背景先于裁剪绘制，结果是"点击瞬间整屏变色，只有 UI 跟着圆扩散"，正好相反。
 * 2. **最大半径取"圆心到最远角的距离"**，不是对角线长度 —— 对角线是以屏幕中心为圆心的算法，
 *    而圆心可能在 FAB（右下角）或在列表中部的某张卡片上，用对角线会让圆铺不满。
 * 3. **关闭时先 hide() 收键盘、等圆缩完再清焦点**。只 hide() 不 clearFocus()，输入框会一直
 *    握着焦点，下次打开键盘瞬间弹回、绕过动画；只 clearFocus() 不 hide()，收缩期间键盘还挂着。
 */
@Composable
fun EntryRevealHost(
    vm: AppViewModel,
    categories: List<Category>,
    visible: Boolean,
    /** 揭示圆的圆心（屏幕坐标）。null 表示暂时拿不到，退化为左上角。 */
    revealCenter: Offset?,
    onDismiss: () -> Unit,
    /** 非空表示"编辑已有流水"，为空表示"新建"。 */
    editTarget: TransactionEntity? = null,
    onDeleteRequest: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val center = revealCenter ?: Offset.Zero

    // 当前展示内容的冻结副本。
    //
    // 为什么需要它：关闭时 FlowtApp 会立刻把 editTarget 置空，但收缩动画期间
    // EntryScreen 仍然挂载着（这正是"收缩时圆里还有内容"的实现方式）。
    // 如果内容直接读 editTarget，那一瞬间它会以为自己变成了"新建"模式 ——
    // 结果是输入框全部重置、还弹出键盘。冻结副本只在打开时更新，关闭期间保持不变。
    var overlayEditTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var overlayIsEditing by remember { mutableStateOf(false) }

    // 圆铺满后才让金额输入框获取焦点 —— 否则键盘会在点击瞬间弹出，抢在动画前面
    var revealDone by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible, editTarget) {
        if (visible) {
            // 只在"打开"这一刻冻结内容，关闭过程中不再改动
            overlayEditTarget = editTarget
            overlayIsEditing = editTarget != null
            revealDone = false
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(CIRCLE_ANIM_MS, easing = FastOutSlowInEasing),
            )
            revealDone = true
        } else {
            // 关闭分三件事，顺序是关键：
            // 1) 立刻收键盘 —— 用户已经点了取消，键盘不该赖在屏幕上等动画播完。
            //    hide() 只收键盘，不动焦点，所以输入框还活着、圆里仍有内容。
            // 2) 播圆的收缩动画 —— 这期间内容还挂着，圆不是空白的。
            // 3) 圆缩没后再清焦点 —— shouldRenderContent 随之变 false，EntryScreen 被卸载，
            //    保证下次打开时输入框是全新的，键盘不会在点击瞬间就弹回来。
            keyboardController?.hide()
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(CIRCLE_ANIM_MS, easing = FastOutSlowInEasing),
            )
            revealDone = false
            focusManager.clearFocus()
        }
    }

    // 内容挂载条件：只要圆还看得见就保留内容 —— 所以收缩过程中圆里不是空白。
    // 真正卸载发生在 progress 归 0 之后（上一行 effect 的末尾才清焦点）。
    val shouldRenderContent = visible || progress.value > 0f

    if (!shouldRenderContent) return

    val maxRadius = if (screenSize == IntSize.Zero) {
        0f
    } else {
        val w = screenSize.width.toFloat()
        val h = screenSize.height.toFloat()
        listOf(
            hypot(center.x, center.y),
            hypot(w - center.x, center.y),
            hypot(center.x, h - center.y),
            hypot(w - center.x, h - center.y),
        ).max()
    }
    val radius = maxRadius * progress.value

    // 揭场圆与记账页底色同源；FAB 的加号也是同一个颜色位 ——
    // 圆从 FAB 中心长出来时加号无缝溶进圆里，起跳零跳变
    val revealColor = FlowtColors.current.entryPageBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
            .drawWithContent {
                if (radius <= 0f) return@drawWithContent
                val path = Path().apply {
                    addOval(Rect(center = center, radius = radius))
                }
                clipPath(path) {
                    // 圆内先铺底色（与 FAB 同色），再画记账界面 —— 圆因此是一个有颜色的实体
                    drawRect(color = revealColor)
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        if (shouldRenderContent) {
            EntryScreen(
                vm = vm,
                categories = categories,
                onDone = onDismiss,
                autoFocusAmount = revealDone,
                editTarget = overlayEditTarget,
                // 传冻结的编辑态：关闭动画期间即使 FlowtApp 已把 editTarget 置空，
                // 这一页也不该突然切换成"新建"模式（会重置输入框并弹键盘）。
                isEditing = overlayIsEditing,
                onDeleteRequest = onDeleteRequest,
            )
        }
    }
}
