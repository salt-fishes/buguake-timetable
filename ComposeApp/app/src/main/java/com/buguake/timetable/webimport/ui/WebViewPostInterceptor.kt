package com.buguake.timetable.webimport.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit

/*
 * WebView 桌面模式请求重放（移植自拾光课程表 shiguangschedule，Apache-2.0,
 * Copyright 2025 XingHeYuZhuan；原实现见
 * shared/.../schoolselection/web/WebViewRequestInterceptor.kt 与 AndroidWebConstants.kt，
 * 网络层由 Ktor 改为本项目已有的 okhttp）。
 *
 * 为什么需要：Android WebView 的每个请求都会自动附加 `X-Requested-With: <包名>`，
 * 部分教务厂商（如超星）的反爬/防抓取会据此把请求识别为"未知 App 的内嵌浏览器"
 * 而拒绝登录/接口调用。拾光的解法是桌面模式下由原生层重放主框架 GET 与
 * XHR/Fetch/Form POST（剥掉该特征头、显式带 Cookie），使请求看起来与桌面浏览器一致。
 * 湖北文理学院（hbuas.jw.chaoxing.com）等超星站点依赖此通道才能正常登录。
 */

/** 注入页面：接管 XHR / Fetch / Form POST，把请求体经 [WebPostBridge] 暂存给原生层重放。 */
val JS_INTERCEPT_POST: String = """
(function() {
    var bridge = window.WebPostService;
    if (!bridge || window._postInterceptInjected) return;
    window._postInterceptInjected = true;

    var requestIdHeader = 'X-WebView-Post-Id';
    var requestIdParam = '_webview_post_id';

    function register(id, body, contentType) {
        try {
            if (window.WebPostService && window.WebPostService.register) {
                window.WebPostService.register(id, body, contentType || '');
            }
        } catch(e) {
            console.error("WebPostService register error:", e);
        }
    }

    // --- 1. XMLHttpRequest Interception ---
    var oldOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._method = method;
        this._url = url;
        this._headers = {};
        return oldOpen.apply(this, arguments);
    };

    var oldSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        try {
            if (this._headers && header) {
                this._headers[header] = value;
            }
            if (header && header.toLowerCase() === 'x-requested-with') return;
        } catch(e) {
            console.error("XHR setRequestHeader error:", e);
        }
        return oldSetRequestHeader.apply(this, arguments);
    };

    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        try {
            if (this._method && this._method.toUpperCase() !== 'GET' && body) {
                var id = 'xhr_' + Date.now() + '_' + Math.random().toString(36).substr(2);
                var contentType = (this._headers && (this._headers['Content-Type'] || this._headers['content-type'])) || '';

                var bodyStr = '';
                if (typeof body === 'string') {
                    bodyStr = body;
                } else if (window.FormData && body instanceof FormData) {
                    if (window.URLSearchParams) {
                        var params = new URLSearchParams();
                        for (var pair of body.entries()) {
                            params.append(pair[0], pair[1]);
                        }
                        bodyStr = params.toString();
                    }
                    if (!contentType) contentType = 'application/x-www-form-urlencoded';
                } else if (window.URLSearchParams && body instanceof URLSearchParams) {
                    bodyStr = body.toString();
                    if (!contentType) contentType = 'application/x-www-form-urlencoded';
                }

                if (bodyStr) {
                    register(id, bodyStr, contentType);
                    this.setRequestHeader(requestIdHeader, id);
                }
            }
        } catch(e) {
            console.error("XHR send intercept error:", e);
        }
        return oldSend.apply(this, arguments);
    };

    // --- 2. Fetch API Interception ---
    if (window.fetch) {
        var oldFetch = window.fetch;
        window.fetch = function(input, init) {
            try {
                var options = init || {};
                var method = options.method;

                if (!method && input && typeof input === 'object' && input.method) {
                    method = input.method;
                }
                if (!method) method = 'GET';

                var body = options.body;

                if (method.toUpperCase() !== 'GET' && body) {
                    var id = 'fetch_' + Date.now() + '_' + Math.random().toString(36).substr(2);
                    var contentType = '';

                    var headers = options.headers || (input && typeof input === 'object' ? input.headers : null);
                    if (headers) {
                        if (window.Headers && headers instanceof Headers) {
                            contentType = headers.get('Content-Type') || headers.get('content-type') || '';
                        } else if (Array.isArray(headers)) {
                            for (var i = 0; i < headers.length; i++) {
                                if (headers[i] && headers[i][0] && headers[i][0].toLowerCase() === 'content-type') {
                                    contentType = headers[i][1];
                                    break;
                                }
                            }
                        } else if (typeof headers === 'object') {
                            contentType = headers['Content-Type'] || headers['content-type'] || '';
                        }
                    }

                    var bodyStr = '';
                    if (typeof body === 'string') {
                        bodyStr = body;
                    } else if (window.URLSearchParams && body instanceof URLSearchParams) {
                        bodyStr = body.toString();
                        if (!contentType) contentType = 'application/x-www-form-urlencoded';
                    } else if (window.FormData && body instanceof FormData) {
                        if (window.URLSearchParams) {
                            var p = new URLSearchParams();
                            for (var pair of body.entries()) {
                                p.append(pair[0], pair[1]);
                            }
                            bodyStr = p.toString();
                        }
                        if (!contentType) contentType = 'application/x-www-form-urlencoded';
                    }

                    if (bodyStr) {
                        register(id, bodyStr, contentType);

                        if (!options.headers) options.headers = {};
                        if (window.Headers && options.headers instanceof Headers) {
                            options.headers.set(requestIdHeader, id);
                        } else if (Array.isArray(options.headers)) {
                            options.headers.push([requestIdHeader, id]);
                        } else if (typeof options.headers === 'object') {
                            options.headers[requestIdHeader] = id;
                        }
                    }
                }
                return oldFetch.call(this, input, options);
            } catch(e) {
                console.error("Fetch intercept error:", e);
                return oldFetch.apply(this, arguments);
            }
        };
    }

    // --- 3. Traditional Form Submit Interception ---
    document.addEventListener('submit', function(e) {
        try {
            var form = e.target;
            if (!form || !form.tagName || form.tagName.toLowerCase() !== 'form') return;
            if (!form.method || form.method.toLowerCase() !== 'post') return;

            if (form.querySelector && form.querySelector('input[type="file"]')) return;

            var id = 'form_' + Date.now() + '_' + Math.random().toString(36).substr(2);
            var formData = new FormData(form);

            var submitter = e.submitter || document.activeElement;
            if (submitter && submitter.form === form && submitter.name) {
                formData.append(submitter.name, submitter.value);
            }

            if (window.URLSearchParams) {
                var params = new URLSearchParams(formData);
                register(id, params.toString(), 'application/x-www-form-urlencoded');

                var action = form.getAttribute('action') || window.location.href;
                var separator = action.indexOf('?') !== -1 ? '&' : '?';
                form.setAttribute('action', action + separator + requestIdParam + '=' + id);
            }
        } catch(err) {
            console.error("Form submit intercept error:", err);
        }
    }, true);
})();
""".trimIndent()

