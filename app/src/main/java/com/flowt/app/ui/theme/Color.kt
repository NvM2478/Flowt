package com.flowt.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 语义色常量。
 *
 * 支出金额现在是普通角色（[RoleKey.Expense]，用户可在设置页自定义），这对常量
 * 只作为它的**基底值**：用户没改过时支出永远是暖红，不管选了哪套配色、
 * 也不管是浅色还是深色模式。
 *
 * 扩展方式：将来加收入时在这里加一对常量，并在 [RoleKey] 加一个 FREE 角色。
 */
private val ExpenseLight = Color(0xFFC0392B)
private val ExpenseDark = Color(0xFFFF8A80)

/** 支出金额的基底颜色（未覆盖时的取值）。 */
fun expenseColor(dark: Boolean): Color = if (dark) ExpenseDark else ExpenseLight

/** 次要文字颜色，用于"没有数据"的占位符等。走颜色位总表，与全局角色联动。 */
@Composable
fun subtleTextColor(): Color = FlowtColors.current.subtleText
