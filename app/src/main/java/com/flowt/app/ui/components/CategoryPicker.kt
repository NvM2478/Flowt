package com.flowt.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.data.db.Category
import com.flowt.app.ui.theme.subtleTextColor

/** 由分类列表算出「父 id → 子分类」，省得每个调用方各写一遍。 */
fun childrenByParentOf(categories: List<Category>): Map<Long?, List<Category>> =
    categories.groupBy { it.parentId }

/**
 * 分类下钻选择器：默认显示一级分类；点有子类的分类继续下钻，
 * 点叶子分类直接选中。每层只加一次点击 —— 这就是"3 步封顶"在多层树上的实现方式。
 *
 * 记账页与导入页共用：两处做的事都是"从这棵树里挑一个分类"，
 * 下钻的交互已经调好，没有理由写第二份。
 */
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    childrenByParent: Map<Long?, List<Category>> = childrenByParentOf(categories),
    onPick: (Long) -> Unit,
) {
    var currentParentId by remember { mutableStateOf<Long?>(null) }
    val pathById = remember(categories) { categories.associate { it.id to it.path } }

    val currentChildren = childrenByParent[currentParentId].orEmpty()
        .sortedWith(compareBy({ it.sortOrder }, { it.name }))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 460.dp)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentParentId?.let { pathById[it] } ?: "全部分类",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (currentParentId != null) {
                TextButton(onClick = { currentParentId = null }) { Text("返回顶层") }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (currentChildren.isEmpty()) {
            Text(
                text = "这里还没有分类。去「设置 → 分类管理」建几个吧。",
                style = MaterialTheme.typography.bodyMedium,
                color = subtleTextColor(),
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(currentChildren, key = { it.id }) { node ->
                    val hasChildren = childrenByParent[node.id].orEmpty().isNotEmpty()
                    Card(
                        onClick = {
                            if (hasChildren) currentParentId = node.id else onPick(node.id)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(node.name, style = MaterialTheme.typography.bodyLarge)
                            if (hasChildren) {
                                Text(
                                    text = "›",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = subtleTextColor(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
