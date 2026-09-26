package com.buguake.timetable.ui.mine

import com.buguake.timetable.ui.theme.*

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.buguake.timetable.data.SettingsRepository

/**
 * 通用 HTML 文档页：隐私政策 / 关于 都渲染 assets/html 下的静态页面。
 *
 * 排版与文案全在 HTML 里维护（改文案不必动 Kotlin），原生侧只负责标题栏、
 * 返回、以及可选的原生动作回调——「关于」页的「检查更新」通过一个极简的
 * JS 桥回传到 Compose（[onBridgeMessage] 收到 "checkUpdate"）。
 *
 * 视觉一致性：文档页固定用**实色底**（顶栏与正文同一个颜色），
 * 由 Compose 把当前主题的 surface 颜色作为 `bg` 参数交给 HTML，HTML 用它设置 CSS 变量。
 * 不用「半透明 + 磨砂」是因为 WebView 对 `backdrop-filter` 支持不可靠，
 * 半透明会让正文压在背景图上，顶栏与正文形成明显的两层色块。
 *
 * 主题状态同样通过 URL 参数传递（dark），避免 WebView 默认的
 * prefers-color-scheme 与应用内的深色设置不一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlDocScreen(
    title: String,
    assetPath: String,
    extraQuery: String? = null,
    onBridgeMessage: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 应用内的深色设置优先，未指定时跟随系统
    val darkMode = remember(context) {
        runCatching { SettingsRepository.getInstance(context).current.darkMode }.getOrDefault("system")
    }
    val dark = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val surface = MaterialTheme.colorScheme.surface
    val surfaceArgb = remember(surface) { surface.toArgb() }
    val bgHex = remember(surfaceArgb) {
        String.format("%06X", surfaceArgb and 0xFFFFFF)
    }
    val url = remember(assetPath, dark, bgHex, extraQuery) {
        buildString {
            append("file:///android_asset/").append(assetPath)
            append("?dark=").append(if (dark) 1 else 0)
            append("&bg=").append(bgHex)
            if (!extraQuery.isNullOrBlank()) append("&").append(extraQuery)
        }
    }

    Scaffold(
        containerColor = surface,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SketchArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            factory = { ctx ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false  // assets 仍可读，禁用任意文件访问
                    settings.setSupportZoom(false)
                    // 与顶栏同色，避免首帧白闪造成"分层"观感
                    setBackgroundColor(surfaceArgb)
                    isVerticalScrollBarEnabled = true
                    webViewClient = WebViewClient()  // 站内页面，不跳系统浏览器
                    if (onBridgeMessage != null) {
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun post(msg: String?) {
                                    msg?.let(onBridgeMessage)
                                }
                            },
                            "AndroidBridge",
                        )
                    }
                    loadUrl(url)
                }
            },
        )
    }
}
