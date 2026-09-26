package com.buguake.timetable.campus.ui

import com.buguake.timetable.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.CampusStore
import com.buguake.timetable.campus.exam.CampusExamBundle
import com.buguake.timetable.campus.exam.ExamCalendar
import com.buguake.timetable.data.CalendarExport
import com.buguake.timetable.data.CalendarSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FMT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)

/** 考试提醒统一提前 1 天（考试需要比上课更早的余量）。 */
private const val EXAM_REMIND_MINUTES = 24 * 60

/**
 * 考试安排 · 设置页：教务入口、读取、日历写入/导出、缓存清理集中在这里。
 * 主页只负责浏览，保持干净。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusExamSettingsScreen(
    glass: Boolean = false,
    bundle: CampusExamBundle?,
    entryName: String,
    entryUrl: String,
    showSnackbar: (String) -> Unit,
    onPickSchool: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onRead: () -> Unit,
    onCleared: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { CampusStore.getInstance(context) }
    val scope = rememberCoroutineScope()
    val exams = bundle?.exams.orEmpty()

    var showUrlDialog by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf(entryUrl) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingIcs by remember { mutableStateOf<String?>(null) }
    var calendarAction by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val content = pendingIcs
        pendingIcs = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("无法写入文件")
                }
            }.onSuccess { showSnackbar("已导出 .ics：可导入系统日历或其它日历应用") }
                .onFailure { e -> showSnackbar("导出失败：${e.message ?: "未知错误"}") }
        }
    }

    fun syncToCalendar() {
        val specs = ExamCalendar.specs(exams)
        if (specs.isEmpty()) {
            showSnackbar("没有可写入日历的考试（缺考试时间）")
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    CalendarSync.sync(
                        cr = context.contentResolver,
                        specs = specs,
                        remindMinutesBefore = EXAM_REMIND_MINUTES,
                        calendarName = CalendarSync.CAL_EXAM_DISPLAY_NAME,
                    )
                }
            }.onSuccess { n ->
                showSnackbar("已把 $n 场考试写入系统日历「${CalendarSync.CAL_EXAM_DISPLAY_NAME}」")
            }.onFailure { e -> showSnackbar("同步失败：${e.message ?: "未知错误"}") }
        }
    }

    val calendarPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[android.Manifest.permission.WRITE_CALENDAR] == true &&
            grants[android.Manifest.permission.READ_CALENDAR] == true
        if (calendarAction == "sync") {
            if (granted) syncToCalendar() else showSnackbar("需要「日历」权限才能写入系统日历")
        }
        calendarAction = null
    }

    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("考试设置") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SketchArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp),
        ) {
            // ---- 教务入口 ----
            SettingsCard("教务入口") {
                Text(
                    entryName.ifBlank { "尚未选择学校" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    entryUrl.ifBlank { "尚未设置教务网址" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onPickSchool) { Text("选择学校") }
                    TextButton(onClick = {
                        urlDraft = entryUrl
                        showUrlDialog = true
                    }) { Text("手动填写网址") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "目前仅兼容正方教务系统（V9）；其它教务系统暂不支持。选择学校会自动带出该校正方入口。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 读取 ----
            SettingsCard("读取考试安排") {
                Text(
                    if (entryUrl.isBlank()) "请先选择学校或填写本校正方教务网址。"
                    else "在应用内登录教务系统后点下面的按钮即可：会自动进入「考试信息查询」页，" +
                        "再按学年/学期筛选（与导入课表一样的选择方式）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (entryUrl.isBlank()) showSnackbar("请先选择学校或填写教务网址")
                        else onRead()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (exams.isEmpty()) "读取考试安排" else "重新读取考试安排") }
            }

            // ---- 日历 ----
            SettingsCard("日历") {
                Text(
                    "写入系统日历「${CalendarSync.CAL_EXAM_DISPLAY_NAME}」，并提前 1 天提醒；" +
                        "与课程日历分开，互不影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (exams.none { it.hasTime }) {
                                showSnackbar("没有可写入日历的考试（缺考试时间）")
                            } else {
                                calendarAction = "sync"
                                calendarPerms.launch(
                                    arrayOf(
                                        android.Manifest.permission.WRITE_CALENDAR,
                                        android.Manifest.permission.READ_CALENDAR,
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("同步到日历") }
                    OutlinedButton(
                        onClick = {
                            if (exams.none { it.hasTime }) {
                                showSnackbar("没有可导出的考试（缺考试时间）")
                            } else {
                                pendingIcs = CalendarExport.buildSimpleIcs(
                                    events = ExamCalendar.simpleEvents(exams, EXAM_REMIND_MINUTES),
                                    calendarName = "考试安排",
                                )
                                icsLauncher.launch("考试安排.ics")
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("导出 .ics") }
                }
            }

            // ---- 数据 ----
            SettingsCard("数据") {
                Text(
                    "已缓存 ${exams.size} 场考试",
                    style = MaterialTheme.typography.bodySmall,
                )
                val term = bundle?.termLabel.orEmpty()
                if (term.isNotBlank()) {
                    Text(
                        "学期：$term",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                bundle?.updatedAt?.takeIf { it > 0 }?.let {
                    Text(
                        "上次读取 " + FMT_STAMP.format(
                            Instant.ofEpochMilli(it).atZone(ZONE_EXAM)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exams.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showClearConfirm = true }) { Text("清空考试缓存") }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("正方教务网址") },
            text = {
                Column {
                    Text(
                        "填写本校正方教务系统首页地址，例如 http://jwxt.example.edu.cn/",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        singleLine = true,
                        placeholder = { Text("http://...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val url = urlDraft.trim().let {
                        if (it.isNotEmpty() && !it.startsWith("http")) "http://$it" else it
                    }
                    onSaveUrl(url)
                    showUrlDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("取消") } },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空考试缓存") },
            text = { Text("只清除本机缓存的考试安排，不影响云莓登录、宿舍开门与课表。") },
            confirmButton = {
                TextButton(onClick = {
                    store.clearExams()
                    showClearConfirm = false
                    onCleared()
                    showSnackbar("已清空考试缓存")
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
