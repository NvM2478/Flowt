package com.flowt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowt.app.ui.AppViewModel
import com.flowt.app.ui.FlowtApp
import com.flowt.app.ui.theme.FlowtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsState()
            // 主题在 Compose 树的最顶层应用：配色和明暗模式变化会立即重建整棵树，
            // 所以设置页改配色后无需重启即可生效。
            FlowtTheme(prefs = prefs) {
                FlowtApp(vm = vm)
            }
        }
    }
}
