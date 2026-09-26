package com.flowt.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 角色的三种类型。
 *
 * - [BASE]：底色类。用户自由取色的入口，可以随便挑（自由取色盘）。
 * - [ON]：配对色类。默认**自动派生** —— 它的 [RoleKey.pairsWith] 指向的 BASE 被用户
 *   改过时，本角色的值实时重算（同色相、拉开明度、对比度 ≥ [CONTRAST_READABLE]），
 *   保证"浅底浅字"这种不可读配色拼不出来。用户仍可手动覆盖（进入覆盖态），
 *   但设置页会用对比度检查 + 倒计时确认拦一道。
 * - [FREE]：独立语义色。既不派生也没有配对，语义由产品定义。
 */
enum class RoleKind { BASE, ON, FREE }

/**
 * 动态取色 / 预设方案共用的**大面与容器方案化**。
 *
 * 两个职责：
 * 1. 大面积底色中性化 —— 官方生成的容器都是 tone 30/90 的饱和色，"面积最大的
 *    元素最艳"与分层原则相反；记账页 + 流水条目的大面积底 → 雾色，指标卡 → 雾彩；
 * 2. **容器层注入方案色相** —— 卡片底与导航栏底按方案主色的色相生成带色调的值，
 *    跨方案肉眼可辨（此前这两槽是无彩派生灰，六个方案看起来一样）。
 *
 * 文字槽保留官方值：tone 10/90 的深浅落在雾色上对比反而更足。
 * 浅色与深色、预设与自动取色都走这一条路 —— 观感规则只有一份。
 */
internal fun ColorScheme.neutralizeLargeSurfaces(
    dark: Boolean,
): ColorScheme {
    val pageMist = mistFrom(accent = primary, dark = dark)
    val cardMist = mistFrom(accent = tertiary, dark = dark, strong = true)
    // 容器层注入方案色相，三层递进（页面底 → 导航栏底 → 卡片底，逐层加深加彩）：
    // 卡片底比页面底深一档、导航栏底介于两者之间 —— 跨方案肉眼可辨
    val cardTint = tintedContainer(primary, 0.10f, if (dark) 0.13f else 0.94f)
    val navTint = tintedContainer(primary, if (dark) 0.18f else 0.20f, if (dark) 0.25f else 0.90f)
    val navSurface = tintedContainer(primary, 0.08f, if (dark) 0.10f else 0.955f)
    // 深色模式的页面底统一为纯黑（#000000）：OLED 友好，卡片/弹层靠明度差浮起。
    // 页面底覆盖三个主 tab **和各二级页**的背景 —— 二级页直接叠在主界面的
    // Scaffold 上，透出的就是同一个 surface 槽。只动页面级三槽 —— 卡片
    // （surfaceContainerLow）、对话框（surfaceContainerHigh）各归各的槽，不随页面底变化。
    return if (dark) {
        copy(
            primaryContainer = pageMist,
            surfaceContainerHighest = pageMist,
            tertiaryContainer = cardMist,
            surfaceContainerLow = cardTint,
            surfaceContainer = navSurface,
            secondaryContainer = navTint,
            surface = Color(0xFF000000),
            background = Color(0xFF000000),
            surfaceDim = Color(0xFF000000),
        )
    } else {
        copy(
            primaryContainer = pageMist,
            surfaceContainerHighest = pageMist,
            tertiaryContainer = cardMist,
            surfaceContainerLow = cardTint,
            surfaceContainer = navSurface,
            secondaryContainer = navTint,
        )
    }
}

/** 提取 [accent] 的色相，按指定的饱和度/明度生成带方案色调的容器色。 */
private fun tintedContainer(accent: Color, saturation: Float, value: Float): Color =
    hsvToColor(rgbToHsv(accent).first, saturation, value)

