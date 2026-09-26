package com.flowt.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.FlowtApp
import com.flowt.app.ui.theme.FlowtTheme

class MainActivity : ComponentActivity() {

    /**
     * 系统当前的深浅模式。清单里给 Activity 声明了 `configChanges="uiMode"`：
     * 系统切换深浅时 Activity 不再重建，而是走到 [onConfigurationChanged] 更新这里 ——
     * Compose 树因此实时拿到新状态，主题即时切换，页面状态（比如正在调色）也不丢。
     */
    private val systemDark = mutableStateOf(false)

    /**
     * 壁纸版本号：每次回到前台递增。换壁纸必然离开 app 去系统设置/图库，
     * 回来时 onResume 让壁纸取色的记忆化失效、重新查询 —— 主题和预览立即跟上新壁纸。
     */
    private val wallpaperEpoch = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        systemDark.value = isSystemDark()
        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsState()
            // 主题在 Compose 树的最顶层应用：配色和明暗模式变化会立即重组，
            // 所以设置页改配色后无需重启即可生效。
            FlowtTheme(prefs = prefs, dark = systemDark.value, wallpaperEpoch = wallpaperEpoch.value) {
                FlowtApp(vm = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wallpaperEpoch.value += 1
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDark.value =
            (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
