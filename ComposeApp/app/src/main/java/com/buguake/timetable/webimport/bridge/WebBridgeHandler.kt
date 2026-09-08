package com.buguake.timetable.webimport.bridge

import android.content.Context
import com.buguake.timetable.data.ScheduleRepository
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.SettingsRepository
import com.buguake.timetable.data.TimetableEntity
import com.buguake.timetable.reminder.AppRefresh
import com.buguake.timetable.webimport.WebImportConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 弹窗宿主：Handler 把 JS 弹窗请求转交 UI 层渲染，结果经回调回给 Handler。 */
interface WebDialogHost {
    fun showAlert(title: String, content: String, confirmText: String, onResult: (Boolean) -> Unit)

    /**
     * Prompt 输入弹窗。用户点确认时宿主回调 [onSubmit]：
     * Handler 完成校验后，成功调 [onSuccess]（宿主关闭弹窗），失败调 [onValidationError]
     * （宿主把错误信息显示在输入框下方，弹窗保持打开）；用户取消走 [onCancel]。
     */
    fun showPrompt(
        title: String,
        tip: String,
        defaultText: String,
        validatorJsFunction: String?,
        onCancel: () -> Unit,
        onSubmit: (input: String, onValidationError: (String) -> Unit, onSuccess: () -> Unit) -> Unit,
    )

    fun showSingleSelection(
        title: String,
        items: List<String>,
        defaultSelectedIndex: Int,
        onResult: (selectedIndex: Int?) -> Unit,
    )
}

/**
 * JS 桥消息处理器：路由 shiguang_warehouse 适配脚本的 8 个动作。
 * 行为对齐拾光课程表 WebBridgeHandler.kt（Apache 2.0，本项目重写实现）。
 *
 * 与拾光的差异：saveImportedCourses/saveCourseConfig/savePresetTimeSlots
 * 写入本项目的 Room 仓库（目标课表为注入脚本前用户选定的表）。
 */
class WebBridgeHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dialogHost: WebDialogHost,
    private val showToast: (String) -> Unit,
    private val evaluateJs: (script: String, callback: ((String?) -> Unit)?) -> Unit,
    private val settingsRepo: SettingsRepository,
    private val scheduleRepo: ScheduleRepository,
    private val onTaskCompleted: () -> Unit,
) {
    /** 当前导入目标课表 id（注入脚本前由用户选定；null = 未选择，保存动作报错）。 */
    @Volatile var importTableId: Long? = null

    /** 缓存最近一次收到的课程数据（notifyTaskCompletion 时供界面汇总提示）。 */
    @Volatile var lastImportedCourseCount: Int = 0
        private set

    private val settings: ScheduleSettings? get() = settingsRepo.current

    fun onMessageReceived(jsonString: String) {
        val message = JsBridgeMessage.parse(jsonString) ?: run {
            android.util.Log.w(TAG, "桥消息解析失败: ${jsonString.take(200)}")
            return
        }
        android.util.Log.i(TAG, "桥动作: ${message.action} callbackId=${message.callbackId}")
        val callbackId = message.callbackId
        when (message.action) {
            "showToast" -> parseShowToast(message.payload)?.let { showToast(it.message) }
            "showAlert" -> parseShowAlert(message.payload)?.let {
                dialogHost.showAlert(
                    it.titleText, it.contentText, it.confirmText ?: "确定",
                ) { confirmed ->
                    if (callbackId != null) {
                        resolveJsPromise(callbackId, if (confirmed) "true" else "false")
                    }
                }
            }
            "showPrompt" -> parseShowPrompt(message.payload)?.let {
                showPrompt(it, callbackId)
            }
            "showSingleSelection" -> parseShowSingleSelection(message.payload)?.let { p ->
                val items = parseSelectionItems(p.itemsJsonString)
                if (items.isEmpty()) {
                    showToast("单选列表数据错误，无法显示。")
                    if (callbackId != null) rejectJsPromise(callbackId, "选项列表 JSON 无效")
                    return@let
                }
                dialogHost.showSingleSelection(p.titleText, items, p.defaultSelectedIndex) { index ->
                    if (callbackId != null) {
                        resolveJsPromise(callbackId, index?.toString() ?: "null")
                    }
                }
            }
            "saveImportedCourses" -> parseSaveCourses(message.payload)?.let {
                saveImportedCourses(it.coursesJsonString, callbackId)
            }
            "saveCourseConfig" -> parseSaveConfig(message.payload)?.let {
                saveCourseConfig(it.configJsonString, callbackId)
            }
            "savePresetTimeSlots" -> parseSaveTimeSlots(message.payload)?.let {
                savePresetTimeSlots(it.timeSlotsJsonString, callbackId)
            }
            "notifyTaskCompletion" -> {
                importTableId = null
                onTaskCompleted()
            }
        }
    }

    /** Prompt 弹窗：validatorJsFunction 非空时在页面内执行校验，失败信息回显输入框。 */
    private fun showPrompt(p: ShowPromptPayload, callbackId: String?) {
        dialogHost.showPrompt(
            p.titleText, p.tipText, p.defaultText, p.validatorJsFunction,
            onCancel = { if (callbackId != null) resolveJsPromise(callbackId, "null") },
            onSubmit = { input, onValidationError, onSuccess ->
                val encodedInput = JSONObject.quote(input)
                val validator = p.validatorJsFunction
                if (validator.isNullOrEmpty()) {
                    if (callbackId != null) resolveJsPromise(callbackId, encodedInput)
                    onSuccess()
                } else {
                    // 在页面上下文执行 JS 校验函数：返回空/"false" = 通过，否则为错误提示文案
                    evaluateJs("$validator($encodedInput)") { result ->
                        val error = result?.trim('"')
                        if (error.isNullOrEmpty() || error.equals("false", ignoreCase = true)) {
                            if (callbackId != null) resolveJsPromise(callbackId, encodedInput)
                            scope.launch(Dispatchers.Main) { onSuccess() }
                        } else {
                            scope.launch(Dispatchers.Main) { onValidationError(error) }
                        }
                    }
                }
            },
        )
    }

    // ---- 三个数据动作 ----

    private fun saveImportedCourses(coursesJsonString: String, callbackId: String?) {
        scope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                withContext(Dispatchers.Main) {
                    showToast("导入失败：未选择课表。")
                    if (callbackId != null) rejectJsPromise(callbackId, "课表选择已取消。")
                }
                return@launch
            }
            // 自定义时间段课次按目标课表作息映射节次
            val tt = scheduleRepo.getTimetable(tableId)
            val sectionTimes = SettingsRepository.sectionTimesFor(
                tt?.sectionTimesCsv, tt?.sectionsPerDay ?: 0, settingsRepo.current.sectionTimes,
            )
            val result = WebImportConverter.convertCourses(coursesJsonString, sectionTimes)
                .mapCatching { (parsed, report) ->
                    scheduleRepo.importSchedule(parsed, tableId)
                    report
                }
            val logDetail = result.getOrNull()
                ?.let { "imported=${it.imported} skipped=${it.skipped}" }
                ?: (result.exceptionOrNull()?.message ?: "unknown")
            android.util.Log.i(TAG, "saveImportedCourses: success=${result.isSuccess} $logDetail")
            withContext(Dispatchers.Main) {
                result.onSuccess { report ->
                    lastImportedCourseCount = report.imported
                    val skippedNote = if (report.skipped > 0) "，跳过 ${report.skipped} 条无效数据" else ""
                    showToast("课程导入成功：${report.imported} 门课程$skippedNote")
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    showToast("课程导入失败：${e.message}")
                    if (callbackId != null) rejectJsPromise(callbackId, "课程导入失败：${e.message}")
                }
            }
        }
    }

    private fun saveCourseConfig(configJsonString: String, callbackId: String?) {
        scope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                withContext(Dispatchers.Main) {
                    showToast("配置导入失败：未选择目标课表。")
                    if (callbackId != null) rejectJsPromise(callbackId, "课表选择已取消或未设置。")
                }
                return@launch
            }
            val config = WebImportConverter.parseCourseConfig(configJsonString).getOrNull()
            if (config == null) {
                withContext(Dispatchers.Main) {
                    showToast("课表配置解析失败。")
                    if (callbackId != null) rejectJsPromise(callbackId, "配置 JSON 无效")
                }
                return@launch
            }
            val result = runCatching {
                val tt = scheduleRepo.getTimetable(tableId)
                    ?: throw IllegalStateException("目标课表不存在")
                scheduleRepo.updateTimetable(
                    tt.copy(
                        startMillis = config.semesterStartDate
                            ?.let { WebImportConverter.semesterStartMillis(it) }
                            ?: tt.startMillis,
                        totalWeeks = config.semesterTotalWeeks?.coerceIn(8, 30) ?: tt.totalWeeks,
                    )
                )
            }
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    AppRefresh.onDataChanged(context)
                    showToast("课表配置导入成功！")
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    showToast("课表配置导入失败：${e.message}")
                    if (callbackId != null) rejectJsPromise(callbackId, "配置导入失败：${e.message}")
                }
            }
        }
    }

    private fun savePresetTimeSlots(timeSlotsJsonString: String, callbackId: String?) {
        scope.launch(Dispatchers.Default) {
            val tableId = importTableId
            if (tableId == null) {
                withContext(Dispatchers.Main) {
                    showToast("导入失败：未选择课表。")
                    if (callbackId != null) rejectJsPromise(callbackId, "课表选择已取消。")
                }
                return@launch
            }
            val result = WebImportConverter.parseTimeSlots(timeSlotsJsonString).mapCatching { slots ->
                val tt = scheduleRepo.getTimetable(tableId)
                    ?: throw IllegalStateException("目标课表不存在")
                // 作息下沉到该课表；节数取最大节次号
                val csv = SettingsRepository.encodeSections(slots)
                scheduleRepo.updateTimetable(
                    tt.copy(sectionTimesCsv = csv, sectionsPerDay = slots.maxOf { it.section })
                )
                slots.size
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { n ->
                    AppRefresh.onDataChanged(context)
                    showToast("节次时间表导入成功（$n 节）！")
                    if (callbackId != null) resolveJsPromise(callbackId, "true")
                }.onFailure { e ->
                    showToast("节次时间表导入失败：${e.message}")
                    if (callbackId != null) rejectJsPromise(callbackId, "节次导入失败：${e.message}")
                }
            }
        }
    }

    // ---- Promise 回程 ----

    private fun resolveJsPromise(callbackId: String, resultRawJs: String) {
        scope.launch(Dispatchers.Main) {
            evaluateJs(buildJsCallbackScript(callbackId, isSuccess = true, resultRawJs = resultRawJs), null)
        }
    }

    private fun rejectJsPromise(callbackId: String, errorText: String) {
        android.util.Log.w(TAG, "Promise reject[$callbackId]: $errorText")
        val safeErrorJson = JSONObject.quote(errorText)
        scope.launch(Dispatchers.Main) {
            evaluateJs(buildJsCallbackScript(callbackId, isSuccess = false, resultRawJs = safeErrorJson), null)
        }
    }

    companion object {
        private const val TAG = "WebImport"
    }
}
