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
)

class PrefsRepository(private val context: Context) {

    private object Keys {
        val ENABLED_METRICS = stringPreferencesKey("enabled_metrics_ordered")
        val DISABLED_METRICS = stringPreferencesKey("disabled_metrics_ordered")
        val SELECTED_THEME = stringPreferencesKey("selected_theme_id")
        val SAVED_THEMES = stringSetPreferencesKey("saved_themes")
        val ROLE_OVERRIDES = stringSetPreferencesKey("role_overrides")

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
