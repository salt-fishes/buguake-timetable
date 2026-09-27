package com.buguake.timetable.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.buguake.timetable.ui.theme.CampusIcon
import com.buguake.timetable.ui.theme.MineIcon
import com.buguake.timetable.ui.theme.TimetableIcon
import com.buguake.timetable.ui.theme.TodayIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.buguake.timetable.ui.theme.AppMotion
import com.buguake.timetable.ui.theme.Haptics
import com.buguake.timetable.ui.theme.CampusIcon
import com.buguake.timetable.data.CalendarSync
import com.buguake.timetable.data.EntryWithCourse
import com.buguake.timetable.data.ScheduleRepository
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.SettingsRepository
import com.buguake.timetable.data.TimetableEntity
import com.buguake.timetable.reminder.AppRefresh
import com.buguake.timetable.schedule.ParsedSchedule
import com.buguake.timetable.webimport.ui.WebImportFlow
import com.buguake.timetable.ui.mine.MineScreen
import com.buguake.timetable.ui.timetable.NewTimetableDialog
import com.buguake.timetable.ui.timetable.ImportChooseDialog
import com.buguake.timetable.ui.timetable.ImportTarget
import com.buguake.timetable.ui.timetable.TimetableInfo
import com.buguake.timetable.ui.timetable.TimetableManagePage
import com.buguake.timetable.ui.timetable.TimetableScreen
import com.buguake.timetable.ui.timetable.WidgetBindPage
import com.buguake.timetable.ui.timetable.CourseDetailSheet
import com.buguake.timetable.ui.timetable.TimetableScreen
import com.buguake.timetable.ui.today.TodayScreen
import com.buguake.timetable.ui.compare.CompareRepository
import com.buguake.timetable.ui.compare.CompareScreen
import com.buguake.timetable.ui.compare.CompareTimetable
import com.buguake.timetable.ui.compare.OccupancyDetection
import com.buguake.timetable.ui.compare.OccupancyReviewScreen
import com.buguake.timetable.schedule.OccupancyParser
import com.buguake.timetable.widget.ScheduleWidgetCompactProvider
import com.buguake.timetable.widget.ScheduleWidgetMediumProvider
import com.buguake.timetable.widget.ScheduleWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val TAB_LABELS = listOf("课表", "今日", "校园", "我的")

/** 待确认的调课请求：范围（以后每周/仅本周）由用户在弹窗中选择。 */
private data class MoveReq(
    val entry: com.buguake.timetable.data.EntryWithCourse,
    val day: Int,
    val start: Int,
    val end: Int,
    val week: Int,
)

