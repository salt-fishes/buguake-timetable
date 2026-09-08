package com.saltfish.simple.webimport.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.saltfish.simple.data.ScheduleRepository
import com.saltfish.simple.data.SettingsRepository
import com.saltfish.simple.ui.timetable.TimetableInfo
import com.saltfish.simple.reminder.AppRefresh
import com.saltfish.simple.webimport.AdapterData
import com.saltfish.simple.webimport.SchoolData
import com.saltfish.simple.webimport.bridge.JS_BRIDGE_INIT
import com.saltfish.simple.webimport.bridge.WebBridgeHandler
import com.saltfish.simple.webimport.bridge.WebDialogHost
import kotlinx.coroutines.launch

/** 桌面 UA：教务/CAS 页面按 PC 浏览器设计（与拾光 DESKTOP_USER_AGENT 一致）。 */
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** 桥弹窗的 UI 状态（Host 渲染，Handler 回调驱动）。 */
private sealed interface BridgeDialog {
    data class Alert(
        val title: String,
        val content: String,
        val confirmText: String,
        val onResult: (Boolean) -> Unit,
    ) : BridgeDialog

    data class Prompt(
        val title: String,
        val tip: String,
        val defaultText: String,
        val validatorJs: String?,
        val onCancel: () -> Unit,
        val onSubmit: (input: String, onValidationError: (String) -> Unit, onSuccess: () -> Unit) -> Unit,
    ) : BridgeDialog

    data class SingleSelection(
        val title: String,
        val items: List<String>,
        val defaultSelectedIndex: Int,
        val onResult: (Int?) -> Unit,
    ) : BridgeDialog
}

