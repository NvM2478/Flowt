package com.flowt.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowt.app.data.AppPrefs
import com.flowt.app.data.CategoryRepository
import com.flowt.app.data.ClearResult
import com.flowt.app.data.PrefsRepository
import com.flowt.app.data.TxRepository
import com.flowt.app.data.bill.ExportService
import com.flowt.app.data.bill.ImportService
import com.flowt.app.data.db.Category
import com.flowt.app.data.db.FlowtDatabase
import com.flowt.app.data.db.TransactionEntity
import com.flowt.app.metrics.MetricValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FlowtDatabase.get(app)

    val categoryRepo = CategoryRepository(db)
    val txRepo = TxRepository(db)
    val prefsRepo = PrefsRepository(app)

    /** 账单文件的导入与导出。都是无状态的编排器，直接挂在这里由界面调用。 */
    val importService = ImportService(db, txRepo, categoryRepo, prefsRepo)
    val exportService = ExportService(txRepo, categoryRepo)

    val prefs: StateFlow<AppPrefs> = prefsRepo.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    val transactions: StateFlow<List<TransactionEntity>> = txRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 首页指标卡：按用户勾选的指标实时计算。
     *
     * 不需要任何"数据已变更"的通知机制 —— Room 的 Flow 在表内容变化时
     * 会自动重发，指标因此自动重算。多加一层手动通知只会引入
     * "忘了通知导致界面不刷新"这类 bug。
     */
    val metrics: StateFlow<List<MetricValue>> =
        combine(txRepo.observeAll(), prefs) { list, pref ->
            MetricValue.computeAll(list, pref.enabledMetrics)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 全部指标的数值，与是否启用无关。
     * 「首页指标」设置页用它渲染未显示区的卡片 —— 那里也要显示真实金额。
     */
    val allMetrics: StateFlow<List<MetricValue>> =
        txRepo.observeAll()
            .map { list -> MetricValue.computeEveryMetric(list) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 清空流水与分类；[includeCategories] 为 true 时连分类树一起清。
     *
     * 顺带抹掉"这个文件导过了"的记录 —— 数据都清空了，还留着那些提醒，
     * 用户重新导入备份时会莫名其妙。这一步走 DataStore、不在 Room 事务里，
     * 万一失败也只是残留几条提醒，不影响账目本身。
     */
    suspend fun clearAllData(includeCategories: Boolean): ClearResult {
        val result = txRepo.clearAll(includeCategories)
        // 导入记录是附属信息：它清不掉也不该让"清空数据"看起来失败了 —— 那样用户会
        // 卡在对话框里反复点，而账目其实早就清空了
        runCatching { prefsRepo.clearImportedFiles() }
        return result
    }
}