/** 应用外壳：底部导航三页 + 全局状态。 */
    @OptIn(
        kotlinx.coroutines.ExperimentalCoroutinesApi::class,
        )
    @Composable
    fun AppRoot() {
    // 系统「动画时长缩放」→ AppMotion：用户在开发者选项/无障碍里关掉动画时，
    // 全应用自定义动效瞬时完成（含底栏胶囊、页签转场、二级页缩放）
    com.buguake.timetable.ui.theme.RememberSystemMotion()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val scheduleRepo = remember { ScheduleRepository.getInstance(context) }

    val settings by settingsRepo.settings.collectAsState(initial = defaultSettings())
    // 多课表：条目/课程按活动课表作用域，切换活动课表自动换数据源
    val entries by settingsRepo.activeTimetableIdFlow
        .flatMapLatest { scheduleRepo.observeAllEntries(it) }
        .collectAsState(initial = emptyList())
    val courses by settingsRepo.activeTimetableIdFlow
        .flatMapLatest { scheduleRepo.observeCourses(it) }
        .collectAsState(initial = emptyList())
    val timetables by scheduleRepo.observeTimetables().collectAsState(initial = emptyList())
    val courseCounts by scheduleRepo.observeCourseCounts().collectAsState(initial = emptyList())
    val timetableInfos = remember(timetables, courseCounts) {
        timetables.map { t ->
            TimetableInfo(t, courseCounts.firstOrNull { it.timetableId == t.id }?.courseCount ?: 0)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 「长按应用图标 → 快速开锁」：切到校园页，由 CampusUnlockScreen 用默认门锁直接开门
    val quickUnlockSeq by com.buguake.timetable.campus.QuickUnlock.seq.collectAsState()
    // 直达开门只消费一次：开门页处理完回传序号，之后（切页返回、重组）放行值归 0，不再重复开门；
    // saveable 保证旋转/重建界面后也不会拿旧序号再开一次
    var consumedUnlockSeq by rememberSaveable { mutableIntStateOf(0) }
    val pendingUnlockSeq = if (quickUnlockSeq > consumedUnlockSeq) quickUnlockSeq else 0
    // 「洗衣房小组件 / 快捷方式」直达：切到校园页并打开洗衣房（楼栋由缓存自动带出）
    val laundrySeq by com.buguake.timetable.campus.LaundryLaunch.seq.collectAsState()
    var consumedLaundrySeq by rememberSaveable { mutableIntStateOf(0) }
    val pendingLaundrySeq = if (laundrySeq > consumedLaundrySeq) laundrySeq else 0
    var selectedEntry by remember { mutableStateOf<EntryWithCourse?>(null) }
    var editingEntry by remember { mutableStateOf<EntryWithCourse?>(null) }
    var showAddCourse by rememberSaveable { mutableStateOf(false) }
    // 长按网格空白添加：预填落点（星期 to 起始节），null = 顶栏加号进入
    var addCoursePrefill by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showSectionTimes by rememberSaveable { mutableStateOf(false) }
    var showReminders by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showTimetableManage by rememberSaveable { mutableStateOf(false) }
    var showWidgetBind by rememberSaveable { mutableStateOf(false) }
    var widgetBindRefresh by remember { mutableIntStateOf(0) }
    var showCompare by rememberSaveable { mutableStateOf(false) }
    var showMoveCourse by rememberSaveable { mutableStateOf(false) }
    var showWebImport by rememberSaveable { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<MoveReq?>(null) }
    var compareTimetables by remember { mutableStateOf<List<CompareTimetable>>(emptyList()) }
    var pendingOccupancy by remember { mutableStateOf<OccupancyDetection?>(null) }
    // 网页导入预留：解析结果确认弹窗与入库流程（Phase 5/6 接线）
    var pendingImport by remember { mutableStateOf<ParsedSchedule?>(null) }

    // 快捷方式直达开门：切到校园页，并收起压在上面的二级页——
    // 否则会出现"门已经开了，屏幕还停在关于页"的错位观感
    LaunchedEffect(quickUnlockSeq) {
        if (quickUnlockSeq <= 0) return@LaunchedEffect
        tab = 2
        showAddCourse = false
        showSectionTimes = false
        showReminders = false
        showAbout = false
        showPrivacy = false
        showTimetableManage = false
        showWidgetBind = false
        showCompare = false
        showWebImport = false
        showMoveCourse = false
        selectedEntry = null
        editingEntry = null
    }

    // 洗衣房小组件 / 快捷方式直达：同样切到校园页并收起二级页
    LaunchedEffect(laundrySeq) {
        if (laundrySeq <= 0) return@LaunchedEffect
        tab = 2
        showAbout = false
        showPrivacy = false
        showCompare = false
        showWebImport = false
        showMoveCourse = false
    }

    // 全应用提示统一走系统 Toast（不再用 Snackbar）：二级覆盖页、WebView 之上都能看到，
    // 也不会被页面内容或滚动区遮挡。下游沿用 showSnackbar 这个名字，换的只是出口
    val showSnackbar: (String) -> Unit = { msg -> SystemPrompt.toast(context, msg) }

    // ---- 设置动作 ----
    val setSemesterStart: (java.time.LocalDate) -> Unit = { date ->
        scope.launch {
            settingsRepo.setSemesterStart(date)  // 写库完成后再刷新，避免小组件读到旧日期
            AppRefresh.onDataChanged(context)
            Haptics.tick(context)  // 学期设置轻震
        }
    }

    // ---- 背景图导入（系统照片选择器 Photo Picker：零权限，旧版本自动回退 SAF）
    //      选图后走系统原生裁剪（按屏幕比例），无系统裁剪组件时回退应用内裁剪 ----
    var pendingCropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 系统裁剪拿不到结果时的兜底源图（就是选图时那一张，已降采样到 ≤2048）
    var cropFallbackSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // 裁剪结果（系统裁剪输出文件）→ 落盘为背景并刷新
    suspend fun saveBackground(cropped: android.graphics.Bitmap) {
        val oldPath = settings.customBgPath
        runCatching {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                val dst = File(context.filesDir, "bg_custom.jpg")
                // 换图时清理旧背景文件（路径不同才删，避免误删刚写入的新图）
                if (oldPath.isNotBlank() &&
                    File(oldPath).absolutePath != dst.absolutePath
                ) File(oldPath).delete()
                dst.outputStream().use {
                    cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it)
                }
                dst.absolutePath
            }
        }.onSuccess { path ->
            settingsRepo.setCustomBgPath(path)
            settingsRepo.setCustomBgEnabled(true)
            AppRefresh.onDataChanged(context)  // 落盘后立即刷新（界面/小组件）
            Haptics.click(context)
            showSnackbar("背景已更新")
        }.onFailure { e ->
            showSnackbar("保存背景失败：${e.message ?: "未知错误"}")
        }
    }

    // 系统原生裁剪（com.android.camera.action.CROP）：输入/输出都走应用自己的
    // FileProvider 缓存文件（file_paths 已含 cache/share/），输出按屏幕比例
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val outFile = File(context.cacheDir, "share/bg_crop_out.jpg")
                        android.graphics.BitmapFactory.decodeFile(outFile.absolutePath)
                            ?: error("裁剪结果为空")
                    }
                }.onSuccess { bmp ->
                    cropFallbackSource = null
                    saveBackground(bmp)
                }
                    .onFailure { e ->
                        // 有些 ROM 不支持 com.android.camera.action.CROP，或裁剪应用不回写输出文件：
                        // 这时改用应用内裁剪（源图还在手上），而不是把用户堵在一句报错上
                        val fallback = cropFallbackSource
                        if (fallback != null) {
                            showSnackbar("系统裁剪不可用，改用应用内裁剪")
                            pendingCropSource = fallback
                        } else {
                            showSnackbar("无法读取图片：${e.message ?: "未知错误"}")
                        }
                    }
            }
        }
    }

    fun launchSystemCrop(src: android.graphics.Bitmap) {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels
        val inFile = File(context.cacheDir, "share/bg_incoming.jpg")
        val outFile = File(context.cacheDir, "share/bg_crop_out.jpg").apply { delete() }
        val inUri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", inFile,
        )
        val outUri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", outFile,
        )
        val intent = android.content.Intent("com.android.camera.action.CROP").apply {
            setDataAndType(inUri, "image/*")
            // 输入读 + 输出写：两个授权都必须给。只给 READ 时，裁剪应用无法写入
            // EXTRA_OUTPUT 指向的 FileProvider Uri（华为等 ROM 直接失败），输出文件为空
            // → 回到本应用 decodeFile 得到 null，表现为"裁剪后无法保存"。
            // clipData 里同时带上进出的 Uri，是各 ROM 兼容性最好的写法。
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = android.content.ClipData
                .newUri(context.contentResolver, "crop_in", inUri)
                .apply { addItem(android.content.ClipData.Item(outUri)) }
            putExtra("crop", "true")
            putExtra("scale", true)
            putExtra("aspectX", w)
            putExtra("aspectY", h)
            putExtra("outputX", 1080)
            putExtra("outputY", (1080.0 * h / w).toInt())
            putExtra("outputFormat", android.graphics.Bitmap.CompressFormat.JPEG.toString())
            putExtra("return-data", false)
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, outUri)
        }
        try {
            cropLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            pendingCropSource = src  // 系统无裁剪组件 → 应用内裁剪兜底
        }
    }

    val bgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val bmp = decodeBackgroundBitmap(context.contentResolver, uri)
                        // 先落缓存文件（系统裁剪的输入）；bitmap 同时留作应用内裁剪兜底
                        val inFile = File(context.cacheDir, "share/bg_incoming.jpg")
                        inFile.parentFile?.mkdirs()
                        inFile.outputStream().use {
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
                        }
                        bmp
                    }
                }.onSuccess { bmp ->
                    cropFallbackSource = bmp
                    launchSystemCrop(bmp)
                }
                    .onFailure { e -> showSnackbar("无法读取图片：${e.message ?: "未知错误"}") }
            }
        }
    }
    pendingCropSource?.let { src ->
        BackgroundCropDialog(
            source = src,
            onDismiss = {
                pendingCropSource = null
                cropFallbackSource = null
            },
            onConfirm = { cropped ->
                pendingCropSource = null
                cropFallbackSource = null
                scope.launch { saveBackground(cropped) }
            },
        )
    }

    // ---- 课表对比：系统照片选择器 → 本地占用识别 → 人工校正 ----
    val compareImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        val dst = File(context.cacheDir, "occ_" + System.currentTimeMillis() + ".jpg")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            dst.outputStream().use { input.copyTo(it) }
                        }
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(dst.absolutePath, bounds)
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
                        val bmp = android.graphics.BitmapFactory.decodeFile(
                            dst.absolutePath,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
                        ) ?: throw IllegalStateException("无法解码图片")
                        val grid = OccupancyParser.parse(context, bmp)
                        val overlay = OccupancyParser.drawOverlay(bmp, grid)
                        val reviewFile = File(context.filesDir, "occ_review_" + System.currentTimeMillis() + ".png")
                        reviewFile.outputStream().use {
                            overlay.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it)
                        }
                        overlay.recycle(); bmp.recycle()
                        OccupancyDetection(reviewFile.absolutePath, grid)
                    }
                }
                result.onSuccess { pendingOccupancy = it }
                    .onFailure { e -> showSnackbar("识别失败：" + (e.message ?: "未知错误")) }
            }
        }
    }

    // ---- 导出到系统日历（.ics）：SAF 选保存位置后写入，零权限 ----
    var pendingIcsExport by remember { mutableStateOf<String?>(null) }
    val icsSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri != null) {
            val content = pendingIcsExport
            pendingIcsExport = null
            if (content != null) {
                scope.launch {
                    val result = runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(content.toByteArray(Charsets.UTF_8))
                            } ?: throw IllegalStateException("无法写入文件")
                        }
                    }
                    result.onSuccess { showSnackbar("已导出：可导入到系统日历或日历应用") }
                        .onFailure { e ->
                            showSnackbar("导出失败：${e.message ?: "未知错误"}")
                        }
                }
            }
        } else {
            pendingIcsExport = null
        }
    }
    val doExportIcs: () -> Unit = {
        val start = settings.semesterStart.takeIf { it > 0L }
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        if (start == null) {
            showSnackbar("请先设置开学时间")
        } else {
            val ics = com.buguake.timetable.data.CalendarExport.buildIcs(
                entries = entries,
                sectionTimes = settings.sectionTimes,
                semesterStart = start,
                opts = com.buguake.timetable.data.CalendarExport.Options(
                    timetableId = settings.timetableId,
                    timetableName = settings.timetableName,
                    totalWeeks = settings.totalWeeks,
                    remindMinutesBefore = if (settings.remindEnabled) settings.remindMinutesBefore else 0,
                ),
            )
            pendingIcsExport = ics
            icsSaver.launch("课表_${settings.timetableName.ifBlank { "我的课表" }}.ics")
        }
    }

    // ---- 系统日历：同步课程 / 清空已同步课程（共用一次日历权限申请） ----
    var calendarAction by remember { mutableStateOf<String?>(null) }
    var showClearCalendarConfirm by remember { mutableStateOf(false) }

    fun runCalendarSync() {
        scope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val start = settings.semesterStart.takeIf { it > 0L }
                        ?.let {
                            java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        }
                        ?: throw IllegalStateException("请先在上方设置开学时间")
                    val specs = CalendarSync.buildEventSpecs(
                        entries = entries,
                        sectionTimes = settings.sectionTimes,
                        semesterStart = start,
                        totalWeeks = settings.totalWeeks,
                    )
                    CalendarSync.sync(
                        context.contentResolver,
                        specs,
                        if (settings.remindEnabled) settings.remindMinutesBefore else 0,
                    )
                }
            }
            result.onSuccess { n ->
                Haptics.click(context)  // 同步完成确认触感
                showSnackbar("已把 $n 节课程写入系统日历「不挂科课表」")
            }.onFailure { e ->
                showSnackbar("同步失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun runCalendarClear() {
        scope.launch {
            val removed = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    CalendarSync.clearSyncedEvents(context.contentResolver)
                }
            }
            removed.onSuccess { n ->
                Haptics.click(context)  // 清空完成确认触感
                showSnackbar(if (n > 0) "已从系统日历移除 $n 节课程" else "系统日历里没有可清理的课程")
            }.onFailure { e ->
                showSnackbar("清理失败：${e.message ?: "未知错误"}")
            }
        }
    }

    val calendarPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[android.Manifest.permission.WRITE_CALENDAR] == true &&
            grants[android.Manifest.permission.READ_CALENDAR] == true
        when (calendarAction) {
            "sync" -> if (granted) runCalendarSync()
            else showSnackbar("需要「日历」权限才能同步，请在弹窗或系统设置中允许")
            "clear" -> if (granted) runCalendarClear()
            else showSnackbar("需要「日历」权限才能清理，请在弹窗或系统设置中允许")
        }
        calendarAction = null
    }
    fun requestCalendarPermission(action: String) {
        calendarAction = action
        calendarPerms.launch(
            arrayOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR,
            )
        )
    }
    val doSyncCalendar: () -> Unit = {
        if (settings.semesterStart <= 0L) {
            showSnackbar("请先设置开学时间")
        } else {
            requestCalendarPermission("sync")
        }
    }
    val doClearCalendar: () -> Unit = { showClearCalendarConfirm = true }


    // ---- 启动逻辑：回填默认课表（升级迁移）→ 重排提醒 ----
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            settingsRepo.ensureActiveTimetableReady()
        }
        // 应用更新/覆盖安装会清掉 AlarmManager 闹钟：每次启动重排一次课前提醒；
        // 同时强刷一次小组件（防升级后残留旧渲染数据）
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            com.buguake.timetable.reminder.ClassReminderScheduler.reschedule(context)
            com.buguake.timetable.widget.ScheduleWidgetProvider.requestUpdate(context)
        }
    }

    // ---- 新建课表：弹窗选来源（复制现有），名字可留空稍后设置 ----
    // saveable：选文件期间 Activity 可能被系统回收重建，id 丢了会导致复制目标错乱
    var showNewTimetableDialog by rememberSaveable { mutableStateOf(false) }
    var autoCreatedTimetableId by rememberSaveable { mutableStateOf(0L) }
    var pendingAutoName by rememberSaveable { mutableStateOf("") }
    // 「从教务网站导入」预置的目标课表：先进导入流程，再按此 id 注入适配器脚本
    var presetImportTableId by rememberSaveable { mutableStateOf(0L) }
    val confirmNewTimetable: (String, Long?) -> Unit = { name, copyFrom ->
        showNewTimetableDialog = false
        showTimetableManage = false
        // copyFrom == null 是「空白课表」来源，必须真的建表：
        // 这里曾经只有 copyFrom != null 的分支，空白来源整个是空实现——
        // 弹窗关掉了却什么都没建，表现为"选空白课表没反应"。
        scope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    if (copyFrom != null) {
                        // 复制现有课表：结构原样复制，周次重置整学期
                        val src = scheduleRepo.getTimetable(copyFrom)
                            ?: throw IllegalStateException("源课表不存在")
                        scheduleRepo.copyTimetable(
                            src, name.ifBlank { "未命名" }, src.startMillis, src.totalWeeks,
                        )
                    } else {
                        // 空白课表：用全局学期设置作为开学日与周数
                        scheduleRepo.createTimetable(
                            name.ifBlank { "未命名" },
                            settings.semesterStart,
                            settings.totalWeeks,
                        )
                    }
                }
            }
            result.onSuccess { id ->
                settingsRepo.setActiveTimetable(id)
                AppRefresh.onDataChanged(context)
                showSnackbar("已创建《${name.ifBlank { "未命名" }}》")
            }.onFailure { e ->
                showSnackbar("创建失败：${e.message ?: "未知错误"}")
            }
        }
    }

    // ---- 新建课表 → 从教务网站导入：先建表并设为活动，再进导入流程（不再让用户选一次目标） ----
    val startWebImportWithNewTimetable: (String) -> Unit = { name ->
        showNewTimetableDialog = false
        showTimetableManage = false
        scope.launch {
            val result = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    scheduleRepo.createTimetable(
                        name.ifBlank { "新课表" },
                        settings.semesterStart,
                        settings.totalWeeks,
                    )
                }
            }
            result.onSuccess { id ->
                settingsRepo.setActiveTimetable(id)
                AppRefresh.onDataChanged(context)
                presetImportTableId = id
                showWebImport = true
            }.onFailure { e ->
                showSnackbar("创建失败：${e.message ?: "未知错误"}")
            }
        }
    }

    // ---- 导入确认：新建课表（自动命名/开学日）或覆盖指定课表 ----
    // parsed 由调用方传入（弹窗宿主在调用前已清 pendingImport，不能再回头读状态）
    val confirmImport: (ParsedSchedule, ImportTarget) -> Unit = { parsed, target ->
        scope.launch {
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    when (target) {
                        is ImportTarget.NewTimetable -> {
                            val id = scheduleRepo.createTimetable(
                                target.name,
                                target.startMillis,
                                target.totalWeeks,
                            )
                            scheduleRepo.importSchedule(parsed, id)
                            settingsRepo.setActiveTimetable(id)
                            id
                        }
                        is ImportTarget.IntoCreated -> {
                            scheduleRepo.importSchedule(parsed, target.timetableId)
                            // 自动新建的课表：把弹窗里的命名与日期落库
                            scheduleRepo.getTimetable(target.timetableId)?.let { tt ->
                                scheduleRepo.updateTimetable(
                                    tt.copy(
                                        name = target.name,
                                        startMillis = target.startMillis,
                                        totalWeeks = target.totalWeeks,
                                    )
                                )
                            }
                            target.timetableId
                        }
                        is ImportTarget.Existing -> {
                            scheduleRepo.importSchedule(parsed, target.timetableId)
                            target.timetableId
                        }
                    }
                }
            }.onSuccess { id ->
                AppRefresh.onDataChanged(context)  // 入库：刷新小组件 + 重排提醒
                val name = when (target) {
                    is ImportTarget.NewTimetable -> target.name
                    is ImportTarget.IntoCreated -> target.name
                    is ImportTarget.Existing ->
                        timetableInfos.firstOrNull { it.timetable.id == id }?.timetable?.name ?: ""
                }
                android.util.Log.i(
                    "ScheduleImport",
                    "导入成功 target=$id name=$name courses=${parsed.courses.size} entries=${parsed.entries.size}",
                )
                showSnackbar("已导入到《$name》：${parsed.courses.size} 门课程，建议检查课表")
            }.onFailure { e ->
                android.util.Log.w("ScheduleImport", "导入失败 target=$target", e)
                showSnackbar("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    // ---- 系统手势/按键返回：覆盖页显示时返回先关闭覆盖页，回到原界面原位置 ----
    val overlayShown = showSectionTimes || showReminders || showAbout || showPrivacy ||
        showTimetableManage || showWidgetBind || showCompare || showWebImport
    androidx.activity.compose.BackHandler(enabled = overlayShown) {
        when {
            pendingOccupancy != null -> pendingOccupancy = null
            showSectionTimes -> showSectionTimes = false
            showReminders -> showReminders = false
            showTimetableManage -> showTimetableManage = false
            showWidgetBind -> showWidgetBind = false
            showCompare -> showCompare = false
            showAbout -> showAbout = false
            showPrivacy -> showPrivacy = false
        }
    }

    // ---- 深色模式解析 ----
    val darkTheme = when (settings.darkMode) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    com.buguake.timetable.ui.theme.ComposeAppTheme(
        darkTheme = darkTheme,
        themeColorMode = settings.themeColorMode,
        themeSeedColor = settings.themeSeedColor,
        themeTextColor = settings.themeTextColor,
    ) {
        // 圆形主题切换宿主：记录触点 + 承载「旧主题快照」遮罩（只组合一份界面）
        com.buguake.timetable.ui.theme.ThemeRevealHost {
        // 实验性：自定义背景层包裹整个 Scaffold（含底栏），玻璃风格随开关生效
        val glassOn = settings.customBgEnabled
        com.buguake.timetable.ui.theme.CustomBackgroundLayer(
            enabled = glassOn,
            imagePath = settings.customBgPath,
            blurDp = settings.customBgBlurDp,
        ) {
        // 主界面常驻组合：二级页只是盖在上面，返回时保留滚动位置等全部状态
        // （原先 if(!overlayShown) 会把整个 Scaffold 拆掉重组，返回即丢位置）；
        // alpha 跟随动画淡隐，避免生切换底
        val shellAlpha by animateFloatAsState(
            targetValue = if (overlayShown) 0f else 1f,
            animationSpec = AppMotion.effectsFast(),
            label = "shellAlpha",
        )
        Box(Modifier.fillMaxSize()) {
        Scaffold(
        modifier = Modifier.fillMaxSize().alpha(shellAlpha),
        containerColor = if (glassOn) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        bottomBar = {
            // 迷你底栏：56dp 高，图标 + 选中态胶囊；玻璃模式下半透明 + 顶部细描边
            @Composable fun BottomBarRow() {
                // 胶囊位置唯一真源（槽位单位的连续值）。点击、拖动、松手吸附都只写它一个。
                // 旧实现把拖动位置拆成「isDragging ? dragBase + dragPx : pillSlot.value」两个分支，
                // 而且 dragPx / isDragging / dragBase 都没 remember——拖动中一旦发生重组，手指写的
                // 就是被丢弃的旧状态对象，画面便一直停在起点，直到松手改 tab 才一次性跳到终点。
                var pillPos by remember { mutableFloatStateOf(tab.toFloat()) }
                // 位置在组合期读取（见下方 pillLeftPx）：拖动每帧都重组，视觉严格跟手，
                // 不再依赖「放置期才读」的延迟读取路径
                // 拖动中 / 本次 tab 变化是否来自拖动落位 / 打断在途动画的令牌
                var dragging by remember { mutableStateOf(false) }
                var settledByDrag by remember { mutableStateOf(false) }
                var dragToken by remember { mutableIntStateOf(0) }
                var settleNonce by remember { mutableIntStateOf(0) }
                // 抓取点：手指落在胶囊内的横向偏移（px）。逐帧用「手指绝对位置 − 抓取点」反算位置，
                // 而不是累计增量——增量在 slop 判定时会丢掉一段，丢帧后误差还会继续累积
                var grabDx by remember { mutableFloatStateOf(0f) }
                // 切换编排（Pixel2Motion 动效纪律，贴纸品牌个性词：俏皮·跟手·软弹）：
                // 预备 20%（胶囊沿移动方向拉伸压扁）→ 主动作 50%（位置中弹弹簧滑移）→
                // 跟随 30%（形变回弹收尾，与图标 pop 的弹簧规格错开，避免同帧停住）
                val pillStretchX = remember { Animatable(1f) }
                val pillStretchY = remember { Animatable(1f) }
                // 拖动经过槽位时的触感记录：每跨过一个槽位轻震一次
                var lastTickSlot by remember { mutableIntStateOf(tab) }
                // 位置动画的唯一驱动：tab 变化（点击 / 快捷方式跳转 / 拖动落位）或 settleNonce 自增。
                // dragToken 参与 key——拖动一起手就重启本效应，立刻放弃在途动画，位置全交给手指
                LaunchedEffect(tab, dragToken, settleNonce) {
                    // 先消费「来自拖动落位」标记：即使这次重启是被拖动打断的，
                    // 也不把标记留给下一次点击，否则下一次点击会少掉「预备」拉伸
                    val fromDrag = settledByDrag
                    settledByDrag = false
                    if (dragging) return@LaunchedEffect
                    // 拖动落位时落点已由手指决定：跳过「预备」拉伸，直接连续吸附到槽位
                    val choreograph = !fromDrag && AppMotion.enabled &&
                        pillPos != tab.toFloat()
                    if (choreograph) {
                        // 预备：先压后冲
                        launch {
                            pillStretchX.animateTo(
                                1.24f,
                                androidx.compose.animation.core.tween(70),
                            )
                            pillStretchX.animateTo(
                                1f,
                                AppMotion.bouncy(),
                            )
                        }
                        launch {
                            pillStretchY.animateTo(
                                0.76f,
                                androidx.compose.animation.core.tween(70),
                            )
                            pillStretchY.animateTo(
                                1f,
                                AppMotion.bouncy(),
                            )
                        }
                        kotlinx.coroutines.delay(45)
                    }
                    // 主动作 + 跟随：软弹弹簧，带一次可见但收敛的过冲（拖动落位用快速规格收尾）
                    animate(
                        initialValue = pillPos,
                        targetValue = tab.toFloat(),
                        animationSpec = if (choreograph) AppMotion.spatial() else AppMotion.spatialFast(),
                    ) { value, _ -> pillPos = value }
                }
                // 水平内缩 8dp：悬浮岛两端是圆弧，胶囊若顶到槽位边缘会被弧线切到、
                // 看起来像溢出导航条；内缩后首尾槽位的胶囊也完全落在弧线以内
                BoxWithConstraints(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
                    val slot = maxWidth / TAB_LABELS.size
                    val slotPx = with(LocalDensity.current) { slot.toPx() }
                    val pillW = 48.dp
                    val pillInsetPx = with(LocalDensity.current) { ((slot - pillW) / 2).toPx() }
                    // 滑移胶囊：绘制在图标层【之下】，仅作视觉指示，不拦截点击；
                    // CenterStart 对齐后再做横向偏移，否则默认 TopStart 会顶到导航条上沿。
                    // 偏移在组合期算好：拖动每帧都重组 → 每帧都会重新放置，
                    // 不会出现"状态已经变了、画面还停在原处"（旧实现只写在放置期读取的 lambda 里）
                    val pillLeftPx = pillInsetPx + pillPos * slotPx
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset { IntOffset(pillLeftPx.roundToInt(), 0) }
                            .width(pillW)
                            .height(32.dp)
                            .graphicsLayer {
                                scaleX = pillStretchX.value
                                scaleY = pillStretchY.value
                            }
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                    // 图标与胶囊用同一套槽位公式：每槽位宽度 = slot，图标居中，
                    // 整槽位可点（比 64dp 胶囊点击区域大，且不会互相遮挡）；
                    // 横向拖动跟手移动胶囊，点击（未过滑动阈值）仍走各槽位的 clickable。
                    // 落位只改 tab 与位置真源，位移动画交给上方 LaunchedEffect——
                    // 不再"先清偏移、再在协程里补 snapTo"，也就没有松手瞬间回跳一帧再飞出去的残影；
                    // onDragCancel 与 onDragEnd 同等处理，避免手势被打断后胶囊卡在半路
                    fun settleDrag() {
                        if (!dragging) return
                        dragging = false
                        val target = pillPos.roundToInt().coerceIn(0, TAB_LABELS.lastIndex)
                        Haptics.tick(context)  // 松手吸附触感
                        lastTickSlot = target
                        settledByDrag = true
                        // 落点与当前页不同：改 tab 即触发落位动画；相同则用 nonce 触发同一段动画
                        if (target != tab) tab = target else settleNonce++
                    }
                    Row(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { down ->
                                        dragging = true
                                        dragToken++  // 放弃在途的胶囊动画，位置立即交给手指
                                        // 抓取点 = 手指落在胶囊内的横向偏移：
                                        // 按在胶囊哪一点，拖起来就还在那一点（胶囊不会突然跳到手指下）
                                        grabDx = down.x - (pillInsetPx + pillPos * slotPx)
                                        lastTickSlot = pillPos.roundToInt()
                                        Haptics.tick(context)  // 拖动开始轻震
                                    },
                                    onDragEnd = { settleDrag() },
                                    onDragCancel = { settleDrag() },
                                ) { change, _ ->
                                    change.consume()
                                    // 逐帧由手指绝对位置反算胶囊位置：slop 判定之前的那段位移也一并算进来
                                    pillPos = ((change.position.x - grabDx - pillInsetPx) / slotPx)
                                        .coerceIn(0f, (TAB_LABELS.size - 1).toFloat())
                                    // 拖动每跨过一个槽位：轻震一格
                                    val hoveredSlot = pillPos.roundToInt()
                                    if (hoveredSlot != lastTickSlot && hoveredSlot in TAB_LABELS.indices) {
                                        lastTickSlot = hoveredSlot
                                        Haptics.tick(context)
                                    }
                                }
                            }
                    ) {
                        TAB_LABELS.forEachIndexed { i, label ->
                            // 选中图标轻微放大回弹（软弹规格，与胶囊位移弹簧错拍收尾）
                            val iconScale by animateFloatAsState(
                                targetValue = if (tab == i) 1.15f else 1f,
                                animationSpec = AppMotion.bouncy(),
                                label = "tabScale$i",
                            )
                            // 选中/未选颜色平滑过渡（不再瞬变）
                            val tabTint by androidx.compose.animation.animateColorAsState(
                                targetValue = if (tab == i) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = AppMotion.effects(),
                                label = "tabTint$i",
                            )
                            Box(
                                Modifier
                                    .width(slot)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,  // 底栏去水波纹
                                    ) {
                                        tab = i
                                        Haptics.tick(context)  // 页签切换轻震
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    when (i) {
                                        0 -> TimetableIcon
                                        1 -> TodayIcon
                                        2 -> CampusIcon
                                        else -> MineIcon
                                    },
                                    contentDescription = label,
                                    tint = tabTint,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                )
                            }
                        }
                    }
                }
            }
            if (glassOn) {
                // 悬浮岛底栏：居中紧凑胶囊（参考主流课表应用的浮动岛）——
                // 两侧留空、内容从岛下方穿过，加重玻璃底做出"浮在页面上"的体感；
                // 内部仍是同一套滑动胶囊 / 拖动换页 / 触感逻辑（固定 4×64dp 槽位）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val barShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                    com.buguake.timetable.ui.theme.GlassSurface(
                        modifier = Modifier.width(272.dp),
                        shape = barShape,
                        tintAlpha = if (com.buguake.timetable.ui.theme.isDarkTheme()) 0.72f else 0.56f,
                    ) { BottomBarRow() }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) { BottomBarRow() }
            }
        },
        snackbarHost = {},  // Snackbar 已上移到根 Box 顶层，二级页打开时也能看到提示
    ) { padding ->
        // Tab 方向性转场：切到右边页从右滑入，切到左边页从左滑入；规格取 Expressive MotionScheme
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val move = AppMotion.spatial<androidx.compose.ui.unit.IntOffset>()
                val fade = AppMotion.effectsFast<Float>()
                if (targetState > initialState) {
                    (slideInHorizontally(move) { it / 6 } + fadeIn(fade))
                        .togetherWith(
                            slideOutHorizontally(move) { -it / 8 } + fadeOut(fade)
                        )
                } else {
                    (slideInHorizontally(move) { -it / 6 } + fadeIn(fade))
                        .togetherWith(
                            slideOutHorizontally(move) { it / 8 } + fadeOut(fade)
                        )
                }
            },
            label = "tabSwitch",
            // 内容区不再避让底部悬浮岛：只保留顶部 padding，内容通到屏幕底、从岛下方穿过
        modifier = Modifier.padding(top = padding.calculateTopPadding()),
        ) { page ->
            when (page) {
                0 -> TimetableScreen(
                    entries = entries,
                    settings = settings,
                    glass = glassOn,
                    onCourseClick = { selectedEntry = it },
                    onShowSnackbar = showSnackbar,
                    onImportClick = { presetImportTableId = 0L; showWebImport = true },
                    onAddClick = {
                        // 收起已打开的课程详情/编辑弹窗，避免两个面板叠放
                        selectedEntry = null
                        editingEntry = null
                        showAddCourse = true
                        addCoursePrefill = null
                    },
                    onAddAt = { day, sec ->
                        addCoursePrefill = day to sec
                        showAddCourse = true
                    },
                    onMoveEntry = { entry, day, start, end, week ->
                        pendingMove = MoveReq(entry, day, start, end, week)
                    },
                    timetables = timetableInfos,
                    onSwitchTimetable = { id ->
                        settingsRepo.setActiveTimetable(id)
                        AppRefresh.onDataChanged(context)
                        val name = timetableInfos.firstOrNull { it.timetable.id == id }?.timetable?.name ?: ""
                        Haptics.click(context)  // 切换课表触感
                        showSnackbar("已切换到《$name》")
                    },
                    onNewTimetable = { showNewTimetableDialog = true },
                    onOpenManage = { showTimetableManage = true },
                    onOpenMoveCourse = {
                        if (settings.semesterStartDate == null) {
                            showSnackbar("请先在「我的」设置开学时间，才能按日期调课")
                        } else {
                            showMoveCourse = true
                        }
                    },
                )
                1 -> TodayScreen(
                    entries = entries,
                    settings = settings,
                    glass = glassOn,
                )
                2 -> com.buguake.timetable.campus.ui.CampusScreen(
                    glass = glassOn,
                    showSnackbar = showSnackbar,
                    openUnlockSeq = pendingUnlockSeq,
                    onUnlockConsumed = { consumedUnlockSeq = it },
                    openLaundrySeq = pendingLaundrySeq,
                    onLaundryConsumed = { consumedLaundrySeq = it },
                )
                else -> MineScreen(
                    settings = settings,
                    glass = glassOn,
                    courseCount = courses.size,
                    entryCount = entries.size,
                    onImport = { presetImportTableId = 0L; showWebImport = true },
                    onSetSemesterStart = setSemesterStart,
                    onSetTotalWeeks = {
                        scope.launch {
                            settingsRepo.setTotalWeeks(it)
                            AppRefresh.onDataChanged(context)
                            Haptics.tick(context)  // 周数设置轻震
                        }
                    },
                    onSetShowWeekend = {
                        settingsRepo.setShowWeekend(it)
                        AppRefresh.onDataChanged(context)  // 周末开关影响小组件与提醒
                    },
                    onSetShowNonCurrentWeek = { settingsRepo.setShowNonCurrentWeek(it) },
                    onSetShowTeacherOnBlock = { settingsRepo.setShowTeacherOnBlock(it) },
                    onSetShowLocationOnBlock = { settingsRepo.setShowLocationOnBlock(it) },
                    onSetShowExamsOnHome = { settingsRepo.setShowExamsOnHome(it) },
                    onSetMoveScope = { settingsRepo.setMoveScope(it) },
                    onSetDynamicColor = { settingsRepo.setDynamicColor(it) },
                    onSetDarkMode = { settingsRepo.setDarkMode(it) },
                    onSetThemeColor = { mode, brand, text ->
                        settingsRepo.setThemeColorMode(mode)
                        settingsRepo.setThemeSeedColor(brand)
                        settingsRepo.setThemeTextColor(text)
                    },
                    onOpenSectionTimes = { showSectionTimes = true },
                    onOpenReminders = { showReminders = true },
                    onSetCustomBgEnabled = { settingsRepo.setCustomBgEnabled(it) },
                    onPickBackground = {
                        bgPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onClearBackground = {
                        scope.launch {
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                if (settings.customBgPath.isNotBlank()) {
                                    File(settings.customBgPath).delete()
                                }
                                // 只清图片，保留玻璃开启状态：回退到内置渐变背景
                                settingsRepo.setCustomBgPath("")
                            }
                            showSnackbar("已恢复默认渐变背景")
                        }
                    },
                    onSetCustomBgBlur = { settingsRepo.setCustomBgBlur(it) },
                    onSyncCalendar = doSyncCalendar,
                    onClearCalendar = doClearCalendar,
                    onExportIcs = doExportIcs,
                    onClearData = {
                        scope.launch {
                            val affected = timetableInfos
                                .firstOrNull { it.timetable.id == settings.timetableId }
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                scheduleRepo.clearTimetable(settings.timetableId)
                            }
                            AppRefresh.onDataChanged(context)
                            Haptics.heavy(context)  // 危险操作完成的强反馈
                            showSnackbar("已清除《${affected?.timetable?.name ?: "当前课表"}》")
                        }
                    },
                    onShowSnackbar = showSnackbar,
                    onOpenAbout = { showAbout = true },
                    onOpenPrivacy = { showPrivacy = true },
                    onOpenTimetableManage = { showTimetableManage = true },
                    onOpenWidgetBind = { widgetBindRefresh++; showWidgetBind = true },
                    onOpenCompare = { showCompare = true },
                )
             }
        }
    }

        // 触摸拦截层已并入 OverlayPage（随进出场动画一同出现/消失）

    // ---- 课表管理页（全屏覆盖；玻璃模式下透出背景） ----
    OverlayPage(showTimetableManage, onBack = { showTimetableManage = false }) {
        com.buguake.timetable.ui.timetable.TimetableManagePage(
            timetables = timetableInfos,
            activeId = settings.timetableId,
            glass = glassOn,
            onSwitch = { t ->
                settingsRepo.setActiveTimetable(t.id)
                AppRefresh.onDataChanged(context)
                Haptics.click(context)  // 切换课表触感
                showSnackbar("已切换到《${t.name}》")
            },
            onUpdate = { t ->
                scope.launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        scheduleRepo.updateTimetable(t)
                    }
                    AppRefresh.onDataChanged(context)
                    Haptics.click(context)
                    showSnackbar("已保存")
                }
            },
            onEditSchedule = { t ->
                // 切到该课表后进入作息编辑（作息页编辑的是活动课表）
                settingsRepo.setActiveTimetable(t.id)
                AppRefresh.onDataChanged(context)
                showTimetableManage = false
                showSectionTimes = true
                showSnackbar("已切换到《${t.name}》，请调整作息时间")
            },
            onCopy = { t ->
                scope.launch {
                    val result = runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            scheduleRepo.copyTimetable(
                                t, "${t.name} 副本", t.startMillis, t.totalWeeks,
                            )
                        }
                    }
                    result.onSuccess {
                        AppRefresh.onDataChanged(context)
                        Haptics.click(context)  // 复制成功触感
                        showSnackbar("已复制为《${t.name} 副本》")
                    }.onFailure { e ->
                        showSnackbar("复制失败：${e.message ?: "未知错误"}")
                    }
                }
            },
            onDelete = { t ->
                scope.launch {
                    val result = runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            scheduleRepo.deleteTimetable(t.id, settings.timetableId)
                        }
                    }
                    result.onSuccess {
                        AppRefresh.onDataChanged(context)
                        Haptics.heavy(context)  // 删除课表强反馈
                        showSnackbar("已删除《${t.name}》")
                    }.onFailure { e ->
                        showSnackbar(e.message ?: "删除失败")
                    }
                }
            },
            onCreate = {
                showTimetableManage = false
                showNewTimetableDialog = true
            },
            onImportFromWeb = {
                // 不预建课表：直接进导入流程，在导入页里选目标
                presetImportTableId = 0L
                showTimetableManage = false
                showWebImport = true
            },
            onBack = { showTimetableManage = false },
        )
    }

    // ---- 小组件绑定页（全屏覆盖） ----
    OverlayPage(showWidgetBind, onBack = { showWidgetBind = false }) {
        val widgetInstances = remember(showWidgetBind, widgetBindRefresh) {
            queryWidgetInstances(context)
        }
        val bindings = remember(widgetInstances, widgetBindRefresh) {
            widgetInstances.associate { it.widgetId to settingsRepo.getWidgetTimetableId(it.widgetId) }
        }
        com.buguake.timetable.ui.timetable.WidgetBindPage(
            widgets = widgetInstances,
            timetables = timetableInfos,
            bindings = bindings,
            glass = glassOn,
            onBind = { widgetId, ttId ->
                settingsRepo.setWidgetTimetableId(widgetId, ttId)
                widgetBindRefresh++
                AppRefresh.onDataChanged(context)
            },
            onBack = { showWidgetBind = false },
        )
    }

    // ---- 按日期调课弹窗：选「被调日期 → 调到日期」，落点直接覆盖（无独立页面） ----
    if (showMoveCourse) {
        com.buguake.timetable.ui.timetable.MoveDayDialog(
            semesterStart = settings.semesterStartDate,
            onConfirm = { fromWeek, fromDay, toWeek, toDay ->
                showMoveCourse = false
                scope.launch {
                    runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            scheduleRepo.moveDayBetweenWeeks(
                                settings.timetableId, fromWeek, fromDay, toWeek, toDay,
                            )
                        }
                    }.onSuccess {
                        AppRefresh.onDataChanged(context)
                        Haptics.heavy(context)
                        showSnackbar("调课完成：第 $fromWeek 周周$fromDay → 第 $toWeek 周周$toDay，落点已覆盖")
                    }.onFailure {
                        showSnackbar("调课失败：${it.message ?: "未知错误"}")
                    }
                }
            },
            onDismiss = { showMoveCourse = false },
        )
    }

    // ---- 新建课表弹窗 ----
    if (showNewTimetableDialog) {
        NewTimetableDialog(
            timetables = timetableInfos,
            onConfirm = confirmNewTimetable,
            onDismiss = { showNewTimetableDialog = false },
            onImportFromWeb = startWebImportWithNewTimetable,
        )
    }

    // 执行调课（固定范围与弹窗确认共用）：thisWeekOnly=true 仅本周，false 以后每周
    val performMove: suspend (MoveReq, Boolean) -> Unit = { m, thisWeekOnly ->
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        runCatching {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                scheduleRepo.moveEntryScoped(
                    m.entry.entryId, m.day, m.start, m.end, m.week, thisWeekOnly,
                )
            }
        }.onSuccess {
            AppRefresh.onDataChanged(context)
            Haptics.heavy(context)
            showSnackbar(
                if (thisWeekOnly) "已调整（仅第 ${m.week} 周）：${dayNames[m.day - 1]} 第 ${m.start}-${m.end} 节"
                else "已调整（以后每周）：${dayNames[m.day - 1]} 第 ${m.start}-${m.end} 节"
            )
        }.onFailure {
            // 协程被取消（如界面重组）不算调课失败，不打扰用户
            if (it !is kotlinx.coroutines.CancellationException) {
                showSnackbar("调整失败：${it.message ?: "未知错误"}")
            }
        }
    }

    // ---- 调课范围确认：以后每周 / 仅本周 ----
    // 设置里固定了范围时直接执行，不再每次弹窗询问（ask = 每次问）。
    // 用 Unit key + snapshotFlow 监听：effect 内部要清空 pendingMove，
    // 若把 pendingMove 当 key，置空时会重启 effect、取消正在执行的调课协程
    LaunchedEffect(Unit) {
        snapshotFlow { pendingMove }.collect { m ->
            if (m != null && settings.moveScope != SettingsRepository.MOVE_SCOPE_ASK) {
                pendingMove = null
                performMove(m, settings.moveScope == SettingsRepository.MOVE_SCOPE_THIS_WEEK)
            }
        }
    }
    pendingMove?.let { m ->
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("调整课程") },
            text = {
                Text("把《${m.entry.courseName}》移到${dayNames[m.day - 1]} 第 ${m.start}-${m.end} 节。调整范围是？")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMove = null
                    scope.launch { performMove(m, false) }
                }) { Text("以后每周") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingMove = null
                        scope.launch { performMove(m, true) }
                    }) { Text("仅本周") }
                    TextButton(onClick = { pendingMove = null }) { Text("取消") }
                }
            },
        )
    }

    pendingImport?.let { parsed ->
        val preset = timetableInfos.firstOrNull { it.timetable.id == autoCreatedTimetableId }
        ImportChooseDialog(
            parsedName = parsed.suggestedName,
            suggestedStartMillis = parsed.suggestedStartMillis,
            suggestedTotalWeeks = parsed.suggestedTotalWeeks,
            timetables = timetableInfos,
            defaultStartMillis = settings.semesterStart,
            defaultTotalWeeks = settings.totalWeeks,
            presetTarget = preset,
            presetName = pendingAutoName,
            onConfirm = { target ->
                val p = pendingImport
                pendingImport = null
                autoCreatedTimetableId = 0L
                pendingAutoName = ""
                if (p != null) confirmImport(p, target)
            },
            onDismiss = {
                pendingImport = null
                autoCreatedTimetableId = 0L
                pendingAutoName = ""
            },
        )
    }

    // ---- 清空系统日历课程：确认弹窗（防误触，只动本应用的「不挂科课表」日历） ----
    if (showClearCalendarConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCalendarConfirm = false },
            title = { Text("清空系统日历中的课程") },
            text = {
                Text("将删除系统日历「不挂科课表」里本应用写入的全部课程事件，不会影响你的其他日历与日程。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearCalendarConfirm = false
                    requestCalendarPermission("clear")
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCalendarConfirm = false }) { Text("取消") }
            },
        )
    }

    // ---- 作息时间独立页（全屏覆盖，含系统返回键处理；玻璃模式下透出背景） ----
    // 预设列表：保存当前作息 / 快捷切换 / 删除
    var sectionPresets by remember { mutableStateOf(settingsRepo.sectionPresets()) }
    OverlayPage(showSectionTimes, onBack = { showSectionTimes = false }) {
        com.buguake.timetable.ui.mine.SectionTimePage(
            settings = settings,
            glass = glassOn,
            presets = sectionPresets,
            onSavePreset = { name ->
                scope.launch {
                    settingsRepo.saveSectionPreset(
                        name,
                        com.buguake.timetable.data.SettingsRepository.encodeSections(settings.sectionTimes),
                    )
                    sectionPresets = settingsRepo.sectionPresets()
                }
                showSnackbar("已保存作息预设「$name」")
            },
            onDeletePreset = { name ->
                settingsRepo.deleteSectionPreset(name)
                sectionPresets = settingsRepo.sectionPresets()
            },
            onSetSectionTimes = {
                scope.launch {
                    settingsRepo.setSectionTimes(it)
                    AppRefresh.onDataChanged(context)  // 作息变化影响提醒触发时刻
                }
            },
            onSetSectionsPerDay = {
                scope.launch {
                    settingsRepo.setSectionsPerDay(it)
                    AppRefresh.onDataChanged(context)
                }
            },
            onBack = { showSectionTimes = false },
        )
    }

    // ---- 课程提醒独立页（权限引导 / 运行诊断 / 提前量 / 测试） ----
    OverlayPage(showReminders, onBack = { showReminders = false }) {
        com.buguake.timetable.ui.mine.ReminderPage(
            settings = settings,
            glass = glassOn,
            onSetRemindEnabled = {
                settingsRepo.setRemindEnabled(it)
                AppRefresh.onDataChanged(context)
            },
            onSetRemindMinutes = {
                settingsRepo.setRemindMinutesBefore(it)
                AppRefresh.onDataChanged(context)
            },
            onTestNow = {
                scope.launch {
                    com.buguake.timetable.reminder.ClassReminderScheduler.fireTest(context)
                }
            },
            onTestInOneMinute = {
                scope.launch {
                    com.buguake.timetable.reminder.ClassReminderScheduler.fireTestInOneMinute(context)
                }
            },
            onBack = { showReminders = false },
        )
    }

    // ---- 教务网页导入（全屏覆盖：学校 → 适配器 → WebView） ----
    OverlayPage(showWebImport, onBack = { showWebImport = false }) {
        WebImportFlow(
            timetables = timetableInfos,
            defaultStartMillis = settings.semesterStart,
            defaultTotalWeeks = settings.totalWeeks,
            glass = glassOn,
            presetImportTableId = presetImportTableId.takeIf { it != 0L },
            onClose = {
                showWebImport = false
                presetImportTableId = 0L
            },
        )
    }

    // ---- 关于页 / 隐私政策页（全屏覆盖；玻璃模式下透出背景） ----
    OverlayPage(showAbout, onBack = { showAbout = false }) {
            com.buguake.timetable.ui.mine.AboutPage(
                versionName = com.buguake.timetable.BuildConfig.VERSION_NAME,
            onBack = { showAbout = false },
        )
    }
        // ---- 课表对比（实验性）：数据库课表 + 图片对比课表 → 共同空闲 ----
        OverlayPage(showCompare, onBack = { showCompare = false }) {
            androidx.compose.runtime.LaunchedEffect(showCompare) {
                if (showCompare) compareTimetables = CompareRepository.load(context)
            }
            CompareScreen(
                timetables = timetableInfos,
                compareTimetables = compareTimetables,
                glass = glassOn,
                sectionsPerDay = settings.sectionsPerDay,
                defaultWeek = maxOf(
                    1,
                    com.buguake.timetable.data.WeekCalculator.currentWeek(
                        settings.semesterStartDate,
                        java.time.LocalDate.now(),
                    ),
                ),
                loadEntries = { id ->
                    scheduleRepo.observeAllEntries(id).first()
                },
                onPickImage = {
                    compareImagePicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onDeleteCompare = { id ->
                    scope.launch {
                        compareTimetables = CompareRepository.remove(context, id)
                        Haptics.heavy(context)
                        showSnackbar("已删除对比课表")
                    }
                },
                onBack = { showCompare = false },
            )
            // ---- 截图识别校正（三级页）：与全部二/三级页统一进出场 + 侧滑返回 ----
            // 退场动画期间 pendingOccupancy 已置空，用 reviewLast 保留内容渲染最后一帧
            val reviewOpen = pendingOccupancy != null
            val reviewLast = remember { mutableStateOf<OccupancyDetection?>(null) }
            if (reviewOpen) reviewLast.value = pendingOccupancy
            androidx.compose.animation.AnimatedVisibility(
                visible = reviewOpen,
                enter = com.buguake.timetable.ui.theme.pageEnterCloser(),
                exit = com.buguake.timetable.ui.theme.pageExitCloser(),
            ) {
                reviewLast.value?.let { det ->
                    com.buguake.timetable.ui.theme.SwipeBackBox(
                        onBack = { pendingOccupancy = null },
                        enabled = true,
                    ) {
                        OccupancyReviewScreen(
                            detection = det,
                    glass = glassOn,
                    onSave = { name, dayCount, blocks ->
                        pendingOccupancy = null
                        scope.launch {
                            compareTimetables = CompareRepository.add(
                                context,
                                CompareTimetable(
                                    CompareRepository.nextId(compareTimetables),
                                    name, dayCount, blocks,
                                ),
                            )
                            Haptics.click(context)
                            showSnackbar("已添加对比课表「" + name + "」")
                        }
                    },
                        onCancel = { pendingOccupancy = null },
                        )
                    }
                }
            }
        }

    OverlayPage(showPrivacy, onBack = { showPrivacy = false }) {
        com.buguake.timetable.ui.mine.PrivacyPage(
            onBack = { showPrivacy = false },
        )
    }

        }  // Box(fillMaxSize)
    }  // CustomBackgroundLayer

    selectedEntry?.let { e ->
        CourseDetailSheet(
            entry = e,
            dynamicColor = settings.dynamicColor,
            onEdit = { editingEntry = it },
            onDelete = { entry ->
                scope.launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        scheduleRepo.deleteEntry(entry.entryId)
                    }
                    AppRefresh.onDataChanged(context)
                }
                Haptics.heavy(context)  // 删除课程强反馈
                showSnackbar("已删除本节")
            },
            onDismiss = { selectedEntry = null },
        )
    }

    editingEntry?.let { e ->
        com.buguake.timetable.ui.timetable.CourseEditSheet(
            entry = e,
            onSave = { name, teacher, campus, building, room ->
                scope.launch {
                    runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            if (name != e.courseName) scheduleRepo.renameCourse(e.courseId, name)
                            scheduleRepo.updateEntryInfo(e.entryId, teacher, campus, building, room)
                        }
                    }.onSuccess {
                        AppRefresh.onDataChanged(context)
                        Haptics.click(context)  // 保存成功触感
                        showSnackbar("已保存")
                    }.onFailure {
                        showSnackbar("保存失败：${it.message ?: "未知错误"}")
                    }
                }
            },
            onDelete = { entry ->
                scope.launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        scheduleRepo.deleteEntry(entry.entryId)
                    }
                    AppRefresh.onDataChanged(context)
                }
                Haptics.heavy(context)  // 删除课程强反馈
                showSnackbar("已删除本节")
            },
            onDismiss = { editingEntry = null },
        )
    }

    // ---- 新增课程弹窗 ----
    if (showAddCourse) {
        // 周次上限用设置的总周数；默认选中识别到的最长周（不超过总周数）
        val maxWeek = (entries.maxOfOrNull { e -> e.weeks.maxOrNull() ?: 0 } ?: 0)
            .coerceIn(1, settings.totalWeeks)
        com.buguake.timetable.ui.timetable.AddCourseSheet(
            maxWeek = maxWeek,
            prefillDay = addCoursePrefill?.first,
            prefillSection = addCoursePrefill?.second,
            onSave = { name, teacher, location, day, s, e, weeks ->
                scope.launch {
                    runCatching {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            scheduleRepo.addEntry(
                                settings.timetableId,
                                name, teacher,
                                campus = "", building = "", room = location,
                                day, s, e, weeks,
                            )
                        }
                    }.onSuccess {
                        AppRefresh.onDataChanged(context)
                        Haptics.click(context)  // 添加成功触感
                        showSnackbar("已添加：$name")
                    }.onFailure {
                        showSnackbar("添加失败：${it.message ?: "未知错误"}")
                    }
                }
                showAddCourse = false
                addCoursePrefill = null
            },
            onDismiss = { showAddCourse = false; addCoursePrefill = null },
        )
    }
    }  // ThemeRevealHost
    }
}

