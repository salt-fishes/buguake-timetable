package com.buguake.timetable.webimport.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.buguake.timetable.BuildConfig

/** 桌面 UA：教务/CAS 页面按 PC 设计（与拾光 DESKTOP_USER_AGENT 一致）。 */
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 教务页面 WebView 的统一外壳：**课程导入与校园考试读取共用同一套实现**。
 *
 * 统一的部分：JS/DOM/混合内容设置、桌面 UA、viewport 补丁、Cookie 策略、
 * 调试开关（仅 debug 允许 chrome://inspect）、进度与页面回调。
 * 各自不同的部分（桥名、注入脚本、底部操作）由调用方传入。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JwxtWebView(
    /** JS 桥的对象名（课程导入用 `_shiguangNativeBridge`，考试用 `CampusExamBridge`）。 */
    bridgeName: String,
    bridge: Any,
    modifier: Modifier = Modifier,
    startUrl: String? = null,
    desktopMode: Boolean = true,
    onPageStarted: (WebView, String) -> Unit = { _, _ -> },
    onPageFinished: (WebView, String) -> Unit = { _, _ -> },
    onHistoryChanged: (WebView, String) -> Unit = { _, _ -> },
    onProgress: (Int) -> Unit = {},
    /** 每次重组回调当前 WebView，调用方用它保存引用。 */
    onWebView: (WebView) -> Unit = {},
) {
    // 桌面模式可随时切换（切 UA + 重载），回调里读最新值
    val desktop by rememberUpdatedState(desktopMode)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    // 学校站点常见 http 资源混载，放行
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                applyDesktopMode(this, desktop)
                // 仅 debug 构建允许 chrome://inspect 远程调试：
                // 正式包若放开，任何拿到设备的人都能查看教务会话页面内容
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                addJavascriptInterface(bridge, bridgeName)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (desktop) injectDesktopViewportFix(view)
                        onPageFinished(view, url)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onHistoryChanged(view, url)
                    }
                }
                setWebChromeClient(object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    // 脚本错误/日志捕获：适配器脚本的执行异常都在这里现形
                    // （正式包不记录控制台内容：页面自行打印的信息可能含会话串）
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                        if (BuildConfig.DEBUG) {
                            android.util.Log.i(
                                "JwxtWeb",
                                "JS[${consoleMessage.messageLevel()}] ${consoleMessage.message()} " +
                                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                            )
                        }
                        return true
                    }
                })
                startUrl?.takeIf { it.isNotBlank() }?.let { loadUrl(it) }
            }
        },
        update = { onWebView(it) },
        modifier = modifier,
    )
}

/** 切换桌面/手机 UA（Cookie 会话保留）。 */
fun applyDesktopMode(wv: WebView, desktop: Boolean) {
    wv.settings.userAgentString = if (desktop) DESKTOP_USER_AGENT
    else android.webkit.WebSettings.getDefaultUserAgent(wv.context)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
}

/** 桌面模式下补 viewport meta 并触发重排（避免 PC 页按 980px 挤压）。 */
fun injectDesktopViewportFix(wv: WebView) {
    wv.evaluateJavascript(
        """
        (function() {
            try {
                var metas = document.getElementsByTagName('meta');
                for (var i = metas.length - 1; i >= 0; i--) {
                    if (metas[i].getAttribute('name') === 'viewport') {
                        metas[i].parentNode.removeChild(metas[i]);
                    }
                }
                var meta = document.createElement('meta');
                meta.name = 'viewport';
                meta.content = 'width=1280, initial-scale=1.0, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes';
                document.head.appendChild(meta);
                window.dispatchEvent(new Event('resize'));
            } catch(e) {}
        })();
        """.trimIndent(),
        null,
    )
}
