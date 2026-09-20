package com.flowt.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类节点。
 *
 * 设计要点（对应已定的设计决策）：
 * - 树形结构用 parentId 自关联，**层数不设限制**（想几层就几层）。
 * - path 是**派生字段**，由程序从 parentId 链自动生成，绝不接受用户手输；
 *   它的作用是让"查某分类及其整棵子树"变成一次前缀查询，而不必写递归 SQL。
 * - id + parentId 是唯一真相，path 只是为查询和导出服务的缓存。
 *   path 上的唯一索引是"同级不允许重名"的兜底约束。
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["parentId"]),
    ],
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** null 表示根分类（一级分类）。 */
    val parentId: Long? = null,

    /** 分类名本身，**不允许包含分隔符 `/`**（会导致路径歧义）。 */
    val name: String,

    /** 完整路径，例如 "餐饮/外卖"；由程序生成与维护。 */
    val path: String,

    /** 展示排序用；同层内越小越靠前。 */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
)
