package com.flowt.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 颜色位总表 —— 全 app 唯一一处"哪个部件用哪个角色"的登记处。
 *
 * 每个字段是一个**颜色位**（界面上一类可感知的颜色用途），等号右边决定它跟随
 * 哪个角色。规则：
 *
 * - **调整角色成员**（把某个部件划进另一批颜色）= 改这里某一行右边的角色；
 * - **部件代码**只写 `FlowtTheme.colors.字段名`，永远不直接引用
 *   `MaterialTheme.colorScheme.xxx`（有单元测试强制：ThemeBoundariesTest）；
 * - **用户改角色颜色**（设置页）会同时落到所有绑定该角色的位。
 *
 * 一条位只读一个角色的值。需要"底色的 70% 透明"这类衍生时，在部件里
 * `colors.xxx.copy(alpha = ...)`，衍生逻辑留在部件内（它是部件自己的表现细节）。
 */
@Immutable
class FlowtColors(
    private val values: Map<RoleKey, Color>,
    private val defaults: Map<RoleKey, Color>,
) {

    // --- 流水页 ---
    /** 按天分组的卡片底。 */
    val ledgerCardBackground: Color get() = value(RoleKey.CardSurface)
    /** 单笔流水条目底。刻意与记账页同色：点条目进编辑页时揭场圆的起点与终点颜色一致。 */
    val ledgerItemBackground: Color get() = value(RoleKey.EntryPageSurface)
    /** 条目上的文字与涟漪（跟随「记账页文字」，自动匹配条目底色的深浅）。 */
    val ledgerItemText: Color get() = value(RoleKey.EntryPageText)
    /** 支出金额数字。 */
    val expenseAmount: Color get() = value(RoleKey.Expense)

    // --- 指标卡 / 报表头 ---
    /** 首页指标卡与报表月度合计的容器底。 */
    val metricCardBackground: Color get() = value(RoleKey.PrimaryContainer)
    /** 上述容器上的文字（跟随「指标卡底色」自动匹配）。 */
    val metricCardText: Color get() = value(RoleKey.OnPrimaryContainer)

    // --- 记账页 ---
    /** 记账 / 编辑页整页底色，也是揭场动画那个圆的颜色。 */
    val entryPageBackground: Color get() = value(RoleKey.EntryPageSurface)
    /** 主页底部导航栏与二级页顶部导航栏的底色。 */
    val navigationBarBackground: Color get() = value(RoleKey.NavigationBarBackground)
    /** 导航栏上的文字与图标（角色「导航栏文字」；未选中态由部件自动淡化）。 */
    val navigationBarText: Color get() = value(RoleKey.NavigationBarText)
    /** 底部导航栏当前项背后的选中块（角色「导航栏选中块」）。 */
    val navigationIndicator: Color get() = value(RoleKey.NavigationIndicator)
    /** 记账页上的文字、输入框内容与光标。 */
    val entryPageText: Color get() = value(RoleKey.EntryPageText)

    // --- 强调 ---
    /** 主按钮、常用分类按钮、拖拽徽章的底。 */
    val accentBackground: Color get() = value(RoleKey.Primary)
    /** 上述强调底上的文字。 */
    val accentButtonText: Color get() = value(RoleKey.OnPrimary)
    /** 页面底色上的强调文字（如"已修改"角标、绑定高亮）。 */
    val accentInlineText: Color get() = value(RoleKey.Primary)
    /** 正文文字（设置项禁用态等衍生色以它为基）。 */
    val textPrimary: Color get() = value(RoleKey.TextPrimary)

    // --- 危险 ---
    /** 危险操作的文字色（删除确认、错误提示）。 */
    val dangerAccent: Color get() = value(RoleKey.Danger)
    /** 危险底色（拖拽徽章"移除"态）。 */
    val dangerBackground: Color get() = value(RoleKey.Danger)
    /** 危险底上的文字（自动匹配）。 */
    val dangerOnText: Color get() = value(RoleKey.DangerOn)

    // --- 描边与中性 ---
    /** 色块描边、输入框边框。 */
    val outlineBorder: Color get() = value(RoleKey.Outline)
    /** 中性容器底（跟随系统取色的占位圆点等）。 */
    val neutralSurface: Color get() = value(RoleKey.CardItemSurface)
    /** 次要文字（时间、备注、说明文案）。 */
    val subtleText: Color get() = value(RoleKey.TextSecondary)

    /** 直接读某角色的生效值：设置页的角色预览、色块等"元层面"用。 */
    fun role(role: RoleKey): Color = values.getValue(role)

    /**
     * 某个角色清掉自身覆盖后的默认色（取色弹窗"重置"的回退目标与置灰判断依据）。
     * ON 角色回到"配对底色的派生值"（配对底色自己的覆盖仍生效）。
     */
    fun defaultRole(role: RoleKey): Color = defaults.getValue(role)

    private fun value(r: RoleKey): Color = values.getValue(r)

    companion object {
        /** 当前生效的颜色位表。部件里一律 `FlowtColors.current.xxx` 取色。 */
        val current: FlowtColors
            @Composable
            @ReadOnlyComposable
            get() = LocalFlowtColors.current
    }
}

/** 在 FlowtTheme 顶层提供；任何 Composable 都能 `FlowtTheme.colors.xxx` 取到。 */
val LocalFlowtColors = staticCompositionLocalOf<FlowtColors> {
    error("FlowtColors 未提供：FlowtTheme 必须包在 Compose 树最顶层")
}
