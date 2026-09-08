package com.buguake.timetable.webimport.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.buguake.timetable.ui.timetable.TimetableInfo
import com.buguake.timetable.webimport.AdapterData
import com.buguake.timetable.webimport.SchoolData
import com.buguake.timetable.webimport.SchoolIndexStore
import kotlinx.coroutines.launch

/** 导入流程步骤：学校 → 适配器 → WebView（脚本取到后进入）。 */
private sealed interface Step {
    data object School : Step
    data class Adapter(val school: SchoolData) : Step
    data class Browser(val school: SchoolData, val adapter: AdapterData, val jsContent: String) : Step
}

/**
 * 教务网页导入全流程（学校选择 → 适配器 → 教务网页导入），由 AppShell 以覆盖页托管。
 */
@Composable
fun WebImportFlow(
    timetables: List<TimetableInfo>,
    defaultStartMillis: Long,
    defaultTotalWeeks: Int,
    glass: Boolean = false,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SchoolIndexStore.getInstance(context) }
    val snackbar = remember { SnackbarHostState() }

    var step by remember { mutableStateOf<Step>(Step.School) }
    var index by remember { mutableStateOf(store.loadCached()) }
    var loading by remember { mutableStateOf(index == null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refreshIndex(force: Boolean) {
        loading = true
        error = null
        store.refreshOrCached(force)
            .onSuccess { index = it }
            .onFailure { e -> error = e.message }
        loading = false
    }

    when (val s = step) {
        Step.School -> SchoolSelectionScreen(
            index = index,
            loading = loading,
            error = error,
            snackbarHostState = snackbar,
            onSelectSchool = { step = Step.Adapter(it) },
            onRefresh = { scope.launch { refreshIndex(force = true) } },
            onBack = onClose,
            glass = glass,
        )

        is Step.Adapter -> AdapterSelectionScreen(
            school = s.school,
            snackbarHostState = snackbar,
            onSelectAdapter = { adapter ->
                scope.launch {
                    loading = true
                    store.adapterJs(s.school, adapter)
                        .onSuccess { js ->
                            loading = false
                            step = Step.Browser(s.school, adapter, js)
                        }
                        .onFailure { e ->
                            loading = false
                            snackbar.showSnackbar("适配器脚本获取失败：${e.message}")
                        }
                }
            },
            onBack = { step = Step.School },
            glass = glass,
        )

        is Step.Browser -> ImportWebViewScreen(
            school = s.school,
            adapter = s.adapter,
            jsContent = s.jsContent,
            timetables = timetables,
            defaultStartMillis = defaultStartMillis,
            defaultTotalWeeks = defaultTotalWeeks,
            glass = glass,
            onFinished = onClose,
            onBack = { step = Step.Adapter(s.school) },
        )
    }

    // 首次进入后台刷新索引（缓存优先展示，失败静默保留缓存）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (loading) refreshIndex(force = false)
    }
}