/**
 * 二级覆盖页容器：Expressive 转场（淡入 + 轻微上滑 + 缩放进入，快速淡出退场）。
 * 内容外罩全屏触摸拦截层——主界面已常驻组合（alpha 0），挡住穿透到课表格子的误触；
 * 拦截层随动画一同出现/消失，退场期间也不会漏点。
 */
@Composable
private fun OverlayPage(visible: Boolean, onBack: (() -> Unit)? = null, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        // 统一转场：深度缩放 + 淡切（进入迎面放大、退出缩回淡出，与各二/三级页一致）
        enter = com.buguake.timetable.ui.theme.pageEnterCloser(),
        exit = com.buguake.timetable.ui.theme.pageExitCloser(),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 拦截层必须垫在内容【下方】（兄弟节点而非父布局）：
            // 作为父布局会在主传递中先于页面滚动消费事件，导致二级页拖不动；
            // 作为下方兄弟，页面滚动手势优先命中，空白处的点击才落进拦截层，
            // 不会穿透到 alpha 0 的主界面
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { change ->
                                    if (change.pressed) change.consume()
                                }
                            }
                        }
                    }
            )
            com.buguake.timetable.ui.theme.SwipeBackBox(
                onBack = { onBack?.invoke() },
                enabled = visible && onBack != null,
            ) {
                content()
            }
        }
    }
}