/**
 * 可自定义的颜色角色注册表。
 *
 * 设计边界：用户改的是**角色对应的颜色值**，不是"哪个部件用哪个角色"。
 * 部件 → 角色的绑定统一收敛在 [FlowtColors]（颜色位总表）里 —— 改一次
 * [RoleKey.CardSurface]，所有绑定它的部件同时变色，一致性由架构保证。
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
    val kind: RoleKind = RoleKind.BASE,
    /** [kind] 为 [RoleKind.ON] 时的派生基准；派生值随它实时重算。 */
    val pairsWith: RoleKey? = null,
) {
    // --- 界面底色 ---
    PageBackground(
        "页面底色", RoleGroup.Background,
        "所有页面的背景：三个主 tab 和各二级设置页",
    ),
    CardSurface(
        "卡片底色", RoleGroup.Background,
        "流水列表里每一天的卡片背景",
    ),
    CardItemSurface(
        "中性底色", RoleGroup.Background,
        "输入框、日期选择器等控件的内部底色",
    ),
    EntryPageSurface(
        "记账页底色", RoleGroup.Background,
        "记账页整页背景和打开它时的过渡圆，流水列表里每一笔的底色与它相同。" +
            "面积很大，建议选浅淡的颜色",
    ),
    NavigationBarBackground(
        "导航栏底色", RoleGroup.Background,
        "主页底部导航栏和各设置页顶栏的背景",
    ),

    // --- 文字与描边 ---
    TextPrimary(
        "正文文字", RoleGroup.Text,
        "流水分类名、设置页标题等主要文字，随「卡片底色」自动搭配深浅",
        RoleKind.ON, pairsWith = CardSurface,
    ),
    TextSecondary(
        "次要文字", RoleGroup.Text,
        "时间、备注、小计等辅助信息，随「卡片底色」自动搭配深浅",
        RoleKind.ON, pairsWith = CardSurface,
    ),
    Outline(
        "描边与分割线", RoleGroup.Text,
        "输入框、日期框的边框和小色块的描边，随「卡片底色」自动搭配深浅",
        RoleKind.ON, pairsWith = CardSurface,
    ),
    NavigationBarText(
        "导航栏文字", RoleGroup.Text,
        "底部导航和各设置页顶栏的文字，未选中的导航项会自动淡化；" +
            "随「导航栏底色」自动搭配深浅，与「导航栏选中块」一起调整效果更好",
        RoleKind.ON, pairsWith = NavigationBarBackground,
    ),

    // --- 强调色 ---
    Primary(
        "主色", RoleGroup.Accent,
        "记账页的保存按钮、右下角悬浮按钮等最醒目的强调色",
    ),
    OnPrimary(
        "主色上的文字", RoleGroup.Accent,
        "保存按钮等「主色」底上的文字，随「主色」自动搭配深浅",
        RoleKind.ON, pairsWith = Primary,
    ),
    PrimaryContainer(
        "指标卡底色", RoleGroup.Accent,
        "首页指标卡和报表页顶部合计卡的背景",
    ),
    OnPrimaryContainer(
        "指标卡文字", RoleGroup.Accent,
        "指标卡和报表合计卡上的文字，随「指标卡底色」自动搭配深浅",
        RoleKind.ON, pairsWith = PrimaryContainer,
    ),
    EntryPageText(
        "记账页文字", RoleGroup.Text,
        "记账页和流水条目上的文字，随「记账页底色」自动搭配深浅",
        RoleKind.ON, pairsWith = EntryPageSurface,
    ),
    NavigationIndicator(
        "导航栏选中块", RoleGroup.Accent,
        "底部导航当前选中项背后的色块",
    ),

    // --- 语义色 ---
    Danger(
        "危险色", RoleGroup.Semantic,
        "删除、清空等危险操作的按钮和文字",
    ),
    DangerOn(
        "危险色上的文字", RoleGroup.Semantic,
        "「危险色」底上的文字（如拖动指标卡时出现的角标），随「危险色」自动搭配深浅",
        RoleKind.ON, pairsWith = Danger,
    ),
    Expense(
        "支出金额", RoleGroup.Semantic,
        "流水和报表里的支出金额数字，默认暖红色",
        RoleKind.FREE,
    ),
}

enum class RoleGroup(val displayName: String, val description: String?) {
    Background("底色", "决定界面的层次：页面 → 卡片 → 条目，三层从浅到深或从深到浅"),
    Text("文字与描边", "决定可读性：文字色必须与它所在的底色拉开对比"),
    Accent("强调色", "按钮、选中态、指标卡"),
    Semantic("语义色", null),
}

/**
 * 计算所有角色在当前状态下的**生效颜色**。
 *
 * 每个角色的取值优先级：
 * 1. 用户覆盖值（[overrides] 中存在）—— 无论角色类型；
 * 2. [RoleKind.ON] 且配对 BASE 被用户改过 —— 自动派生（见 [deriveOnColor]）；
 * 3. 基底配色（预设主题 / 动态取色）里该角色绑定的槽值。
 *
 * 规则 2 意味着：用户不动颜色时一切照旧（预设主题的手调配色原样生效）；
 * 用户改了某个底色，它的配对文字自动跟着变 —— 这是"用户改底色不出错"的核心机制。
 *
 * 依赖：[RoleKey] 的声明顺序必须保证每个 ON 角色排在其 pairsWith 之后
 * （本文件声明顺序即满足），这样逐个计算时基准色已经就绪。
 */
