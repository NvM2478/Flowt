package com.flowt.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * 全局导航动效规格 —— **唯一的调节点**。
 *
 * 谁在用：主 tab 切换（取景框平移）、设置页二级菜单的进入/退出。
 * 两处必须共用同一份规格，否则会明显感到"一个快一个慢"——
 * 这不是错觉：spring 是物理弹簧（时长由刚度和阻尼决定，没有固定时长），
 * tween 是固定时长插值，两者本质不同，靠凑参数是凑不齐的。
 *
 * 为什么用 spring 而不是 tween：
 * tween 逐帧线性推进偏移，整屏内容在动画期间每帧都要重新布局+绘制，时长越长越容易掉帧；
 * spring 走原生滚动/渲染路径，开销小得多。
 *
 * 调参注意：
 * - [STIFFNESS] 别低于约 600。刚度越低动画尾部越长，而尾部每帧位移不到 1 像素，
 *   观感就是"拖着走、像掉帧"。Pager 内置默认约 1500，900 ≈ 默认的 60%。
 * - [DAMPING] 取 NoBouncy：落位不回弹 —— 取景框平移和纸张覆盖都不该"抖一下"。
 * - 想整体更快/更慢就只改 [STIFFNESS]：调大更快、调小更慢，**两个动画会同时变**。
 */
object Transitions {

    const val STIFFNESS = 700f
    const val DAMPING = Spring.DampingRatioNoBouncy

    /** 平移类动画（位移用像素）。供 Pager 的 animateScrollToPage 使用。 */
    fun <T> slide(): FiniteAnimationSpec<T> = spring(
        stiffness = STIFFNESS,
        dampingRatio = DAMPING,
    )

    /** 横向滑入：从屏幕右侧外进入。 */
    fun slideInFromRight(screenWidthPx: Int): EnterTransition =
        slideInHorizontally(
            animationSpec = spring(
                stiffness = STIFFNESS,
                dampingRatio = DAMPING,
            ),
            initialOffsetX = { screenWidthPx },
        )

    /** 横向滑出：向右退出到屏幕外。 */
    fun slideOutToRight(screenWidthPx: Int): ExitTransition =
        slideOutHorizontally(
            animationSpec = spring(
                stiffness = STIFFNESS,
                dampingRatio = DAMPING,
            ),
            targetOffsetX = { screenWidthPx },
        )
}