/** 枚举桌面上的不挂科课表小组件实例（4×2 / 3×2 / 2×2）。 */
private fun queryWidgetInstances(context: Context): List<com.buguake.timetable.ui.timetable.WidgetInstanceInfo> {
    val mgr = android.appwidget.AppWidgetManager.getInstance(context)
    val large = android.content.ComponentName(context, ScheduleWidgetProvider::class.java)
    val medium = android.content.ComponentName(context, ScheduleWidgetMediumProvider::class.java)
    val compact = android.content.ComponentName(context, ScheduleWidgetCompactProvider::class.java)
    return mgr.getAppWidgetIds(large).map {
        com.buguake.timetable.ui.timetable.WidgetInstanceInfo(it, compact = false, sizeLabel = "4×2 今明双栏")
    } + mgr.getAppWidgetIds(medium).map {
        com.buguake.timetable.ui.timetable.WidgetInstanceInfo(it, compact = false, sizeLabel = "3×2 列表")
    } + mgr.getAppWidgetIds(compact).map {
        com.buguake.timetable.ui.timetable.WidgetInstanceInfo(it, compact = true, sizeLabel = "2×2 紧凑")
    }
}

/** collectAsState 初始值（真实默认值由 SettingsRepository 首帧后发出）。 */
private fun defaultSettings(): ScheduleSettings =
    ScheduleSettings(
        semesterStart = com.buguake.timetable.data.SettingsRepository.DEFAULT_SEMESTER_START_MILLIS,
        sectionsPerDay = 12,
        totalWeeks = com.buguake.timetable.data.SettingsRepository.DEFAULT_TOTAL_WEEKS,
        showWeekend = true,
        showNonCurrentWeek = false,
        showTeacherOnBlock = true,
        showLocationOnBlock = true,
        dynamicColor = false,
        darkMode = "system",
        sectionTimes = com.buguake.timetable.data.SettingsRepository.DEFAULT_SECTION_TIMES.take(12),
        remindEnabled = false,
        remindMinutesBefore = com.buguake.timetable.data.SettingsRepository.REMIND_MINUTES_DEFAULT,
        customBgEnabled = true,
        customBgPath = "",
        customBgBlurDp = com.buguake.timetable.data.SettingsRepository.CUSTOM_BG_BLUR_DEFAULT,
        showExamsOnHome = true,
        moveScope = com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_ASK,
)

/** 背景图解码：两次解码（先边界后位图）降采样至 ≤2048px，交给裁剪后再落盘。 */
private fun decodeBackgroundBitmap(
    cr: android.content.ContentResolver,
    uri: android.net.Uri,
): android.graphics.Bitmap {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
    return cr.openInputStream(uri)!!.use {
        android.graphics.BitmapFactory.decodeStream(
            it, null,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } ?: throw IllegalStateException("无法解码图片")
}
