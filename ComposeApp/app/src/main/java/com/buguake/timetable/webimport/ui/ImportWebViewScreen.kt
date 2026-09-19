package com.buguake.timetable.webimport.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.togetherWith
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import com.buguake.timetable.ui.theme.AppMotion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.buguake.timetable.BuildConfig
import com.buguake.timetable.data.ScheduleRepository
import com.buguake.timetable.data.SettingsRepository
import com.buguake.timetable.ui.timetable.TimetableInfo
import com.buguake.timetable.reminder.AppRefresh
import com.buguake.timetable.webimport.AdapterData
import com.buguake.timetable.webimport.SchoolData
import com.buguake.timetable.webimport.bridge.JS_BRIDGE_INIT
import com.buguake.timetable.webimport.bridge.WebBridgeHandler
import com.buguake.timetable.webimport.bridge.WebDialogHost
import kotlinx.coroutines.launch

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
    /** 已建好的目标课表 id（「新建课表 → 从教务网站导入」路径）：有值时不再弹课表选择。 */
    presetImportTableId: Long? = null,
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
    var importTableId by remember { mutableStateOf(presetImportTableId) }
    var showTablePicker by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<BridgeDialog?>(null) }
    var injectedAtTable by remember { mutableStateOf<Long?>(null) }
    // 适配器脚本顶层 const 在全局作用域：同页二次注入会冲突，先重载页面再自动注入
    var pendingInject by remember { mutableStateOf(false) }
    // 桌面模式：教务/CAS 页面按 PC 设计，手机 UA 常被拒或排版错乱；默认开启
    var desktopMode by remember { mutableStateOf(true) }
    // 地址栏：通用适配器没有默认入口，必须由使用者自己填教务网址。
    // 无默认入口时直接进入编辑态，避免出现"看似空白、不知从何下手"的页面（对齐拾光 WebViewScreen）。
    var currentUrl by remember { mutableStateOf(adapter.importUrl) }
    var urlInput by remember { mutableStateOf(adapter.importUrl) }
    var isEditingUrl by remember { mutableStateOf(adapter.importUrl.isBlank()) }
    var pageTitle by remember { mutableStateOf("") }
    // 原始 UA（切回手机模式用）；JwxtWebView 建好 WebView 后回填
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }

    val keyboard = LocalSoftwareKeyboardController.current

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

    // 拾光桥对象：适配器脚本 → Native（与考试读取共用同一套 WebView 外壳，只是桥名与脚本不同）
    val shiguangBridge = remember {
        object {
            @JavascriptInterface
            fun postMessage(msg: String?) {
                msg ?: return
                handler.onMessageReceived(msg)
            }
        }
    }

    // 目标课表变化同步给 Handler（保存动作以此为作用域）
    LaunchedEffect(importTableId) { handler.importTableId = importTableId }

    // 系统返回：先退出地址栏编辑 → WebView 可后退则退页面 → 退出导入
    BackHandler(enabled = true) {
        if (isEditingUrl) {
            isEditingUrl = false
            keyboard?.hide()
        } else {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
        }
    }

    fun goToUrl(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        keyboard?.hide()
        currentUrl = url
        isEditingUrl = false
        webViewRef?.loadUrl(url)
    }

    fun injectAdapter() {
        val tableId = importTableId ?: run {
            android.util.Log.i("TimetableCreate", "injectAdapter：importTableId 为空 → 打开课表选择弹窗")
            showTablePicker = true
            return
        }
        android.util.Log.i("TimetableCreate", "injectAdapter tableId=$tableId currentUrl=$currentUrl")
        if (injectedAtTable != null) {
            android.util.Log.i("WebImport", "同页重复注入：先重载页面再自动执行")
            pendingInject = true
            webViewRef?.reload()
            return
        }
        val wv = webViewRef
        if (wv == null || currentUrl.isBlank() || currentUrl == "about:blank") {
            // 还没进任何教务页面就注入，脚本找不到页面元素会静默失败——先把原因说清楚
            scope.launch {
                snackbarHostState.showSnackbar("请先在上方地址栏打开本校教务系统并登录，再执行导入")
            }
            if (!isEditingUrl) {
                urlInput = currentUrl
                isEditingUrl = true
            }
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
                    if (isEditingUrl) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = {
                                Text(
                                    "输入教务系统网址，如 https://xxx.edu.cn",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { goToUrl(urlInput) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    } else {
                        Text(
                            pageTitle.ifBlank { adapter.name.ifBlank { school.name } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditingUrl) {
                            isEditingUrl = false
                            keyboard?.hide()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditingUrl) "取消输入网址" else "返回",
                        )
                    }
                },
                actions = {
                    if (isEditingUrl) {
                        IconButton(
                            onClick = { goToUrl(urlInput) },
                            enabled = urlInput.trim().isNotBlank() && urlInput.trim() != "https://",
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "打开网址")
                        }
                    } else {
                        // 有默认入口的适配器也要能改网址：教务域名随时可能变更
                        IconButton(onClick = {
                            urlInput = currentUrl.takeIf { it.isNotBlank() && it != "about:blank" } ?: urlInput
                            isEditingUrl = true
                            keyboard?.show()
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "输入网址")
                        }
                    }
                    // 桌面/手机模式切换：切 UA + 重载（Cookie 会话保留）
                    TextButton(onClick = {
                        desktopMode = !desktopMode
                        webViewRef?.let { wv ->
                            applyDesktopMode(wv, desktopMode, defaultUserAgent)
                            wv.reload()
                        }
                    }) {
                        androidx.compose.animation.AnimatedContent(
                            targetState = desktopMode,
                            transitionSpec = {
                                androidx.compose.animation.fadeIn(com.buguake.timetable.ui.theme.AppMotion.effectsFast())
                                    .togetherWith(androidx.compose.animation.fadeOut(com.buguake.timetable.ui.theme.AppMotion.effectsFast()))
                            },
                            label = "uaToggle",
                        ) { desktop ->
                            Text(
                                if (desktop) "桌面" else "手机",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
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
            androidx.compose.animation.AnimatedVisibility(
                visible = progress < 100,
                enter = androidx.compose.animation.expandVertically(com.buguake.timetable.ui.theme.AppMotion.spatial()) +
                    androidx.compose.animation.fadeIn(com.buguake.timetable.ui.theme.AppMotion.effects()),
                exit = androidx.compose.animation.shrinkVertically(com.buguake.timetable.ui.theme.AppMotion.spatialFast()) +
                    androidx.compose.animation.fadeOut(com.buguake.timetable.ui.theme.AppMotion.effectsFast()),
            ) {
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress / 100f,
                    animationSpec = com.buguake.timetable.ui.theme.AppMotion.effects(),
                    label = "webProgress",
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            JwxtWebView(
                bridgeName = "_shiguangNativeBridge",
                bridge = shiguangBridge,
                startUrl = adapter.importUrl.takeIf { it.isNotBlank() },
                desktopMode = desktopMode,
                onPageStarted = { view, url ->
                    // 正式包不打印页面地址：教务 URL 常带会话参数
                    if (BuildConfig.DEBUG) android.util.Log.i("WebImport", "页面加载: $url")
                    val real = url.takeIf { it.isNotBlank() && it != "about:blank" }
                    if (real != null) {
                        currentUrl = real
                        // 正在编辑时不覆盖用户输入，否则打字会被导航事件打断
                        if (!isEditingUrl) urlInput = real
                    }
                    // 新页面 = 新 JS 全局作用域，重复注入守卫复位
                    injectedAtTable = null
                    // 每次导航都确保桥已挂载（脚本幂等）
                    view.evaluateJavascript(JS_BRIDGE_INIT, null)
                },
                onPageFinished = { view, _ ->
                    // 标题栏显示教务页自己的标题（选学期/课表页一眼可辨）
                    view.evaluateJavascript("document.title") { raw ->
                        val title = raw?.trim('"')?.replace("\\\"", "\"")?.trim().orEmpty()
                        if (title.isNotBlank() && title != "null") pageTitle = title
                    }
                    if (pendingInject) {
                        pendingInject = false
                        // 等页面脚本（jQuery 等）就绪后自动重新注入
                        view.postDelayed({ injectAdapter() }, 600)
                    }
                },
                onHistoryChanged = { view, _ -> view.evaluateJavascript(JS_BRIDGE_INIT, null) },
                onProgress = { progress = it },
                onWebView = { webViewRef = it },
                onDefaultUserAgent = { defaultUserAgent = it },
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
                android.util.Log.i("TimetableCreate", "onPick id=$id → 关闭弹窗并注入适配器")
                importTableId = id
                showTablePicker = false
                injectAdapter()
            },
            onDismiss = { showTablePicker = false },
            onCreated = { _, name ->
                scope.launch { snackbarHostState.showSnackbar("已新建《$name》，正在导入课程…") }
            },
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

/**
 * 目标课表选择弹窗：现有课表 / 新建空白课表 两个模式用 FilterChip 切换
 * （与本项目已跑通的 [com.buguake.timetable.ui.timetable.ImportChooseDialog] 同一套交互）。
 *
 * 早期实现把「新建空白课表」做成可滚动列表末尾的一行可点文本，既没有选中态也没有任何
 * 反馈，点击后与未点击在界面上完全一样——已有课表时表现为"点了没反应"。
 * 现在改为显式模式切换，并补齐：新课表设为活动课表、创建成功提示、创建失败原因回显。
 */
@Composable
private fun TablePickerDialog(
    timetables: List<TimetableInfo>,
    currentId: Long?,
    defaultStartMillis: Long,
    defaultTotalWeeks: Int,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    /** 新建课表成功后的提示出口（由宿主用 Snackbar 呈现，弹窗自身不带宿主）。 */
    onCreated: (Long, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val scheduleRepo = remember { ScheduleRepository.getInstance(context) }

    // 默认落在「新建空白课表」：从教务导入的常见意图是导入到新课表，
    // 覆盖已有课表会清空原课程，不做默认项。
    var mode by remember { mutableStateOf(PickerMode.CREATE) }
    var existingId by remember { mutableStateOf(currentId ?: timetables.firstOrNull()?.timetable?.id) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 进入「新建」模式即聚焦名称输入框并弹键盘：否则用户看不出"要先填名字"，
    // 直接点确认只会得到一句不起眼的错误提示，表现为"点了没反应"。
    LaunchedEffect(mode) {
        if (mode == PickerMode.CREATE && !busy) {
            nameFocus.requestFocus()
            keyboard?.show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("选择目标课表") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == PickerMode.CREATE,
                        onClick = { mode = PickerMode.CREATE; error = null },
                        label = { Text("新建空白课表") },
                    )
                    FilterChip(
                        selected = mode == PickerMode.EXISTING,
                        onClick = { mode = PickerMode.EXISTING; error = null },
                        label = { Text("已有课表") },
                    )
                }
                Spacer(Modifier.height(12.dp))

                when (mode) {
                    PickerMode.CREATE -> {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; error = null },
                            label = { Text("新课表名称") },
                            placeholder = { Text("如：2026 春 个人课表") },
                            isError = error != null,
                            supportingText = {
                                Text(
                                    error ?: "填好名称后点右下角「创建并导入」",
                                    color = if (error != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(nameFocus),
                        )
                    }

                    PickerMode.EXISTING -> {
                        if (timetables.isEmpty()) {
                            Text(
                                "还没有已建课表，请切到「新建空白课表」",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp)) {
                                timetables.forEach { info ->
                                    val selected = existingId == info.timetable.id
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !busy) { existingId = info.timetable.id }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { existingId = info.timetable.id },
                                            enabled = !busy,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                info.timetable.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                            Text(
                                                "${info.courseCount} 门课程",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                "导入会写入所选课表；原有课程不会被清空",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        error?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    android.util.Log.i(
                        "TimetableCreate",
                        "确认点击 mode=$mode name='${newName.trim()}' busy=$busy timetables=${timetables.size}",
                    )
                    when (mode) {
                        PickerMode.EXISTING -> existingId?.let(onPick) ?: run { error = "请选择一个课表" }

                        PickerMode.CREATE -> {
                            val name = newName.trim()
                            if (name.isEmpty()) {
                                // 不能只写一句提示就返回：焦点要落回输入框、键盘要弹出来，
                                // 否则用户看到的就是"点了一下，什么都没发生"
                                error = "请先输入新课表名称"
                                nameFocus.requestFocus()
                                keyboard?.show()
                                return@TextButton
                            }
                            busy = true
                            error = null
                            scope.launch {
                                val result = runCatching {
                                    // 与其他写库路径一致：Room 操作用 IO 派发
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        scheduleRepo.createTimetable(name, defaultStartMillis, defaultTotalWeeks)
                                    }
                                }
                                busy = false
                                result.onSuccess { id ->
                                    android.util.Log.i("TimetableCreate", "新建课表成功 id=$id name=$name")
                                    // 新建的课表设为活动课表，否则用户看不到任何变化（表现为"没反应"）
                                    runCatching { settingsRepo.setActiveTimetable(id) }
                                        .onFailure { android.util.Log.w("TimetableCreate", "设为活动课表失败", it) }
                                    AppRefresh.onDataChanged(context)
                                    onCreated(id, name)
                                    onPick(id)
                                }.onFailure { e ->
                                    android.util.Log.w("TimetableCreate", "新建课表失败", e)
                                    error = "创建失败：${e.message ?: "未知错误"}"
                                }
                            }
                        }
                    }
                },
                enabled = !busy,
            ) {
                Text(
                    when {
                        busy -> "创建中…"
                        mode == PickerMode.CREATE -> "创建并导入"
                        else -> "确定"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
    )
}

/** 目标课表选择模式。 */
private enum class PickerMode { CREATE, EXISTING }
