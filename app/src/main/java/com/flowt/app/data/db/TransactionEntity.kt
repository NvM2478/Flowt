package com.flowt.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条流水。
 *
 * 设计要点（对应已定的设计决策）：
 * - 金额一律用**整数分**（Long）存储，绝不用 Double/Float —— 浮点累加会产生分位偏差。
 * - 时间用 epoch 毫秒（Long），不用字符串 —— 便于按月/按区间统计。
 * - 只存 categoryId，**不存分类路径字符串** —— 改分类名时历史流水一条都不用动。
 * - type 目前恒为 "expense"（只记支出）。留这个字段是为了将来加收入时
 *   **不需要改动已有数据**，只需补界面。
 * - 不设 deleted 软删除字段：自用场景下直接删，避免"查询到处都要带 deleted = 0"。
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.NO_ACTION, // 删除前必须先迁移流水，由仓库层强制
        ),
    ],
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["categoryId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 金额，单位：分。永远是正数，方向由 [type] 决定。 */
    val amountCents: Long,

    /** 发生时间，epoch 毫秒。 */
    val timestamp: Long,

    /** 所属分类；一笔流水必须绑定一个分类。 */
    val categoryId: Long,

    /** 备注，可为空。 */
    val note: String? = null,

    /** V1 恒为 "expense"。 */
    @ColumnInfo(defaultValue = "expense")
    val type: String = TYPE_EXPENSE,

    /** 导入来源标记，手动记账为 null；用于 CSV 导入去重与溯源。 */
    val source: String? = null,
) {
    companion object {
        const val TYPE_EXPENSE = "expense"
    }
}