/**
 * 教务网页导入屏：内嵌浏览器登录教务 → 选目标课表 → 注入适配器脚本执行导入。
 * 账号密码只在 WebView 会话内；「清除登录」一键清 Cookie。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWebViewScreen(
    school: SchoolData,
    adapter: AdapterData,
    jsContent: String,
    timetables: List<TimetableInfo>,
    defaultStartMillis: Long,
    defaultTotalWeeks: Int,
    glass: Boolean = false,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val scheduleRepo = remember { ScheduleRepository.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var importTableId by remember { mutableStateOf<Long?>(null) }
    var showTablePicker by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<BridgeDialog?>(null) }
    var injectedAtTable by remember { mutableStateOf<Long?>(null) }
    // 适配器脚本顶层 const 在全局作用域：同页二次注入会冲突，先重载页面再自动注入
    var pendingInject by remember { mutableStateOf(false) }
    // 桌面模式：教务/CAS 页面按 PC 设计，手机 UA 常被拒或排版错乱；默认开启
    var desktopMode by remember { mutableStateOf(true) }

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
            """.trimIndent(), null
        )
    }

    fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
        webViewRef?.evaluateJavascript(script, callback)
    }

    val handler = remember {
        WebBridgeHandler(
            context = context,
            scope = scope,
            dialogHost = object : WebDialogHost {
                override fun showAlert(title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit) {
                    dialog = BridgeDialog.Alert(title, content, confirmText, onResult)
                }

                override fun showPrompt(
                    title: String,
                    tip: String,
                    defaultText: String,
                    validatorJsFunction: String?,
                    onCancel: () -> Unit,
                    onSubmit: (input: String, onValidationError: (String) -> Unit, onSuccess: () -> Unit) -> Unit,
                ) {
                    dialog = BridgeDialog.Prompt(title, tip, defaultText, validatorJsFunction, onCancel, onSubmit)
                }

                override fun showSingleSelection(
                    title: String,
                    items: List<String>,
                    defaultSelectedIndex: Int,
                    onResult: (Int?) -> Unit,
                ) {
                    dialog = BridgeDialog.SingleSelection(title, items, defaultSelectedIndex, onResult)
                }
            },
            showToast = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            evaluateJs = ::evaluateJs,
            settingsRepo = settingsRepo,
            scheduleRepo = scheduleRepo,
            onTaskCompleted = {
                scope.launch { snackbarHostState.showSnackbar("导入任务完成，已返回") }
                AppRefresh.onDataChanged(context)
                onFinished()
            },
        )
    }

    // 目标课表变化同步给 Handler（保存动作以此为作用域）
    LaunchedEffect(importTableId) { handler.importTableId = importTableId }

    // 系统返回：WebView 可后退则先退页面，否则退出导入
    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    fun injectAdapter() {
        val tableId = importTableId ?: run { showTablePicker = true; return }
        if (injectedAtTable != null) {
            android.util.Log.i("WebImport", "同页重复注入：先重载页面再自动执行")
            pendingInject = true
            webViewRef?.reload()
            return
        }
        injectedAtTable = tableId
        evaluateJs("window.currentTableId = '$tableId';\n$jsContent", null)
        scope.launch { snackbarHostState.showSnackbar("已注入适配器脚本，正在执行…") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        adapter.name.ifBlank { school.name },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 桌面/手机模式切换：切 UA + 重载（Cookie 会话保留）
                    IconButton(onClick = {
                        desktopMode = !desktopMode
                        webViewRef?.let { wv ->
                            applyDesktopMode(wv, desktopMode)
                            wv.reload()
                        }
                    }) {
                        Text(
                            if (desktopMode) "桌面" else "手机",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            webViewRef?.reload()
                            scope.launch { snackbarHostState.showSnackbar("已清除登录会话") }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("清除登录") }
                    Button(
                        onClick = {
                            if (importTableId == null) showTablePicker = true else injectAdapter()
                        },
                        modifier = Modifier.weight(2f),
                    ) {
                        Text(importTableId?.let { id ->
                            "执行导入 → ${timetables.firstOrNull { it.timetable.id == id }?.timetable?.name ?: "已选课表"}"
                        } ?: "选择课表并导入")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        applyDesktopMode(this, desktopMode)
                        // debug 构建允许 chrome://inspect 远程调试
                        WebView.setWebContentsDebuggingEnabled(true)
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun postMessage(msg: String?) {
                                    msg ?: return
                                    handler.onMessageReceived(msg)
                                }
                            },
                            "_shiguangNativeBridge",
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                android.util.Log.i("WebImport", "页面加载: $url")
                                // 新页面 = 新 JS 全局作用域，重复注入守卫复位
                                injectedAtTable = null
                                // 每次导航都确保桥已挂载（脚本幂等）
                                view.evaluateJavascript(JS_BRIDGE_INIT, null)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                if (desktopMode) injectDesktopViewportFix(view)
                                if (pendingInject) {
                                    pendingInject = false
                                    // 等页面脚本（jQuery 等）就绪后自动重新注入
                                    view.postDelayed({ injectAdapter() }, 600)
                                }
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                view.evaluateJavascript(JS_BRIDGE_INIT, null)
                            }
                        }
                        setWebChromeClient(object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }

                            // 脚本错误/日志捕获：适配器脚本的执行异常都在这里现形
                            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                                android.util.Log.i(
                                    "WebImport",
                                    "JS[${consoleMessage.messageLevel()}] ${consoleMessage.message()} " +
                                        "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                                )
                                return true
                            }
                        })
                        adapter.importUrl.takeIf { it.isNotBlank() }?.let { loadUrl(it) }
                    }
                },
                update = { webViewRef = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ---- 目标课表选择弹窗 ----
    if (showTablePicker) {
        TablePickerDialog(
            timetables = timetables,
            currentId = importTableId,
            defaultStartMillis = defaultStartMillis,
            defaultTotalWeeks = defaultTotalWeeks,
            onPick = { id ->
                importTableId = id
                showTablePicker = false
                injectAdapter()
            },
            onDismiss = { showTablePicker = false },
        )
    }

    // ---- 桥弹窗 ----
    when (val d = dialog) {
        is BridgeDialog.Alert -> AlertDialog(
            onDismissRequest = {
                dialog = null
                d.onResult(false)
            },
            title = { Text(d.title) },
            text = { Text(d.content) },
            confirmButton = {
                TextButton(onClick = { dialog = null; d.onResult(true) }) { Text(d.confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null; d.onResult(false) }) { Text("取消") }
            },
        )

        is BridgeDialog.Prompt -> PromptDialog(
            state = d,
            onClose = { dialog = null },
            onCancel = {
                dialog = null
                d.onCancel()
            },
        )

        is BridgeDialog.SingleSelection -> SingleSelectionDialog(
            state = d,
            onClose = { dialog = null },
            onCancel = {
                dialog = null
                d.onResult(null)
            },
        )

        null -> {}
    }
}

@Composable
private fun PromptDialog(state: BridgeDialog.Prompt, onClose: () -> Unit, onCancel: () -> Unit) {
    var text by remember(state) { mutableStateOf(state.defaultText) }
    var error by remember(state) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(state.title) },
        text = {
            Column {
                if (state.tip.isNotBlank()) {
                    Text(
                        state.tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 校验失败经 onValidationError 回显错误、弹窗保持打开；成功后 onClose 关闭
                state.onSubmit(text, { err -> error = err }, onClose)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

@Composable
private fun SingleSelectionDialog(
    state: BridgeDialog.SingleSelection,
    onClose: () -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember(state) { mutableStateOf(state.defaultSelectedIndex) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(state.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                state.items.forEachIndexed { i, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = i }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == i, onClick = { selected = i })
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onClose()
                state.onResult(selected.takeIf { it >= 0 })
            }) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } },
    )
}

/** 目标课表选择弹窗：现有课表单选 + 新建空白课表。 */
@Composable
private fun TablePickerDialog(
    timetables: List<TimetableInfo>,
    currentId: Long?,
    defaultStartMillis: Long,
    defaultTotalWeeks: Int,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val scheduleRepo = remember { ScheduleRepository.getInstance(context) }
    var selected by remember { mutableStateOf(currentId ?: timetables.firstOrNull()?.timetable?.id) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标课表") },
        text = {
            Column {
                if (creating) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; error = null },
                        label = { Text("新课表名称") },
                        isError = error != null,
                        supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 360.dp)) {
                        timetables.forEach { info ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = info.timetable.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected == info.timetable.id,
                                    onClick = { selected = info.timetable.id },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        info.timetable.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${info.courseCount} 门课程",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { creating = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "新建空白课表",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (creating) {
                    val name = newName.trim()
                    if (name.isEmpty()) {
                        error = "请输入课表名称"
                        return@TextButton
                    }
                    scope.launch {
                        val id = runCatching {
                            scheduleRepo.createTimetable(name, defaultStartMillis, defaultTotalWeeks)
                        }.getOrNull()
                        if (id != null) {
                            onPick(id)
                        } else {
                            error = "创建失败，请重试"
                        }
                    }
                } else {
                    selected?.let(onPick)
                }
            }) { Text(if (creating) "创建并导入" else "确定") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (creating) creating = false else onDismiss()
            }) { Text(if (creating) "返回" else "取消") }
        },
    )
}