fun effectiveRoleColors(
    base: ColorScheme,
    overrides: Map<String, Int>,
    dark: Boolean = false,
): Map<RoleKey, Color> {
    val result = HashMap<RoleKey, Color>(RoleKey.entries.size)
    for (role in RoleKey.entries) {
        result[role] = overrides[role.name]?.toComposeColor()
            ?: run {
                val pairBase = role.pairsWith
                if (role.kind == RoleKind.ON && pairBase != null && overrides.containsKey(pairBase.name)) {
                    deriveOnColor(result.getValue(pairBase))
                } else {
                    baseValueOf(role, base, dark)
                }
            }
    }
    return result
}

/** 角色在基底配色里的默认值（未被覆盖、也未触发派生时用）。 */
internal fun baseValueOf(role: RoleKey, base: ColorScheme, dark: Boolean): Color = when (role) {
    RoleKey.PageBackground -> base.surface
    RoleKey.CardSurface -> base.surfaceContainerLow
    RoleKey.CardItemSurface -> base.surfaceContainerHigh
    RoleKey.EntryPageSurface -> base.primaryContainer
    RoleKey.NavigationBarBackground -> base.surfaceContainer

    RoleKey.TextPrimary -> base.onBackground
    RoleKey.TextSecondary -> base.onSurfaceVariant
    RoleKey.Outline -> base.outline
    RoleKey.NavigationBarText -> base.onSurfaceVariant

    RoleKey.Primary -> base.primary
    RoleKey.OnPrimary -> base.onPrimary
    RoleKey.PrimaryContainer -> base.tertiaryContainer
    RoleKey.OnPrimaryContainer -> base.onTertiaryContainer
    RoleKey.EntryPageText -> base.onPrimaryContainer
    RoleKey.NavigationIndicator -> base.secondaryContainer

    RoleKey.Danger -> base.error
    RoleKey.DangerOn -> base.onError
    // 支出色不随主题体系，基底值来自明暗两枚常量（见 [expenseColor]）
    RoleKey.Expense -> expenseColor(dark)
}

/**
 * 把角色生效值写入配色方案的色槽。
 *
 * 注意角色与色槽**不是一对一**：Material 3 有 27 个色槽，这里只写用户真正
 * 能感知到的，每个角色负责写入一组相关色槽（例如「正文文字」同时写
 * onSurface 与 onBackground，因为它们在任何组件里都该是同一个颜色）。
 *
 * 色槽只服务 Material 3 内置组件；app 自己的部件一律读 [FlowtColors] 的颜色位。
 *
 * EntryPageText 与 DangerOn 不写任何槽：前者刻意与「指标卡文字」的
 * onPrimaryContainer 槽脱钩（两个角色写同一槽会互相覆盖），后者所在的
 * error/onError 配对由 M3 内部处理。
 */
