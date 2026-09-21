package com.flowt.app.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * 可自定义的颜色角色注册表。
 *
 * 设计边界：用户改的是**角色对应的颜色值**，不是"哪个部件用哪个角色"。
 * 部件的角色绑定固定不变 —— 所以改一次 [RoleKey.CardSurface]，
 * 所有用它的卡片会同时变色，一致性由架构保证。
 *
 * 分组按**界面层级**而不是技术名称：用户想改"卡片底色"时会去「底色」组找，
 * 而不是去猜 `surfaceContainerLow` 是什么。
 *
 * 每个角色的 [pairHint] 说明它影响哪些部件 —— 这是用户唯一需要知道的映射关系。
 */
enum class RoleKey(
    val displayName: String,
    val group: RoleGroup,
    val pairHint: String?,
) {
    // --- 界面底色：三层结构，从底到上 ---
    PageBackground(
        "页面底色", RoleGroup.Background,
        "流水页 / 报表页 / 设置页的最底层背景",
    ),
    CardSurface(
        "卡片底色", RoleGroup.Background,
        "按天分组的卡片；与页面底色拉开层次才看得出卡片",
    ),
    CardItemSurface(
        "条目底色", RoleGroup.Background,
        "卡片内单笔流水的底色",
    ),
    EntryPageSurface(
        "记账页底色", RoleGroup.Background,
        "记账 / 编辑页整页底色，也是揭场动画那个圆的颜色",
    ),

    // --- 文字与描边 ---
    TextPrimary(
        "正文文字", RoleGroup.Text,
        "分类名、设置项标题等主要文字；须与底色调开",
    ),
    TextSecondary(
        "次要文字", RoleGroup.Text,
        "时间、备注、分组小计；条目内的文字也用它",
    ),
    Outline(
        "描边与分割线", RoleGroup.Text,
        "输入框边框、日期框、色块描边",
    ),

    // --- 强调色 ---
    Primary(
        "主色", RoleGroup.Accent,
        "主按钮、导航栏选中态",
    ),
    OnPrimary(
        "主色上的文字", RoleGroup.Accent,
        "与「主色」配对",
    ),
    PrimaryContainer(
        "指标卡底色", RoleGroup.Accent,
        "首页顶部的指标卡、悬浮按钮",
    ),
    OnPrimaryContainer(
        "指标卡文字", RoleGroup.Accent,
        "与「指标卡底色」配对",
    ),
    NavigationIndicator(
        "导航栏选中块", RoleGroup.Accent,
        "底部导航当前项背后的色块",
    ),

    // --- 语义色 ---
    Danger("危险色", RoleGroup.Semantic, "删除按钮、危险操作"),
    Expense(
        "支出金额", RoleGroup.Semantic,
        "列表里的支出数字；默认不跟随主题（支出永远是红）",
    ),
}

enum class RoleGroup(val displayName: String, val description: String?) {
    Background("底色", "决定界面的层次：页面 → 卡片 → 条目，三层从浅到深或从深到浅"),
    Text("文字与描边", "决定可读性：文字色必须与它所在的底色拉开对比"),
    Accent("强调色", "按钮、选中态、指标卡"),
    Semantic("语义色", null),
}

/**
 * 把用户/保存方案的颜色覆盖应用到配色方案上。
 *
 * 未覆盖的角色保持基底值 —— 所以"选一套主题 + 微调两个颜色"是自然的工作方式。
 *
 * 注意角色与色槽**不是一对一**：Material 3 有 27 个色槽，我们只开放用户真正
 * 能感知到的 15 个，每个角色负责写入一组相关色槽（例如「正文文字」同时写
 * onSurface 与 onBackground，因为它们在任何组件里都该是同一个颜色）。
 */
