package com.flowt.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 分类使用频次统计结果，用于"常用分类置顶"。 */
data class CategoryUsage(
    val categoryId: Long,
    val usageCount: Int,
    val lastUsedAt: Long,
)

/**
 * 判重用的指纹，字段与 [TransactionDao.countMatching] 的 WHERE 条件一一对应。
 *
 * 一次把全表指纹读进内存、而不是逐条查库，是为了让导入能在预览阶段
 * 就报出「其中 N 笔与已有记录重复」——逐条查只能等插入时才知道。
 */
data class TxFingerprint(
    val timestamp: Long,
    val amountCents: Long,
    val categoryId: Long,
    val note: String?,
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 全量读一次（导出用）。导出是一次性动作，用不着 Flow。 */
    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAll(): List<TransactionEntity>

    @Query(
        """
        SELECT categoryId, COUNT(*) AS usageCount, MAX(timestamp) AS lastUsedAt
        FROM transactions
        WHERE timestamp >= :since AND type = :type
        GROUP BY categoryId
        ORDER BY usageCount DESC, lastUsedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getFrequentCategories(
        since: Long,
        type: String = TransactionEntity.TYPE_EXPENSE,
        limit: Int = 8,
    ): List<CategoryUsage>

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IN (:categoryIds)")
    suspend fun countByCategories(categoryIds: List<Long>): Int

    /** 导出/导入去重用：时间 + 金额 + 分类 + 备注 组成指纹。 */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE timestamp = :timestamp AND amountCents = :amountCents
          AND categoryId = :categoryId AND note IS :note
        """
    )
    suspend fun countMatching(timestamp: Long, amountCents: Long, categoryId: Long, note: String?): Int

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :toCategoryId WHERE categoryId IN (:fromCategoryIds)")
    suspend fun reassignCategories(fromCategoryIds: List<Long>, toCategoryId: Long): Int

    @Query("DELETE FROM transactions WHERE categoryId IN (:categoryIds)")
    suspend fun deleteByCategories(categoryIds: List<Long>): Int

    /**
     * 全部已有流水的指纹，供导入前一次性比对。
     *
     * 自用场景下流水量级是几千条，读进内存不到 1MB；换来的是导入能一次算出
     * 重复数，而不是插一条查一条。
     */
    @Query("SELECT timestamp, amountCents, categoryId, note FROM transactions")
    suspend fun allFingerprints(): List<TxFingerprint>

    /** 按导入批次删除（撤销本次导入）。 */
    @Query("DELETE FROM transactions WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    /** 清空全部流水。调用方必须做二次确认 —— 这里不设任何保护。 */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll(): Int
}
