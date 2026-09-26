package com.flowt.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowt.app.ui.theme.FlowtColors

/** 反馈邮箱：点击「反馈问题」时拉起邮件应用；没有邮件应用时复制到剪贴板兜底。 */
private const val FEEDBACK_EMAIL = "nvm2478@qq.com"

/** 一条更新记录：版本号 + 发布日期 + 变更条目。 */
private data class ChangelogEntry(
    val version: String,
    val date: String,
    val items: List<String>,
)

/**
 * 更新日志，**倒序**记录 —— 发新版本时把新条目加在列表最上面，
 * 并同步 build.gradle.kts 的 versionName / versionCode。
 */
private val changelog = listOf(
    ChangelogEntry(
        version = "0.1.0",
        date = "2026-09-26",
        items = listOf("首个发布版本"),
    ),
)

/**
 * 关于 Flowt：版本号、隐私说明与反馈渠道。
 *
 * 分发方式是直接发 APK、无服务端，所以没有检查更新/评分这类需要商店支撑的条目；
 * 隐私说明是这个 app 最大的信任卖点 —— 不请求网络权限，数据只在设备上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    var changelogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FlowtColors.current.navigationBarBackground,
                    titleContentColor = FlowtColors.current.navigationBarText,
                    navigationIconContentColor = FlowtColors.current.navigationBarText,
                ),
                title = { Text("关于 Flowt") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // —— 应用标识 ——
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(FlowtColors.current.accentBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "F",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = FlowtColors.current.accentButtonText,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Flowt",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FlowtColors.current.subtleText,
                )
            }

            // —— 隐私说明：纯本地是这个 app 最大的信任卖点 ——
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = FlowtColors.current.accentInlineText,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(text = "数据仅在本地", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Flowt 不请求网络权限，你的所有账单数据只保存在这台设备上。",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlowtColors.current.subtleText,
                        )
                    }
                }
            }

            // —— 更新日志 ——
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { changelogOpen = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = FlowtColors.current.accentInlineText,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text(text = "更新日志", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "看看每个版本改了什么",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlowtColors.current.subtleText,
                        )
                    }
                }
            }

            // —— 反馈渠道 ——
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openFeedbackEmail(context) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.MailOutline,
                        contentDescription = null,
                        tint = FlowtColors.current.accentInlineText,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "反馈问题", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = FEEDBACK_EMAIL,
                            style = MaterialTheme.typography.bodySmall,
                            color = FlowtColors.current.subtleText,
                        )
                    }
                }
            }
        }
    }

    if (changelogOpen) {
        ChangelogSheet(onDismiss = { changelogOpen = false })
    }
}

/** 更新日志弹窗：与取色弹窗同款的全高 ModalBottomSheet，倒序列出各版本的变更条目。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangelogSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "更新日志",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(16.dp))
            changelog.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.version,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = FlowtColors.current.subtleText,
                    )
                }
                Spacer(Modifier.height(4.dp))
                entry.items.forEach { item ->
                    Text(
                        text = "· $item",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 拉起邮件应用发反馈；设备没有邮件应用时复制邮箱到剪贴板并提示。 */
private fun openFeedbackEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$FEEDBACK_EMAIL".toUri()
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("email", FEEDBACK_EMAIL))
        Toast.makeText(context, "未找到邮件应用，邮箱已复制", Toast.LENGTH_SHORT).show()
    }
}
