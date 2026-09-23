package com.flowt.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowt.app.metrics.LedgerMetric
import com.flowt.app.ui.theme.THEME_ID_SYSTEM_DYNAMIC
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flowt_settings")

/** 明暗模式。保留枚举只为兼容旧数据；新逻辑把浅/暗做成独立的配色变体。 */
enum class BrightnessMode { SYSTEM, LIGHT, DARK }

/**
 * 用户保存的自定义配色方案。
 *
 * [overrides] 存的是**完整的 8 个角色颜色值**，所以它是自包含的：
 * 删除某个保存方案不会影响正在使用的主题，也不需要回退到任何基底。
 */
data class SavedTheme(
    val name: String,
    val overrides: Map<String, Int>,
)

/**
 * 一次已完成的导入，用于「这个文件已经导过了」的提醒。
 *
 * 为什么需要它：行级指纹里含分类 id，用户第二次导入同一个文件时若改了某个分类的
 * 去向，指纹就不同了，只靠行级判重会把同一批数据放进库两遍。文件内容哈希与
 * 分类映射、列绑定都无关，所以这一层挡得住。
 */
data class ImportedFileRecord(
    /** 文件内容的 SHA-256。与文件名无关——改了名仍是同一个文件，照样认得出。 */
    val hash: String,
    val fileName: String,
    val importedAt: Long,
    val count: Int,
    /**
     * 那一次导入的批次号，写在每条流水的 `source` 上。
     *
     * 「撤销上次导入」按它整批删除。之所以记批次号而不是流水 id 列表：
     * 编辑流水时 `source` 是保留的（只改被编辑的字段），所以用户改过金额、分类、
     * 备注之后照样撤得掉 —— 而库里只多存了一个字符串，不是几千个 id。
     *
     * 旧版本的记录没有这个字段（空串），那条记录就撤销不了，但仍可用于重复提醒。
     */
    val batchSource: String,
)

/**
 * UI 偏好设置。
 *
 * 刻意**不放进 Room**：这里存的都是界面偏好（显示哪几个指标、用哪套配色），
 * 不是业务数据。放 Room 意味着每次加一个开关都要升级 schema + 写迁移，
 * 而它们完全不值得这个代价。
 */
data class AppPrefs(
    /** 显示在首页的指标，**顺序即首页渲染顺序**。 */
    val enabledMetrics: List<String> = LedgerMetric.defaultEnabledIds().toList(),
    /**
     * 未显示在首页的指标，顺序即它们在设置页下方区的排列。
     *
     * 指标拆成两个有序列表而不是一个 Set，是因为设置页的交互是
     * "点击在两个区之间搬运 + 长按拖动排序"，两个区都有用户亲手排的顺序，
     * Set 表达不了顺序，也就无法还原用户排好的版。
     */
    val disabledMetrics: List<String> = LedgerMetric.defaultDisabledIds(),
    /** 当前选中的配色：内置变体 id（"violet@dark"）或 "system_dynamic" 或 "saved:<名称>"。 */
    val selectedThemeId: String = "violet@light",
    /** 用户保存的方案。 */
    val savedThemes: List<SavedTheme> = emptyList(),
    /** 当前临时生效的角色覆盖（未保存的微调）。 */
    val roleOverrides: Map<String, Int> = emptyMap(),
    /** 已导入过的文件（按内容哈希），用于导入前的重复提醒。 */
    val importedFiles: List<ImportedFileRecord> = emptyList(),
)

class PrefsRepository(private val context: Context) {

    private object Keys {
        val ENABLED_METRICS = stringPreferencesKey("enabled_metrics_ordered")
        val DISABLED_METRICS = stringPreferencesKey("disabled_metrics_ordered")
        val SELECTED_THEME = stringPreferencesKey("selected_theme_id")
        val SAVED_THEMES = stringSetPreferencesKey("saved_themes")
        val ROLE_OVERRIDES = stringSetPreferencesKey("role_overrides")
        val IMPORTED_FILES = stringSetPreferencesKey("imported_files")

