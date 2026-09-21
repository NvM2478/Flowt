package com.flowt.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY path")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories ORDER BY path")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): Category?

    /** 直接子分类（parentId 为 null 时取根分类）。 */
    @Query("SELECT * FROM categories WHERE parentId IS :parentId ORDER BY sortOrder, name")
    suspend fun getChildren(parentId: Long?): List<Category>

    /**
     * 取某路径下的整棵子树（不含自身）。
     * 只负责生成参数，SQL 层的转义在仓库层完成。
     */
    @Query("SELECT * FROM categories WHERE path LIKE :prefix ESCAPE '\\' ORDER BY path")
    suspend fun getSubtree(prefix: String): List<Category>

    @Query("SELECT COUNT(*) FROM categories WHERE parentId IS :parentId AND name = :name AND id != :excludeId")
    suspend fun countSiblingsNamed(parentId: Long?, name: String, excludeId: Long): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM categories WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int
}
