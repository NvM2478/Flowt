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

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

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
}