        // 旧字段：仅用于一次性迁移，读过即可，不再写回
        val LEGACY_THEME_ID = stringPreferencesKey("theme_id")
        val LEGACY_BRIGHTNESS = stringPreferencesKey("brightness_mode")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            enabledMetrics = decodeMetrics(p[Keys.ENABLED_METRICS])
                ?: LedgerMetric.defaultEnabledIds().toList(),
            disabledMetrics = decodeMetrics(p[Keys.DISABLED_METRICS])
                ?: LedgerMetric.defaultDisabledIds(),
            selectedThemeId = p[Keys.SELECTED_THEME] ?: migrateLegacySelection(p),
            savedThemes = decodeSavedThemes(p[Keys.SAVED_THEMES]),
            roleOverrides = decodeOverrides(p[Keys.ROLE_OVERRIDES]),
            importedFiles = decodeImportedFiles(p[Keys.IMPORTED_FILES]),
        )
    }

    /**
     * 保存指标的显示状态与顺序。
     *
     * 两个列表都要传：它们在设置页是两个可互相搬运的有序区，
     * 分开写会出现"两个区都认为某个指标在自己这里"的中间态。
     */
    suspend fun setMetricOrder(enabled: List<String>, disabled: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENABLED_METRICS] = enabled.joinToString(",")
            prefs[Keys.DISABLED_METRICS] = disabled.joinToString(",")
        }
    }

    /** 切换配色方案。调用方负责在此之前处理"未保存的自定义"，这里只负责写入。 */
    suspend fun setSelectedTheme(selectionId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_THEME] = selectionId
            // 切换方案 = 放弃当前未保存的微调，否则旧覆盖会莫名其妙地套在新方案上
            prefs.remove(Keys.ROLE_OVERRIDES)
            prefs.remove(Keys.LEGACY_THEME_ID)
            prefs.remove(Keys.LEGACY_BRIGHTNESS)
        }
    }

    /** 覆盖单个颜色角色的颜色值（临时，未保存）。 */
    suspend fun setRoleOverride(roleName: String, argb: Int) {
        context.dataStore.edit { prefs ->
            val current = decodeOverrides(prefs[Keys.ROLE_OVERRIDES]).toMutableMap()
            current[roleName] = argb
            prefs[Keys.ROLE_OVERRIDES] = encodeOverrides(current)
        }
    }

    /** 恢复某个角色的默认颜色（不再覆盖）。 */
    suspend fun clearRoleOverride(roleName: String) {
        context.dataStore.edit { prefs ->
            val current = decodeOverrides(prefs[Keys.ROLE_OVERRIDES]).toMutableMap()
            current.remove(roleName)
            prefs[Keys.ROLE_OVERRIDES] = encodeOverrides(current)
        }
    }

    /**
     * 把当前的角色覆盖存成一个命名方案，并切换到它。
     *
     * 若当前选中的是内置方案，会把该方案的基底色一并写进保存值 ——
     * 这样保存下来的是一个**完整自包含**的配色，删掉它也不影响任何东西。
     */
    suspend fun saveCurrentAsTheme(
        name: String,
        completeOverrides: Map<String, Int>,
    ) {
        context.dataStore.edit { prefs ->
            val existing = decodeSavedThemes(prefs[Keys.SAVED_THEMES])
                .filterNot { it.name == name } // 同名覆盖
            val updated = existing + SavedTheme(name = name, overrides = completeOverrides)
            prefs[Keys.SAVED_THEMES] = encodeSavedThemes(updated)
            prefs[Keys.SELECTED_THEME] = "$SAVED_PREFIX$name"
            prefs.remove(Keys.ROLE_OVERRIDES)
        }
    }

    /** 删除一个保存的方案。如果它正在使用，切回跟随系统取色，避免悬空。 */
    suspend fun deleteSavedTheme(name: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeSavedThemes(prefs[Keys.SAVED_THEMES]).filterNot { it.name == name }
            prefs[Keys.SAVED_THEMES] = encodeSavedThemes(updated)
            if (prefs[Keys.SELECTED_THEME] == "$SAVED_PREFIX$name") {
                prefs[Keys.SELECTED_THEME] = THEME_ID_SYSTEM_DYNAMIC
            }
        }
    }

    /** 记下一次成功导入。按 hash 去重，同一文件重复导入只保留最新一条。 */
    suspend fun recordImportedFile(record: ImportedFileRecord) {
        context.dataStore.edit { prefs ->
            val updated = decodeImportedFiles(prefs[Keys.IMPORTED_FILES])
                .filterNot { it.hash == record.hash } + record
            prefs[Keys.IMPORTED_FILES] = encodeImportedFiles(updated)
        }
    }

    /** 抹掉全部导入记录（清空数据时一并做掉）。 */
    suspend fun clearImportedFiles() {
        context.dataStore.edit { prefs -> prefs.remove(Keys.IMPORTED_FILES) }
    }

    /** 撤销导入时一并移除记录——撤销就是完全回滚，用户重导不该被警告。 */
    suspend fun forgetImportedFile(hash: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeImportedFiles(prefs[Keys.IMPORTED_FILES])
                .filterNot { it.hash == hash }
            prefs[Keys.IMPORTED_FILES] = encodeImportedFiles(updated)
        }
    }

    // --- 编码工具 ---
    // DataStore Preferences 没有 Map/对象类型，这些数据量极小（最多十来个条目），
    // 用字符串集合编码即可，不值得为此引入序列化库。

    /** 指标顺序用逗号分隔存储。顺序本身是语义的一部分，所以不能用 Set（会丢顺序）。 */
    private fun decodeMetrics(raw: String?): List<String>? =
        raw?.split(',')?.filter { it.isNotBlank() }

    private fun encodeOverrides(map: Map<String, Int>): Set<String> =
        map.entries.map { "${it.key}:${it.value}" }.toSet()

    private fun decodeOverrides(raw: Set<String>?): Map<String, Int> =
        raw.orEmpty().mapNotNull { entry ->
            val separator = entry.lastIndexOf(':')
            if (separator <= 0) return@mapNotNull null
            val name = entry.substring(0, separator)
            val value = entry.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
            name to value
        }.toMap()

    private fun encodeSavedThemes(themes: List<SavedTheme>): Set<String> =
        themes.map { theme ->
            val pairs = theme.overrides.entries.joinToString(",") { "${it.key}=${it.value}" }
            "${theme.name}|$pairs"
        }.toSet()

    private fun decodeSavedThemes(raw: Set<String>?): List<SavedTheme> =
        raw.orEmpty().mapNotNull { entry ->
            val name = entry.substringBefore('|', "")
            val rest = entry.substringAfter('|', "")
            if (name.isEmpty()) return@mapNotNull null
            val overrides = rest.split(',').mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                val value = pair.substringAfter('=', "").toIntOrNull()
                if (key.isEmpty() || value == null) null else key to value
            }.toMap()
            SavedTheme(name = name, overrides = overrides)
        }.sortedBy { it.name }

    /** 文件名放最后一段：配合 split 的 limit，文件名里即便含 "|" 也不会被切断。 */
    private fun encodeImportedFiles(records: List<ImportedFileRecord>): Set<String> =
        records.map { "${it.hash}|${it.importedAt}|${it.count}|${it.batchSource}|${it.fileName}" }.toSet()

    /** 兼容旧格式：早期只存了 4 段（没有批次号），那条记录撤销不了但还能做重复提醒。 */
    private fun decodeImportedFiles(raw: Set<String>?): List<ImportedFileRecord> =
        raw.orEmpty().mapNotNull { entry ->
            val parts = entry.split('|', limit = 5)
            if (parts.size < 4) return@mapNotNull null
            val importedAt = parts[1].toLongOrNull() ?: return@mapNotNull null
            val count = parts[2].toIntOrNull() ?: return@mapNotNull null
            val hasBatch = parts.size == 5
            ImportedFileRecord(
                hash = parts[0],
                fileName = if (hasBatch) parts[4] else parts[3],
                importedAt = importedAt,
                count = count,
                batchSource = if (hasBatch) parts[3] else "",
            )
        }.sortedByDescending { it.importedAt }

    /** 老版本只存了 themeId + brightnessMode，这里转成新的单值选择。 */
    private fun migrateLegacySelection(p: Preferences): String {
        val legacyTheme = p[Keys.LEGACY_THEME_ID] ?: return "violet@light"
        if (legacyTheme == THEME_ID_SYSTEM_DYNAMIC) return THEME_ID_SYSTEM_DYNAMIC
        val legacyDark = p[Keys.LEGACY_BRIGHTNESS] == BrightnessMode.DARK.name
        return "${legacyTheme}@${if (legacyDark) "dark" else "light"}"
    }

    companion object {
        /** 保存方案的选中 id 前缀。 */
        const val SAVED_PREFIX = "saved:"
    }
}
