package com.flowt.app.data

import androidx.room.withTransaction
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.CategoryDao
import com.flowt.app.data.db.FlowtDatabase
import com.flowt.app.data.db.TransactionDao
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.data.db.TxFingerprint
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

    /** 全量读一次，按时间升序（导出用）。 */
    suspend fun getAll(): List<TransactionEntity> = txDao.getAll()

    suspend fun insert(transaction: TransactionEntity): Long =
        db.withTransaction { txDao.insert(transaction) }

    /** 批量插入（导入用）。与外层事务可嵌套复用，所以导入能做到"全成或全不成"。 */
    suspend fun insertAll(transactions: List<TransactionEntity>) = db.withTransaction {
        transactions.forEach { txDao.insert(it) }
    }

    /** 按导入批次删除，撤销本次导入时用。 */
    suspend fun deleteBySource(source: String): Int =
        db.withTransaction { txDao.deleteBySource(source) }

    /**
     * 清空全部流水；[includeCategories] 为 true 时连分类树一起清掉。
     *
     * 这是整个应用里唯一会绕过"删除分类前必须迁移流水"这条规则的操作 ——
     * 它走的是相反的次序：先删流水，再删分类。顺序不能反，分类的外键是 NO_ACTION，
     * 流水还在时删分类会被数据库直接拒绝。
     *
     * 调用方必须做二次确认，这里不设任何保护。
     */
    suspend fun clearAll(includeCategories: Boolean): ClearResult = db.withTransaction {
        val deletedTransactions = txDao.deleteAll()
        val deletedCategories = if (includeCategories) categoryDao.deleteAll() else 0
        ClearResult(transactions = deletedTransactions, categories = deletedCategories)
    }

    suspend fun update(transaction: TransactionEntity) =
        db.withTransaction { txDao.update(transaction) }

    suspend fun delete(transaction: TransactionEntity) =
        db.withTransaction { txDao.delete(transaction) }

    /**
     * 批量导入。与**导入前**已有的流水比对指纹（时间 + 金额 + 分类 + 备注）去重。
     *
     * 关键在"导入前"：比对基准是进入本函数时的快照，所以同一文件里两行完全相同的
     * 流水都会被保留 —— 那更可能是用户真的消费了两次，丢掉是数据损失；而重复导入
     * 同一份文件时，第二批会全部命中已有指纹，不会把账记两遍。
     * @return 实际新增的条数与跳过的条数
     */
    suspend fun importDeduped(rows: List<TransactionEntity>): ImportResult = db.withTransaction {
        val existing = txDao.allFingerprints().toHashSet()
        var imported = 0
        var skipped = 0
        rows.forEach { row ->
            if (row.fingerprint() in existing) {
                skipped++
            } else {
                txDao.insert(row)
                imported++
            }
        }
        ImportResult(imported = imported, skipped = skipped)
    }

    /** 导入前的指纹快照：让预览阶段就能报出「N 笔与已有记录重复」，不必等插入时才发现。 */
    suspend fun fingerprintSnapshot(): Set<TxFingerprint> = txDao.allFingerprints().toHashSet()

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

/** 清空数据的结果，用于给用户一句准确的回执。 */
data class ClearResult(val transactions: Int, val categories: Int)

/**
 * 待导入的流水 → 判重指纹。口径必须与 [TxFingerprint] 的字段一一对应，
 * 否则「预览说会跳过 N 笔」和「实际跳过 N 笔」会对不上。
 */
fun TransactionEntity.fingerprint(): TxFingerprint = TxFingerprint(
    timestamp = timestamp,
    amountCents = amountCents,
    categoryId = categoryId,
    note = note,
)
