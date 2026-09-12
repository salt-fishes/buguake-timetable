package com.buguake.timetable.campus.ui

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buguake.timetable.BuildConfig
import com.buguake.timetable.campus.CampusStore
import com.buguake.timetable.campus.exam.CampusExamBundle
import com.buguake.timetable.campus.exam.JwxtExamParser
import com.buguake.timetable.webimport.ui.JwxtWebView
import org.json.JSONObject

/** 最多自动跳转几次（防止定位失败时来回打转）。 */
private const val MAX_NAV_HOPS = 2

/** 单次读取的兜底超时（秒）：宁可报错，也不能一直卡在「处理中」。 */
private const val READ_TIMEOUT_SECONDS = 20L

/** 相对地址兜底：脚本已回传绝对地址，这里再保一层（WebView.loadUrl 不接受相对路径）。 */
private fun resolveUrl(base: String?, target: String): String {
    if (target.isBlank()) return ""
    if (target.startsWith("http://") || target.startsWith("https://")) return target
    val origin = base?.let { runCatching { java.net.URI(it) }.getOrNull() }
        ?.let { "${it.scheme}://${it.authority}" }
        ?: return target
    return origin + target
}

/**
 * 教务系统登录 + 读取考试屏。
 *
 * WebView 外壳与课程导入**共用** [JwxtWebView]（同一套 UA/桌面模式/viewport/清除登录）；
 * 差别只在桥名、注入脚本与保存动作。
 *
 * 操作与课程导入对齐：登录后点一下按钮即可——脚本会自动定位并跳到
 * 「考试信息查询」页，再弹学期选择（学年/学期），确认后读回并落盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusExamImportScreen(
    glass: Boolean = false,
    title: String,
    startUrl: String,
    showSnackbar: (String) -> Unit,
    onSaved: (CampusExamBundle, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { CampusStore.getInstance(context) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("请先登录教务系统，然后点下方按钮读取") }
    var urlInput by remember { mutableStateOf(startUrl) }
    var terms by remember { mutableStateOf<JwxtExamParser.TermOptions?>(null) }
    var pickedYear by remember { mutableStateOf("") }
    var pickedSemester by remember { mutableStateOf("") }
    var pickedLabel by remember { mutableStateOf("") }
    // 自动跳转考试页：跳过去后由 onPageFinished 续跑 start()
    var autoContinue by remember { mutableStateOf(false) }
    var navHops by remember { mutableStateOf(0) }
    // 每轮读取的序号：超时兜底只清理同一轮，不会误清后续操作
    var attempt by remember { mutableStateOf(0) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val script = remember {
        runCatching {
            context.assets.open("jwxt_exam.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    fun setStatus(text: String) {
        main.post {
            status = text
            busy = false
        }
    }

    /** 每次页面加载后重挂脚本（幂等）。 */
    fun injectScript(wv: WebView) {
        if (script.isNotBlank()) wv.evaluateJavascript(script, null)
    }

    /** 注入脚本并触发 read()；脚本没挂上（页面刚换过上下文）时立刻报错并重挂。 */
    fun callRead(wv: WebView, xnm: String, xqm: String) {
        injectScript(wv)
        wv.evaluateJavascript(
            "(function(){ if (!window.__campusExam) return 'missing';" +
                " window.__campusExam.read('$xnm','$xqm'); return 'ok'; })()"
        ) { result ->
            if (result != null && result.contains("missing")) {
                setStatus("脚本注入失败，请返回后重试")
            }
        }
    }

    /** 超时兜底：本轮若一直没有回传，就把状态从「处理中」解开并给出可操作的提示。 */
    fun armWatchdog(token: Int) {
        main.postDelayed({
            if (attempt == token && busy) {
                busy = false
                status = "读取超时：请确认已登录教务系统与网络可用，然后重试"
            }
        }, READ_TIMEOUT_SECONDS * 1000)
    }

    /** 注入脚本并触发 start()；脚本没挂上时立刻报错，而不是一直等。 */
    fun callStart(wv: WebView) {
        injectScript(wv)
        wv.evaluateJavascript(
            "(function(){ if (!window.__campusExam) return 'missing';" +
                " window.__campusExam.start(); return 'ok'; })()"
        ) { result ->
            if (result != null && result.contains("missing")) {
                setStatus("脚本注入失败，请返回后重试")
            }
        }
    }

    /**
     * 桥消息处理：解析回传并按 kind 分派。
     * 注意：本方法运行在 JS 桥线程，所有 UI/WebView 操作都必须再 post 回主线程
     * （WebView 的方法在非创建线程调用会抛异常，曾导致消息整条丢失、界面一直卡住）。
     */
    fun handleBridgeMessage(json: String?) {
        val payload = json ?: return
        val o = runCatching { JSONObject(payload) }.getOrNull()
        if (o == null) {
            setStatus("回传数据无法解析")
            return
        }
        if (!o.optBoolean("ok")) {
            setStatus(o.optString("msg").ifBlank { "读取失败，请重试" })
            return
        }
        when (o.optString("kind")) {
                    // 自动跳到考试查询页（原生负责导航，页面加载完成后续跑）
                    "navigating" -> {
                        val raw = o.optString("url")
                        // WebView 方法必须在 UI 线程调用（这里是 JS 桥线程）
                        main.post {
                            val url = resolveUrl(webViewRef?.url, raw)
                            if (navHops >= MAX_NAV_HOPS || url.isBlank()) {
                                busy = false
                                status = "没能自动打开「考试信息查询」，请在页面里手动进入该页后重试"
                            } else {
                                navHops++
                                autoContinue = true
                                attempt++
                                armWatchdog(attempt)
                                status = "正在打开「考试信息查询」…"
                                webViewRef?.loadUrl(url)
                            }
                        }
                    }

                    "terms" -> {
                        val parsed = JwxtExamParser.parseTerms(payload).getOrNull()
                        if (parsed == null) {
                            setStatus("未能读取页面上的学期选项")
                            return
                        }
                        main.post {
                            busy = false
                            if (parsed.years.isEmpty() && parsed.semesters.isEmpty()) {
                                pickedLabel = ""
                                status = "正在读取考试安排…"
                                busy = true
                                webViewRef?.let { callRead(it, "", "") }
                            } else {
                                terms = parsed
                                pickedYear = parsed.currentYear.ifBlank {
                                    parsed.years.firstOrNull()?.code.orEmpty()
                                }
                                pickedSemester = parsed.currentSemester.ifBlank {
                                    parsed.semesters.firstOrNull()?.code.orEmpty()
                                }
                                status = "请选择要读取的学年与学期"
                            }
                        }
                    }

                    "exams" -> {
                        JwxtExamParser.parse(o.optString("data"))
                            .onSuccess { (exams, report) ->
                                val bundle = CampusExamBundle(
                                    updatedAt = System.currentTimeMillis(),
                                    exams = exams,
                                    termLabel = pickedLabel,
                                )
                                store.saveExams(bundle)
                                // 注意：webViewRef.url 必须在 UI 线程读取（桥线程调用会抛异常）
                                main.post {
                                    val pageUrl = webViewRef?.url.orEmpty()
                                    busy = false
                                    status = "已读取 ${report.parsed} 场考试"
                                    onSaved(bundle, pageUrl)
                                    showSnackbar(
                                        buildString {
                                            append("已读取 ${report.parsed} 场考试")
                                            if (report.withoutTime > 0) {
                                                append("（${report.withoutTime} 场时间待定）")
                                            }
                                        }
                                    )
                                }
                            }
                            .onFailure { e -> setStatus(e.message ?: "解析失败") }
                    }

            else -> setStatus("未知回传类型")
        }
    }

    val bridge = remember {
        object {
            @JavascriptInterface
            fun post(json: String?) {
                // 兜底：桥线程里任何异常都不能让整条消息静默丢失（否则界面会一直卡在"处理中"）
                try {
                    handleBridgeMessage(json)
                } catch (t: Throwable) {
                    android.util.Log.w("CampusExam", "桥消息处理失败", t)
                    setStatus("读取出错：${t.message ?: t.javaClass.simpleName}")
                }
            }
        }
    }

    BackHandler(enabled = true) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    fun goTo(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
        webViewRef?.loadUrl(url)
    }

    fun startRead() {
        val wv = webViewRef ?: return
        busy = true
        navHops = 0
        status = "正在检查当前页面…"
        attempt++
        armWatchdog(attempt)
        callStart(wv)
    }

    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                webViewRef?.reload()
                                status = "已清除登录会话"
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("清除登录") }
                        Button(
                            onClick = { startRead() },
                            enabled = !busy,
                            modifier = Modifier.weight(1.6f),
                        ) { Text(if (busy) "处理中…" else "读取考试安排") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("教务系统网址", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { goTo(urlInput) }),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { goTo(urlInput) }) { Text("前往") }
            }
            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            JwxtWebView(
                bridgeName = "CampusExamBridge",
                bridge = bridge,
                startUrl = startUrl.takeIf { it.isNotBlank() },
                desktopMode = true,
                onPageStarted = { _, url ->
                    if (BuildConfig.DEBUG) android.util.Log.i("CampusExam", "页面加载: $url")
                    urlInput = url
                },
                onPageFinished = { view, _ ->
                    injectScript(view)
                    if (autoContinue) {
                        autoContinue = false
                        view.postDelayed({ callStart(view) }, 600)
                    }
                },
                onHistoryChanged = { view, _ -> injectScript(view) },
                onProgress = { progress = it },
                onWebView = { webViewRef = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // ---- 学期选择（学期在前 + 最近学年，交互与「选择课表」一致）----
    terms?.let { t ->
        // 学年下拉有 30+ 项（2001–2031）：只留最近 8 年并保证含当前选中项，
        // 否则「学期」会被挤到滚动区底部、看起来像根本没有学期可选。
        val yearChoices = remember(t) {
            val recent = t.years
                .filter { it.code.toIntOrNull() != null }
                .sortedByDescending { it.code.toIntOrNull() ?: 0 }
                .take(8)
            val current = t.years.filter { it.code == t.currentYear }
            (current + recent).distinctBy { it.code }
        }
        val semesterChoices = remember(t) {
            buildList {
                add("" to "全部学期")
                t.semesters.forEach { add(it.code to it.label.ifBlank { it.code }) }
            }
        }
        val preview = listOf(
            yearChoices.firstOrNull { it.code == pickedYear }?.label.orEmpty(),
            semesterChoices.firstOrNull { it.first == pickedSemester }?.second.orEmpty(),
        ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "全部" }

        AlertDialog(
            onDismissRequest = { terms = null },
            title = { Text("选择学期") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 104.dp),
                ) {
                    Text(
                        "学期",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    semesterChoices.forEach { (code, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { pickedSemester = code }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pickedSemester == code, onClick = { pickedSemester = code })
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (yearChoices.isNotEmpty()) {
                        Text(
                            "学年",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        yearChoices.forEach { y ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pickedYear = y.code }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = pickedYear == y.code, onClick = { pickedYear = y.code })
                                Text(y.label.ifBlank { y.code }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "将读取：$preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val yearLabel = yearChoices.firstOrNull { it.code == pickedYear }?.label.orEmpty()
                    val semLabel = semesterChoices.firstOrNull { it.first == pickedSemester }?.second.orEmpty()
                    pickedLabel = listOf(yearLabel, semLabel)
                        .filter { it.isNotBlank() && it != "全部学期" }
                        .joinToString(" ")
                    terms = null
                    busy = true
                    attempt++
                    armWatchdog(attempt)
                    status = "正在读取考试安排…"
                    webViewRef?.let { callRead(it, pickedYear, pickedSemester) }
                }) { Text("读取") }
            },
            dismissButton = { TextButton(onClick = { terms = null }) { Text("取消") } },
        )
    }
}