/** JS 侧暂存 POST 请求体的桥（页面脚本经 window.WebPostService 调用）。 */
class WebPostBridge {
    @JavascriptInterface
    fun register(id: String, body: String, type: String) {
        WebViewPostInterceptor.registerPostData(id, body, type)
    }
}

/**
 * 桌面模式请求重放器：主框架 GET 与带标记的 POST 由原生 okhttp 重发
 * （剥掉 X-Requested-With 等特征头），其余请求原样放行 WebView 网络栈。
 * 所有方法线程安全；shouldInterceptRequest 在 WebView 的 IO 线程上阻塞调用。
 */
class WebViewPostInterceptor private constructor() {

    private val cookieManager = CookieManager.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** okhttp 会透明解压 gzip，因此必须从响应头剔除 Content-Encoding（见响应构造）。 */
    private val noRedirectClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val redirectClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @SuppressLint("SetJavaScriptEnabled")
    fun intercept(request: WebResourceRequest, isDesktopMode: Boolean): WebResourceResponse? {
        val rawUrl = request.url.toString()
        if (!rawUrl.startsWith("http") || !isDesktopMode) return null

        val requestId = request.requestHeaders["X-WebView-Post-Id"]
            ?: request.url.getQueryParameter("_webview_post_id")

        // 非主框架且未带 POST 标记 = 普通资源/GET AJAX，放行原生栈
        if (!request.isForMainFrame && requestId == null) return null

        // 剥掉内部凭据参数，还原真实 URL
        val url = if (request.url.queryParameterNames.contains("_webview_post_id")) {
            request.url.buildUpon().clearQuery().also { b ->
                request.url.queryParameterNames.forEach { name ->
                    if (name != "_webview_post_id") {
                        request.url.getQueryParameters(name).forEach { v -> b.appendQueryParameter(name, v) }
                    }
                }
            }.build().toString()
        } else rawUrl

        val registeredData = requestId?.let { postBodyRegistry.remove(it) }
        val method = request.method.uppercase()
        if (method != "GET" && registeredData == null) return null

        val client = if (request.isForMainFrame) noRedirectClient else redirectClient
        return try {
            val body: okhttp3.RequestBody? = when {
                registeredData != null -> {
                    val ct = registeredData.contentType.ifBlank { "application/x-www-form-urlencoded" }
                    registeredData.body.toByteArray(Charsets.UTF_8)
                        .toRequestBody(ct.toMediaTypeOrNull())
                }
                method != "GET" && method != "HEAD" ->
                    ByteArray(0).toRequestBody(null)
                else -> null
            }
            val rb = Request.Builder().url(url).method(method, body)
            request.requestHeaders.forEach { (k, v) ->
                // 与拾光一致：只剥两个特征/内部头，其余原样透传。
                // 特别注意 Accept-Encoding / Referer / Origin / Content-Type 必须保留——
                // 少发这些头会让服务端按"非浏览器客户端"处理，超星登录页会返回
                // 背景图正常但内容区被裁掉/空白的降级页面（hbuas.jw.chaoxing.com 实测）。
                // Host / Content-Length 由 okhttp 自行计算，请求头里本身不会出现。
                if (!k.equals("X-Requested-With", true) &&
                    !k.equals("X-WebView-Post-Id", true)
                ) rb.header(k, v)
            }
            cookieManager.getCookie(url)?.takeIf { it.isNotBlank() }?.let { rb.header("Cookie", it) }

            client.newCall(rb.build()).execute().use { resp ->
                // Set-Cookie 同步进 WebView 容器，保持会话一致
                resp.headers("Set-Cookie").forEach { c -> cookieManager.setCookie(url, c) }
                mainHandler.post { cookieManager.flush() }

                val status = resp.code
                // WebView 不能直接吃 3xx：主框架改为注入 JS 跳转
                if (status in 300..399 && request.isForMainFrame) {
                    val location = resp.header("Location")
                    if (location != null) {
                        val absolute = resolveAbsoluteUrl(url, location)
                        val html = "<html><script>window.location.replace('$absolute');</script></html>"
                        return@use WebResourceResponse(
                            "text/html", "UTF-8", 200, "OK",
                            mapOf("Cache-Control" to "no-cache"),
                            html.byteInputStream(),
                        )
                    }
                    return@use null
                }

                val mime = resp.header("Content-Type")?.substringBefore(";")?.ifBlank { null } ?: "text/html"
                val encoding = resp.header("Content-Type")
                    ?.substringAfter("charset=", "")
                    ?.substringBefore(";")
                    ?.takeIf { resp.header("Content-Type")?.contains("charset=") == true }
                    ?: "UTF-8"
                val headers = resp.headers.names()
                    .filter { !it.equals("Content-Encoding", true) }
                    .associateWith { h -> resp.headers(h).joinToString(", ") }
                // use{} 关闭前要取出流：okhttp 的 body 流在响应关闭后不可读，
                // 这里整段读入内存再回给 WebView（教务页面/接口体量小，可接受）
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                ByteArrayInputStream(bytes).let { stream: InputStream ->
                    WebResourceResponse(mime, encoding, status, resp.message.ifBlank { "OK" }, headers, stream)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("JwxtWeb", "请求重放失败（回退原生栈）: $url → ${e.message}")
            null
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, location: String): String =
        try {
            URI(baseUrl).resolve(location).toString()
        } catch (e: Exception) {
            location
        }

    private data class RegisteredPostData(val body: String, val contentType: String)

    companion object {
        private val postBodyRegistry: MutableMap<String, RegisteredPostData> =
            Collections.synchronizedMap(mutableMapOf())

        fun registerPostData(id: String, body: String, contentType: String) {
            postBodyRegistry[id] = RegisteredPostData(body, contentType)
        }

        @Volatile private var INSTANCE: WebViewPostInterceptor? = null

        /** 单例：两个 okhttp 客户端复用连接池，注册表全局共享（每页面一个 WebView）。 */
        fun get(): WebViewPostInterceptor =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebViewPostInterceptor().also { INSTANCE = it }
            }
    }
}
