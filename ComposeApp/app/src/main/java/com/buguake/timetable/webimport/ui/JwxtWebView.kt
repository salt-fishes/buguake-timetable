package com.buguake.timetable.webimport.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
    /** 回填 WebView 自带 UA（调用方切回手机模式时复用，与拾光 WebCompatDelegate 一致）。 */
    onDefaultUserAgent: (String) -> Unit = {},
) {
    // 桌面模式可随时切换（切 UA + 重载），回调里读最新值
    val desktop by rememberUpdatedState(desktopMode)
    val notifyDefaultUa by rememberUpdatedState(onDefaultUserAgent)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                val defaultUa = settings.userAgentString
                notifyDefaultUa(defaultUa)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    allowFileAccess = true
                    allowContentAccess = true
                    // 学校站点常见 http 资源混载，放行
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                applyDesktopMode(this, desktop, defaultUa)
                // 仅 debug 构建允许 chrome://inspect 远程调试：
                // 正式包若放开，任何拿到设备的人都能查看教务会话页面内容
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                addJavascriptInterface(bridge, bridgeName)
                // POST 重放桥：桌面模式下的 XHR/Fetch/Form 请求体经原生层重发
                addJavascriptInterface(WebPostBridge(), "WebPostService")
                val interceptor = WebViewPostInterceptor.get()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): android.webkit.WebResourceResponse? =
                        // 桌面模式：主框架 GET 与标记过的 POST 由原生重放（剥 X-Requested-With 特征头）
                        interceptor.intercept(request, desktop)
                            .also { if (it == null) android.util.Log.d("JwxtWeb", "pass: ${request.method} ${request.url}") }

                    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // 尽早注入：页面自己的脚本发出首个 XHR 前必须已挂上钩子
                        view.evaluateJavascript(JS_INTERCEPT_POST, null)
                        onPageStarted(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (desktop) injectDesktopViewportFix(view)
                        // 页面跳转/重载会重建 JS 环境，落点时补一次注入（脚本自身幂等）
                        view.evaluateJavascript(JS_INTERCEPT_POST, null)
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

/** 切换桌面/手机 UA（Cookie 会话保留；模式差异与拾光 WebCompatDelegate 一致）。 */
fun applyDesktopMode(wv: WebView, desktop: Boolean, defaultUserAgent: String? = null) {
    wv.settings.userAgentString = if (desktop) {
        DESKTOP_USER_AGENT
    } else {
        defaultUserAgent ?: android.webkit.WebSettings.getDefaultUserAgent(wv.context)
    }
    // 桌面模式放大正文，避免 PC 页在窄屏上文字过小（拾光同款 TEXT_AUTOSIZING）
    wv.settings.layoutAlgorithm = if (desktop) {
        android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
    } else {
        android.webkit.WebSettings.LayoutAlgorithm.NORMAL
    }
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
}

/**
 * 桌面模式下修 body 高度塌陷。
 *
 * 拾光（WebCompatDelegate）不做任何 viewport 注入，本函数同样**不注入、不覆盖 viewport**——
 * 之前的 `width=1280` 注入是造成超星登录页空白的元凶之一。
 *
 * 但 Android WebView 对一个"没有 viewport 声明"的文档，会把根元素高度算成 0；
 * 而教务站点登录页大量使用 `html,body{height:100%}` + `body{overflow:hidden}`，
 * 于是 body 高 0、内容被整块裁掉，只剩背景图（hbuas.jw.chaoxing.com 实测：
 * body 高 0 / `.loginMain` 高 0 / 登录框不可见）。
 * 这里只在"body 确实高 0 且内部有内容"时补一个 min-height 兜底，页面自身布局正常时不动它。
 */
fun injectDesktopViewportFix(wv: WebView) {
    wv.evaluateJavascript(
        """
        (function() {
            try {
                var html = document.documentElement;
                var body = document.body;
                if (!body) return;
                if (body.getBoundingClientRect().height >= 1) return;
                var inner = Math.max(body.scrollHeight || 0, html.scrollHeight || 0);
                if (inner < 2) return;
                body.style.minHeight = Math.max(inner, html.clientHeight) + 'px';
            } catch(e) {}
        })();
        """.trimIndent(),
        null,
    )
}
