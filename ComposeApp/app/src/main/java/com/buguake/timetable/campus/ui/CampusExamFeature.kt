package com.buguake.timetable.campus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.buguake.timetable.campus.CampusStore
import com.buguake.timetable.campus.exam.CampusExamBundle
import com.buguake.timetable.webimport.SchoolData
import com.buguake.timetable.webimport.SchoolIndexStore
import com.buguake.timetable.webimport.ui.SchoolSelectionScreen
import kotlinx.coroutines.launch

/** 入口提示：本功能是校园本地化，仅覆盖正方教务。 */
private const val ZHENGFANG_HINT = "考试安排目前仅兼容正方教务系统（V9），其它教务系统暂不支持。"

/** 考试功能内部步骤（二级 = 列表，三级 = 设置 / 选学校 / 教务页面）。 */
private sealed interface ExamStep {
    data object List : ExamStep
    data object Settings : ExamStep
    data object PickSchool : ExamStep
    data object Import : ExamStep
}

/**
 * 校园「考试安排」功能壳：负责步骤编排与转场动画。
 *
 * 与课程导入一致，选学校复用同一套学校选择器（拾光索引），
 * 只是加了"仅兼容正方教务"的提示，并自动带出该校的正方入口地址。
 */
@Composable
fun CampusExamFeature(
    glass: Boolean = false,
    showSnackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CampusStore.getInstance(context) }
    val indexStore = remember { SchoolIndexStore.getInstance(context) }

    var step by remember { mutableStateOf<ExamStep>(ExamStep.List) }
    var bundle by remember { mutableStateOf(store.loadExams()) }
    var index by remember { mutableStateOf(indexStore.loadCached()) }
    var loading by remember { mutableStateOf(index == null) }
    var indexError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshIndex(force: Boolean) {
        loading = true
        indexError = null
        indexStore.refreshOrCached(force)
            .onSuccess { index = it }
            .onFailure { e -> indexError = e.message }
        loading = false
    }

    // 只有停在列表页时，返回手势才交还上层；设置/选校/教务页各自处理
    BackHandler(enabled = step is ExamStep.List) { onBack() }

    CampusStepHost(
        step = step,
        depth = {
            when (it) {
                ExamStep.List -> 0
                ExamStep.Settings -> 1
                ExamStep.PickSchool -> 2
                ExamStep.Import -> 2
            }
        },
        onBack = {
            if (step != ExamStep.List) step = ExamStep.List
        },
    ) { current ->
        when (current) {
            ExamStep.List -> CampusExamScreen(
                glass = glass,
                bundle = bundle,
                onOpenSettings = { step = ExamStep.Settings },
                onBack = onBack,
            )

            ExamStep.Settings -> CampusExamSettingsScreen(
                glass = glass,
                bundle = bundle,
                entryName = store.examEntryName,
                entryUrl = store.examEntryUrl,
                showSnackbar = showSnackbar,
                onPickSchool = { step = ExamStep.PickSchool },
                onSaveUrl = { store.examEntryUrl = it },
                onRead = { step = ExamStep.Import },
                onCleared = { bundle = null },
                onBack = { step = ExamStep.List },
            )

            ExamStep.PickSchool -> SchoolSelectionScreen(
                index = index,
                loading = loading,
                error = indexError,
                onSelectSchool = { school ->
                    val url = zhengfangUrl(school)
                    store.examEntryName = school.name
                    if (url.isNotBlank()) {
                        store.examEntryUrl = url
                    } else {
                        showSnackbar("该校索引里没有正方教务入口，请在下一页的地址栏手动输入")
                    }
                    step = ExamStep.Import
                },
                onRefresh = { scope.launch { refreshIndex(force = true) } },
                onBack = { step = ExamStep.Settings },
                glass = glass,
                banner = ZHENGFANG_HINT,
            )

            ExamStep.Import -> CampusExamImportScreen(
                glass = glass,
                title = store.examEntryName.ifBlank { "教务系统" },
                startUrl = store.examQueryUrl.ifBlank { store.examEntryUrl },
                showSnackbar = showSnackbar,
                onSaved = { saved, pageUrl ->
                    bundle = saved
                    if (pageUrl.isNotBlank()) store.examQueryUrl = pageUrl
                    step = ExamStep.Settings
                },
                onBack = { step = ExamStep.Settings },
            )
        }
    }

    // 首次进入后台刷新索引（缓存优先展示，失败静默保留缓存）
    LaunchedEffect(Unit) {
        if (loading) refreshIndex(force = false)
    }
}

/** 取该校的正方教务入口：优先名字/ID 命中正方的适配器；没有则返回空串（由用户手填）。 */
private fun zhengfangUrl(school: SchoolData): String =
    school.adapters.firstOrNull {
        it.importUrl.isNotBlank() &&
            (it.name.contains("正方") || it.adapterId.contains("zf", ignoreCase = true))
    }?.importUrl.orEmpty()