fun ColorScheme.withOverrides(overrides: Map<String, Int>): ColorScheme {
    if (overrides.isEmpty()) return this
    var scheme = this
    RoleKey.entries.forEach { key ->
        val argb = overrides[key.name] ?: return@forEach
        val color = argb.toComposeColor()
        scheme = when (key) {
            RoleKey.PageBackground -> scheme.copy(background = color, surfaceDim = color)
            RoleKey.CardSurface -> scheme.copy(
                surface = color,
                surfaceContainerLow = color,
            )
            RoleKey.CardItemSurface -> scheme.copy(
                surfaceVariant = color,
                surfaceContainerHigh = color,
            )
            RoleKey.EntryPageSurface -> scheme.copy(
                primaryContainer = color,
                surfaceContainerHighest = color,
            )

            RoleKey.TextPrimary -> scheme.copy(onBackground = color, onSurface = color)
            // 只写 onSurfaceVariant：条目内文字也用它。刻意不给条目单独一个角色 ——
            // 那样两个角色会写同一个色槽，后写的覆盖先写的，用户改了会失效。
            RoleKey.TextSecondary -> scheme.copy(onSurfaceVariant = color)
            RoleKey.Outline -> scheme.copy(outline = color, outlineVariant = color)

            RoleKey.Primary -> scheme.copy(primary = color)
            RoleKey.OnPrimary -> scheme.copy(onPrimary = color)
            // 指标卡用 tertiaryContainer 而不是 primaryContainer：
            // 后者已经被「记账页底色」占用，两个角色写同一个色槽会互相覆盖。
            RoleKey.PrimaryContainer -> scheme.copy(
                tertiaryContainer = color,
                surfaceTint = color,
            )
            RoleKey.OnPrimaryContainer -> scheme.copy(onTertiaryContainer = color)
            RoleKey.NavigationIndicator -> scheme.copy(secondaryContainer = color)

            RoleKey.Danger -> scheme.copy(error = color)
            // 支出色的语义是"不跟随主题"，这里写入 tertiary 只为让扩展色槽保持一致；
            // 真正生效值由 expenseColor() 提供（见 resolveRoleColor）
            RoleKey.Expense -> scheme.copy(tertiary = color)
        }
    }
    return scheme
}

/**
 * 读取某个角色当前实际生效的颜色（覆盖值优先，否则取基底配色里的值）。
 *
 * [dark] 只影响「支出金额」这个语义色 —— 它不跟随主题，但需要区分深浅模式。
 */
fun resolveRoleColor(
    role: RoleKey,
    base: ColorScheme,
    overrides: Map<String, Int>,
    dark: Boolean = false,
): androidx.compose.ui.graphics.Color {
    overrides[role.name]?.let { return it.toComposeColor() }
    return when (role) {
        RoleKey.PageBackground -> base.background
        RoleKey.CardSurface -> base.surfaceContainerLow
        RoleKey.CardItemSurface -> base.surfaceContainerHigh
        RoleKey.EntryPageSurface -> base.primaryContainer

        RoleKey.TextPrimary -> base.onBackground
        RoleKey.TextSecondary -> base.onSurfaceVariant
        RoleKey.Outline -> base.outline

        RoleKey.Primary -> base.primary
        RoleKey.OnPrimary -> base.onPrimary
        RoleKey.PrimaryContainer -> base.tertiaryContainer
        RoleKey.OnPrimaryContainer -> base.onTertiaryContainer
        RoleKey.NavigationIndicator -> base.secondaryContainer

        RoleKey.Danger -> base.error
        RoleKey.Expense -> expenseColor(dark)
    }
}

internal fun Int.toComposeColor() = androidx.compose.ui.graphics.Color(this)

/**
 * 色板：用户从这里挑颜色。
 *
 * 为什么用色板而不是 HSV 自由取色：
 * 1. 自由取色极易挑出与整套配色冲突的脏色，色板是"已经调好的候选"；
 * 2. 手机上点一下比拖三个滑块快得多，也不用引入第三方取色库；
 * 3. 想扩展就往 [ColorPalette.swatches] 加颜色，UI 自动多一格。
 */
object ColorPalette {
    val swatches: List<Int> = listOf(
        0xFF6750A4.toInt(), // 紫罗兰
        0xFF4355B9.toInt(), // 靛蓝
        0xFF00696E.toInt(), // 青碧
        0xFF3F6837.toInt(), // 森绿
        0xFF9A4520.toInt(), // 暖橙
        0xFF984061.toInt(), // 玫红
        0xFF00E5FF.toInt(), // 荧光青
        0xFFE040FB.toInt(), // 品红
        0xFFB3261E.toInt(), // 警示红
        0xFFC0392B.toInt(), // 支出红
        0xFF2E7D32.toInt(), // 收入绿
        0xFFEADDFF.toInt(), // 淡紫
        0xFFDEE0FF.toInt(), // 淡靛
        0xFFB2EBF2.toInt(), // 淡青
        0xFFC0EFB0.toInt(), // 淡绿
        0xFFFFDBCD.toInt(), // 淡橙
        0xFFFFD9E2.toInt(), // 淡粉
        0xFFEFEBE9.toInt(), // 米白
        0xFFFFFBFE.toInt(), // 近白
        0xFFCFD8DC.toInt(), // 浅灰
        0xFF2A3352.toInt(), // 深蓝紫
        0xFF1C1B1F.toInt(), // 近黑
        0xFF0D1226.toInt(), // 深蓝黑
        0xFF49454F.toInt(), // 深灰
        0xFF21005D.toInt(), // 深紫
        0xFF00105C.toInt(), // 深蓝
        0xFF002022.toInt(), // 深青
    )
}