fun ColorScheme.withRoleValues(values: Map<RoleKey, Color>): ColorScheme {
    var scheme = this
    RoleKey.entries.forEach { key ->
        val color = values[key] ?: return@forEach
        scheme = when (key) {
            // 页面底色接管 surface 槽（Scaffold 等页面级背景的默认来源），
            // 卡片底色只写 surfaceContainerLow —— 两者从此可独立调整、名实相符
            RoleKey.PageBackground -> scheme.copy(
                background = color,
                surfaceDim = color,
                surface = color,
            )
            RoleKey.CardSurface -> scheme.copy(
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
            // 只写 surfaceContainer：TopAppBar 默认读的 surface 槽已被「卡片底色」占用，
            // 两个角色写同一槽会互相覆盖 —— 顶栏由部件显式读导航栏底色位
            RoleKey.NavigationBarBackground -> scheme.copy(surfaceContainer = color)

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
            RoleKey.EntryPageText -> scheme
            // 导航栏文字不写任何槽：两个导航栏的字色都由部件显式读颜色位，
            // onSurfaceVariant 槽留给「次要文字」，避免两角色互相覆盖
            RoleKey.NavigationBarText -> scheme
            RoleKey.NavigationIndicator -> scheme.copy(secondaryContainer = color)

            RoleKey.Danger -> scheme.copy(error = color)
            RoleKey.DangerOn -> scheme.copy(onError = color)
            // 写入 tertiary 只为让扩展色槽保持一致；支出色的真实消费在 FlowtColors.expenseAmount
            RoleKey.Expense -> scheme.copy(tertiary = color)
        }
    }
    return scheme
}

internal fun Int.toComposeColor() = Color(this)

/**
 * 色板：按**角色用途**生成的候选色，而不是一张静态大色表。
 *
 * 打开取色弹窗时按角色类型生成对应量级的候选，用户永远在正确的明度/饱和度带里选：
 * - 底色类只见雾色（全色相，随明暗成套）与中性色，外加少量反差色供反差设计；
 * - 强调类是全色相、明度归一的强调色（稳/艳两档）；
 * - 文字类是灰轴加同色相的深（浅色模式）/浅（深色模式）文字；
 * - 语义类（危险/支出）是固定的红绿家族 —— 语义色不参与色相轮换。
 *
 * 生成规则与动态取色、深色校准同源（同一套 HSV 工具与雾色参数），
 * 色板里的雾青和实际应用的雾青永远一致。想加色相就往 [hues] 里加一个度数。
 * 量级之外的自由度由自由取色盘承担。
 */
object ColorPalette {

    /** 色板覆盖的色相轴（度数）。 */
    private val hues = listOf(0f, 30f, 55f, 110f, 180f, 220f, 275f, 330f)

    /** 按角色生成候选色（[dark] 为当前生效明暗）。 */
    fun swatchesFor(role: RoleKey, dark: Boolean): List<Int> = when (role) {
        RoleKey.Expense -> listOf(
            0xFFC0392B.toInt(), 0xFFB3261E.toInt(), 0xFFFF8A80.toInt(), 0xFFFF5252.toInt(),
            0xFF2E7D32.toInt(), 0xFF1B5E20.toInt(), 0xFF66BB6A.toInt(), 0xFFA5D397.toInt(),
        )

        RoleKey.Danger, RoleKey.DangerOn -> listOf(
            0xFFB3261E.toInt(), 0xFFC0392B.toInt(), 0xFF790000.toInt(),
            0xFFFF5252.toInt(), 0xFFFF8A80.toInt(),
        )

        RoleKey.PageBackground,
        RoleKey.CardSurface,
        RoleKey.CardItemSurface,
        RoleKey.EntryPageSurface,
        RoleKey.NavigationBarBackground,
        -> buildList {
            hues.forEach { add(hsv(it, 0.04f, if (dark) 0.17f else 0.965f)) } // 雾
            hues.forEach { add(hsv(it, 0.14f, if (dark) 0.24f else 0.93f)) }  // 淡彩
            if (dark) {
                addAll(listOf(0xFF121212.toInt(), 0xFF1C1B1F.toInt(), 0xFF303030.toInt()))
                addAll(listOf(0xFFFFFBFE.toInt(), 0xFFCFD8DC.toInt()))
            } else {
                addAll(listOf(0xFFFFFBFE.toInt(), 0xFFEFEBE9.toInt(), 0xFFCFD8DC.toInt()))
                addAll(listOf(0xFF1C1B1F.toInt(), 0xFF2A3352.toInt()))
            }
        }

        RoleKey.Primary,
        RoleKey.PrimaryContainer,
        RoleKey.NavigationIndicator,
        -> buildList {
            hues.forEach { add(hsv(it, 0.55f, if (dark) 0.80f else 0.45f)) }  // 稳
            hues.forEach { add(hsv(it, 0.85f, if (dark) 0.65f else 0.72f)) }  // 艳
        }

        else -> buildList {
            // 文字类：灰轴 + 同色相的深（浅色模式）/浅（深色模式）文字
            if (dark) {
                addAll(listOf(0xFFFFFFFF.toInt(), 0xFFE6E1E5.toInt(), 0xFFCAC4D0.toInt(), 0xFF49454F.toInt(), 0xFF1C1B1F.toInt()))
                hues.forEach { add(hsv(it, 0.45f, 0.85f)) }
            } else {
                addAll(listOf(0xFF1C1B1F.toInt(), 0xFF49454F.toInt(), 0xFF79747E.toInt(), 0xFFCAC4D0.toInt(), 0xFFFFFFFF.toInt()))
                hues.forEach { add(hsv(it, 0.45f, 0.25f)) }
            }
        }
    }

    private fun hsv(h: Float, s: Float, v: Float): Int = hsvToColor(h, s, v).toArgb()
}
