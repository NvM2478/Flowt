package com.flowt.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义色常量。
 *
 * 为什么需要它们：Material 3 是**角色制**配色，没有"支出金额该用什么颜色"这种概念。
 * 而记账应用需要一个跨越所有配色的稳定语义——**支出永远是暖红**，
 * 不管用户选了哪套配色、也不管是浅色还是深色模式。
 *
 * 扩展方式：以后要加"预算超支警告色"之类的语义色，在这里加一对常量即可。
 * （V1 只记支出，所以暂时只有 expense 一组；将来加收入时在 [LedgerMetric] 侧
 * 一并补上 income 的常量即可。）
 */
private val ExpenseLight = Color(0xFFC0392B)
private val ExpenseDark = Color(0xFFFF8A80)

/**
 * 支出金额颜色。
 *
 * 刻意做成**纯函数**（显式传入 dark）而不是 @Composable：
 * 「支出永远是红」这条语义色需要能在非 Composable 的地方取到
 * （例如 resolveRoleColor 计算设置页的颜色预览），@Composable 会把约束一路传染上去。
 */
fun expenseColor(dark: Boolean): Color = if (dark) ExpenseDark else ExpenseLight

/** 便捷重载：在 Composable 里用，自动跟随系统深色模式。 */
@Composable
fun expenseColor(): Color = expenseColor(isSystemInDarkTheme())

/** 次要文字颜色，用于"没有数据"的占位符等。 */
@Composable
fun subtleTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
