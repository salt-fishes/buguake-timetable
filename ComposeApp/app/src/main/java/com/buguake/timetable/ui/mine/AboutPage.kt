package com.buguake.timetable.ui.mine

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.buguake.timetable.data.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 关于页：正文（功能、更新记录、开发者、兼容性）由 `assets/html/about.html` 渲染并已精简。
 * 只有「检查更新」保留原生实现——它要访问 GitHub 并弹出结果对话框，
 * HTML 里的按钮通过 JS 桥（AndroidBridge.post("checkUpdate")）回调到这里。
 */
@Composable
fun AboutPage(
    versionName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }

    // ---- 「检查更新」结果弹窗：手动触发，不做任何后台轮询 ----
    updateResult?.let { result ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(result.title) },
            text = { Text(result.message) },
            confirmButton = {
                if (result.url != null) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, result.url.toUri()))
                        }
                        updateResult = null
                    }) { Text("前往下载") }
                } else {
                    TextButton(onClick = { updateResult = null }) { Text("知道了") }
                }
            },
            dismissButton = if (result.url != null) {
                { TextButton(onClick = { updateResult = null }) { Text("稍后") } }
            } else null,
        )
    }

    HtmlDocScreen(
        title = "关于",
        assetPath = "html/about.html",
        extraQuery = "v=$versionName",
        onBridgeMessage = { message ->
            if (message == "checkUpdate" && !checkingUpdate) {
                checkingUpdate = true
                // 检查更新有阻塞网络请求（OkHttp execute），必须切到 IO 线程——
                // 主线程发起网络会被系统直接抛 NetworkOnMainThreadException，
                // 表现为「一点检查更新就立刻失败」
                scope.launch {
                    updateResult = withContext(Dispatchers.IO) {
                        runCatching { UpdateChecker.check(versionName) }
                    }.fold(
                        onSuccess = { info ->
                            if (info == null) {
                                UpdateResult(
                                    "已是最新版本",
                                    "当前版本 v$versionName 已是最新，无需更新。",
                                    null,
                                )
                            } else {
                                UpdateResult(
                                    "发现新版本 v${info.version}",
                                    info.notes.ifBlank { info.name },
                                    info.url,
                                )
                            }
                        },
                        onFailure = { e ->
                            UpdateResult(
                                "检查更新失败",
                                "无法连接 GitHub：${e.message ?: "未知错误"}",
                                null,
                            )
                        },
                    )
                    checkingUpdate = false
                }
            }
        },
        onBack = onBack,
    )
}

/** 「检查更新」结果弹窗数据；url 非空时可跳转下载。 */
private data class UpdateResult(
    val title: String,
    val message: String,
    val url: String?,
)
