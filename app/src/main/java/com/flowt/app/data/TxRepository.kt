package com.flowt.app.data

import androidx.room.withTransaction
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.CategoryDao
import com.flowt.app.data.db.FlowtDatabase
import com.flowt.app.data.db.TransactionDao
import com.flowt.app.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** 一条流水的展示信息：流水本身 + 它所属分类的完整路径（join 出来）。 */
data class TransactionView(
    val entity: TransactionEntity,
    val categoryPath: String,
    val categoryId: Long,
)

class TxRepository(
    private val db: FlowtDatabase,
    private val txDao: TransactionDao = db.transactionDao(),
    private val categoryDao: CategoryDao = db.categoryDao(),
) {

    fun observeAll(): Flow<List<TransactionEntity>> = txDao.observeAll()

    suspend fun insert(transaction: TransactionEntity): Long =
        db.withTransaction { txDao.insert(transaction) }

    suspend fun update(transaction: TransactionEntity) =
        db.withTransaction { txDao.update(transaction) }

    suspend fun delete(transaction: TransactionEntity) =
        db.withTransaction { txDao.delete(transaction) }

    /**
     * 批量导入。逐条做指纹去重（时间 + 金额 + 分类 + 备注），
     * 保证同一份账单重复导入不会把账记两遍。
     * @return 实际新增的条数与跳过的条数
     */
    suspend fun importDeduped(rows: List<TransactionEntity>): ImportResult = db.withTransaction {
        var imported = 0
        var skipped = 0
        rows.forEach { row ->
            val exists = txDao.countMatching(
                timestamp = row.timestamp,
                amountCents = row.amountCents,
                categoryId = row.categoryId,
                note = row.note,
            ) > 0
            if (exists) {
                skipped++
            } else {
                txDao.insert(row)
                imported++
            }
        }
        ImportResult(imported = imported, skipped = skipped)
    }

    /** 按分类路径取分类 id（导入时把账单里的分类名映射到已有分类）。 */
    suspend fun categoryIdByPath(path: String): Long? = categoryDao.getByPath(path)?.id

    /** 统计一批分类（含子树）下有多少笔流水，用于删除分类前的判断。 */
    suspend fun countInCategories(categoryIds: List<Long>): Int =
        if (categoryIds.isEmpty()) 0 else txDao.countByCategories(categoryIds)

    /** 分类 id → 路径，用于列表展示（避免 UI 层自己查一遍）。 */
    suspend fun categoryPathById(): Map<Long, String> =
        categoryDao.getAll().associate { it.id to it.path }
}

data class ImportResult(val imported: Int, val skipped: Int)
