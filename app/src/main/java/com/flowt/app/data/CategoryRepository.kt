package com.flowt.app.data

import androidx.room.withTransaction
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.CategoryDao
import com.flowt.app.data.db.CategoryUsage
import com.flowt.app.data.db.FlowtDatabase
import com.flowt.app.data.db.TransactionDao
import com.flowt.app.data.db.TransactionEntity

/** 分类名里禁止出现的字符：它们是路径分隔符，出现会导致路径二义。 */
private val FORBIDDEN_IN_NAME = charArrayOf('/')

private const val PATH_SEPARATOR = "/"

/** 前缀查询的 LIKE 转义：分类名里若出现 % 或 _ 会被当成通配符。 */
private fun escapeLike(value: String): String =
    value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/** "常用分类"统计窗口：最近 30 天（滚动窗口，不是自然月）。 */
private const val FREQUENT_WINDOW_DAYS = 30L
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

class CategoryRepository(
    private val db: FlowtDatabase,
    private val categoryDao: CategoryDao = db.categoryDao(),
    private val transactionDao: TransactionDao = db.transactionDao(),
) {

    suspend fun getAll(): List<Category> = categoryDao.getAll()

    suspend fun getChildren(parentId: Long?): List<Category> = categoryDao.getChildren(parentId)

    /**
     * 新建分类。path 由程序生成，调用方只提供父分类和名字。
     * @return 新分类的 id
     */
    suspend fun create(parentId: Long?, name: String, sortOrder: Int = 0): Long =
        db.withTransaction {
            val cleanName = validateName(name)
            val parent = parentId?.let { id ->
                categoryDao.getById(id) ?: throw CategoryException("父分类不存在（id=$id）")
            }
            requireUniqueName(parentId, cleanName)
            val parentPath = parent?.path
            val path = if (parentPath.isNullOrEmpty()) cleanName else "$parentPath$PATH_SEPARATOR$cleanName"
            categoryDao.insert(Category(parentId = parentId, name = cleanName, path = path, sortOrder = sortOrder))
        }

    /**
     * 改名或移动分类。整棵子树的 path 在**同一个事务内**重算，
     * 要么全部成功、要么全部回滚 —— 避免出现"父改了子没改"的裂开状态。
     */
    suspend fun rename(categoryId: Long, newName: String) = db.withTransaction {
        val target = categoryDao.getById(categoryId)
            ?: throw CategoryException("分类不存在（id=$categoryId）")
        val cleanName = validateName(newName)
        if (cleanName == target.name) return@withTransaction

        requireUniqueName(target.parentId, cleanName, excludeId = categoryId)

        val parent = target.parentId?.let { categoryDao.getById(it) }
        val newPath = if (parent == null) cleanName else "${parent.path}$PATH_SEPARATOR$cleanName"

        // 1. 先改自身
        categoryDao.update(target.copy(name = cleanName, path = newPath))
        // 2. 再改整棵子树（应用层重算，不依赖 SQL 字符串替换，避免误伤）
        repathSubtree(oldPath = target.path, newPath = newPath, excludeId = categoryId)
    }

    /** 把分类移动到另一个父分类下（parentId 传 null 表示移到根）。 */
    suspend fun move(categoryId: Long, newParentId: Long?) = db.withTransaction {
        val target = categoryDao.getById(categoryId)
            ?: throw CategoryException("分类不存在（id=$categoryId）")
        if (categoryId == newParentId) throw CategoryException("不能把分类移动到自己下面")

        // 防止把分类移动到自己的子树里，否则会形成环、整棵树永久脱离
        if (newParentId != null) {
            val newParent = categoryDao.getById(newParentId)
                ?: throw CategoryException("目标父分类不存在（id=$newParentId）")
            if (newParent.path == target.path || newParent.path.startsWith("${target.path}$PATH_SEPARATOR")) {
                throw CategoryException("不能把分类移动到它自己的子分类下")
            }
        }

        requireUniqueName(newParentId, target.name, excludeId = categoryId)

        val newParentPath = newParentId?.let { categoryDao.getById(it)?.path }
        val newPath = if (newParentPath.isNullOrEmpty()) target.name else "$newParentPath$PATH_SEPARATOR${target.name}"

        categoryDao.update(target.copy(parentId = newParentId, path = newPath))
        repathSubtree(oldPath = target.path, newPath = newPath, excludeId = categoryId)
    }

    /**
     * 删除分类（连同整棵子树）。
     *
     * 规则（对应已定的设计决策）：
     * - 只要该分类**或它的任何子孙**还被流水引用，就拒绝删除；调用方需先把流水
     *   迁移到其他分类（[moveTransactions]）后再删。
     * - 不与"一笔流水必须绑定一个分类"这条约束冲突。
     */
    suspend fun delete(categoryId: Long) = db.withTransaction {
        val target = categoryDao.getById(categoryId)
            ?: throw CategoryException("分类不存在（id=$categoryId）")
        val subtree = categoryDao.getSubtree(escapeLike(target.path) + PATH_SEPARATOR + "%")
        val allIds = listOf(target.id) + subtree.map { it.id }

        val referenced = transactionDao.countByCategories(allIds)
        if (referenced > 0) {
            throw CategoryException("「${target.path}」及其子分类下还有 $referenced 笔流水，请先迁移或删除这些流水")
        }
        categoryDao.deleteByIds(allIds)
    }

    /** 把一批分类下的流水迁移到目标分类（删除分类前的指定动作）。 */
    suspend fun moveTransactions(fromCategoryIds: List<Long>, toCategoryId: Long): Int =
        db.withTransaction {
            val to = categoryDao.getById(toCategoryId)
                ?: throw CategoryException("目标分类不存在（id=$toCategoryId）")
            if (to.id in fromCategoryIds) {
                throw CategoryException("目标分类不能是待迁移分类本身")
            }
            transactionDao.reassignCategories(fromCategoryIds, toCategoryId)
        }

    /**
     * 删除一批分类（及其整棵子树）下的全部流水。
     * 与 [moveTransactions] 互斥：这是"删分类时选择丢弃流水"的那条出路。
     * @return 被删除的流水条数
     */
    suspend fun deleteTransactionsOf(categoryIds: List<Long>): Int = db.withTransaction {
        if (categoryIds.isEmpty()) return@withTransaction 0
        val allIds = categoryIds.toMutableList()
        categoryIds.forEach { id ->
            val node = categoryDao.getById(id) ?: return@forEach
            val subtree = categoryDao.getSubtree(escapeLike(node.path) + PATH_SEPARATOR + "%")
            allIds += subtree.map { it.id }
        }
        transactionDao.deleteByCategories(allIds.distinct())
    }

    /**
     * 常用分类：最近 30 天内使用次数最多的分类。
     * 并列时用最近一次使用时间做次级排序，避免顺序随机跳动。
     */
    suspend fun getFrequentCategories(limit: Int = 8): List<CategoryUsage> =
        transactionDao.getFrequentCategories(
            since = System.currentTimeMillis() - FREQUENT_WINDOW_DAYS * MILLIS_PER_DAY,
            type = TransactionEntity.TYPE_EXPENSE,
            limit = limit,
        )

    // --- 内部工具 ---

    private fun validateName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw CategoryException("分类名不能为空")
        if (name.any { it in FORBIDDEN_IN_NAME }) {
            throw CategoryException("分类名不能包含「/」")
        }
        return name
    }

    private suspend fun requireUniqueName(parentId: Long?, name: String, excludeId: Long = -1L) {
        if (categoryDao.countSiblingsNamed(parentId, name, excludeId) > 0) {
            throw CategoryException("同一层级下已经有一个叫「$name」的分类了")
        }
    }

    /** 重算整棵子树的 path：在应用层按层级关系重新拼接，避免 SQL REPLACE 误伤。 */
    private suspend fun repathSubtree(oldPath: String, newPath: String, excludeId: Long) {
        val subtree = categoryDao
            .getSubtree(escapeLike(oldPath) + PATH_SEPARATOR + "%")
            .filter { it.id != excludeId }
        if (subtree.isEmpty()) return

        val byId = subtree.associateBy { it.id }
        // 按路径长度升序处理，保证父节点先算好
        subtree.sortedBy { it.path.length }.forEach { node ->
            val parentId = node.parentId
            val newParentPath = when {
                parentId == null -> null
                parentId == excludeId -> newPath
                else -> byId[parentId]?.path ?: categoryDao.getById(parentId)?.path
            }
            val recomputed = if (newParentPath.isNullOrEmpty()) {
                node.name
            } else {
                "$newParentPath$PATH_SEPARATOR${node.name}"
            }
            if (recomputed != node.path) {
                categoryDao.update(node.copy(path = recomputed))
            }
        }
    }
}

class CategoryException(message: String) : Exception(message)
