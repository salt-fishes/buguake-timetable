package com.buguake.timetable.ui.timetable

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buguake.timetable.ui.theme.AppMotion
import com.buguake.timetable.campus.ui.toTimetableEntry
import com.buguake.timetable.data.EntryWithCourse
import com.buguake.timetable.data.ScheduleRepository
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.SectionTime
import com.buguake.timetable.data.SettingsRepository
import com.buguake.timetable.data.TimeUtils
import com.buguake.timetable.data.WeekCalculator
import com.buguake.timetable.ui.theme.Haptics
import com.buguake.timetable.ui.theme.courseBlockColors
import com.buguake.timetable.ui.theme.courseBlockColorsDynamic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.LocalTime

internal val ROW_HEIGHT = 56.dp
internal val AXIS_WIDTH = 46.dp
private val WEEKDAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

// 翻页范围余量：HorizontalPager 需要有限页数，向前/向后放宽到实际用不到的边界，
// 视觉效果即「翻页不做限制」
private const val WEEK_PAGE_FLOOR = -260
private const val WEEK_PAGE_CEIL_EXTRA = 104

/** 课程类型 -> 表格标记符（与 PDF 图例一致）。 */
internal fun typeSymbol(type: String): String = when (type) {
    "讲课" -> "★"
    "实验" -> "○"
    "上机" -> "●"
    "实践" -> "◇"
    "集中实践" -> ":"
    else -> ""
}

/** 课表页：第 N 周标题 + 网格（可隐藏周末）+ 左右滑动切周。 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun TimetableScreen(
    entries: List<EntryWithCourse>,
    settings: ScheduleSettings,
    glass: Boolean = false,
    onCourseClick: (EntryWithCourse) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onImportClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onAddAt: (day: Int, section: Int) -> Unit = { _, _ -> },  // 长按网格空白处添加课程
    onMoveEntry: (EntryWithCourse, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ -> },
    timetables: List<TimetableInfo> = emptyList(),
    onSwitchTimetable: (Long) -> Unit = {},
    onNewTimetable: () -> Unit = {},   // 自动新建未命名课表并进入导入流程
    onOpenManage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val semesterStart = settings.semesterStartDate
    val context = androidx.compose.ui.platform.LocalContext.current
    // 教务系统读取的考试合并进课表网格：负数 id 与真实课程条目区分；
    // 设置里关掉「首页显示考试」则完全不加载
    val examBundle = remember { com.buguake.timetable.campus.CampusStore.getInstance(context).loadExams() }
    val examPairs = remember(examBundle, semesterStart, settings.showExamsOnHome) {
        if (semesterStart == null || !settings.showExamsOnHome) emptyList()
        else examBundle?.exams.orEmpty().filter { it.hasTime }.mapIndexed { i, e ->
            val week = com.buguake.timetable.campus.ui.weekIndex(
                com.buguake.timetable.campus.ui.dateOf(e), semesterStart,
            )
            e.toTimetableEntry(week, -(i.toLong() + 1)) to e
        }
    }
    val examEntries = examPairs.map { it.first }
    // 展示中的周是否包含今天
    fun weekContainsToday(w: Int): Boolean {
        val start = semesterStart?.let { WeekCalculator.mondayOfWeek(it, w) } ?: return false
        return !today.isBefore(start) && today.isBefore(start.plusDays(7))
    }
    val rawCurrentWeek = WeekCalculator.currentWeek(settings.semesterStartDate, today)
    val maxEntryWeek = maxOf(
        entries.maxOfOrNull { e -> e.weeks.maxOrNull() ?: 0 } ?: 0,
        examEntries.maxOfOrNull { e -> e.weeks.maxOrNull() ?: 0 } ?: 0,
    )
    // 翻页范围不做限制：HorizontalPager 需要有限页数，向前后各放宽足够大的余量
    // （往前约 5 年，往后在总周数基础上再加 2 年；有更早的考试周则再往左扩）
    val minExamWeek = examEntries.minOfOrNull { e -> e.weeks.minOrNull() ?: Int.MAX_VALUE } ?: Int.MAX_VALUE
    val minWeek = minOf(WEEK_PAGE_FLOOR, minExamWeek)
    val maxWeek = maxOf(settings.totalWeeks, maxEntryWeek, rawCurrentWeek) + WEEK_PAGE_CEIL_EXTRA

    val pageCount = maxWeek - minWeek + 1
    val pagerState = rememberPagerState(
        initialPage = (rawCurrentWeek - minWeek).coerceIn(0, pageCount - 1)
    ) { pageCount }
    // 切换课表后：按新课表的开学时间重新定位周次（开学前会落到 0/负周，同样随日期走）
    LaunchedEffect(settings.timetableId) {
        val w = rawCurrentWeek.coerceIn(minWeek, maxWeek)
        pagerState.scrollToPage(w - minWeek)
    }
    // 页码 → 周次：第 0 页对应 minWeek（可为很大的负数）
    val selectedWeek = pagerState.currentPage + minWeek
    // 翻页是否进行中：滑动过程中冻结「随周次切换的内容」（轴考试时间/网格外考试列表），
    // 落定后再切换，避免滑动中途内容高度变化引起上下抖动
    val paging by remember { androidx.compose.runtime.derivedStateOf { pagerState.currentPageOffsetFraction != 0f } }
    val settledWeekState = remember { mutableStateOf(selectedWeek) }
    LaunchedEffect(paging, selectedWeek) {
        if (!paging) settledWeekState.value = selectedWeek
    }
    val settledWeek = settledWeekState.value
    val scope = rememberCoroutineScope()
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var examDetail by remember { mutableStateOf<com.buguake.timetable.campus.exam.CampusExam?>(null) }
    // 动态取色开关：课表块颜色随壁纸主题联动
    val dynamicColor = settings.dynamicColor

    val visibleDays = remember(settings.showWeekend) {
        if (settings.showWeekend) (1..7).toList() else (1..5).toList()
    }
    // 一日节数由设置决定（作息页可调），网格按此渲染
    val maxSection = settings.sectionsPerDay

    LaunchedEffect(Unit) {
        if (!settings.showWeekend && today.dayOfWeek.value >= 6) {
            onShowSnackbar("今天是周末，已显示本周一")
        }
    }

    Column(modifier.fillMaxSize()) {
        // ---- 空状态：无课表数据时引导导入（首启引导） ----
        if (entries.isEmpty() && examEntries.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // 空态 stagger 入场：主标题 → 副标题 → 三步引导逐个浮现
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                androidx.compose.animation.AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(AppMotion.effects()) + slideInVertically(
                        initialOffsetY = { it / 12 },
                        animationSpec = AppMotion.spatial(),
                    ),
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        "欢迎使用不挂科课表",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "选择学校，登录教务，一键导入课表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))
                    // 三步引导：逐行 stagger 入场
                    com.buguake.timetable.ui.theme.StaggerIn(0) {
                        GuideStep(1, "在「我的」页选择你的学校", "教务网页导入能力开发中")
                    }
                    Spacer(Modifier.height(12.dp))
                    com.buguake.timetable.ui.theme.StaggerIn(1) {
                        GuideStep(2, "在内嵌浏览器登录教务系统", "账号密码只在浏览器会话内，App 不读取")
                    }
                    Spacer(Modifier.height(12.dp))
                    com.buguake.timetable.ui.theme.StaggerIn(2) {
                        GuideStep(3, "点击执行导入", "课程、周次、地点自动落位")
                    }
                    Spacer(Modifier.height(28.dp))
                    com.buguake.timetable.ui.theme.StaggerIn(3) {
                        androidx.compose.material3.Button(
                            onClick = onImportClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("导入课表")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    com.buguake.timetable.ui.theme.StaggerIn(4) {
                        Text(
                            "教务网页导入已上线，更多学校持续适配中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }
            return@Column
        }

        // ---- 标题栏：第 N 周大字 + 日期范围小字（玻璃模式包一层玻璃舱） ----
        val headerContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showWeekPicker = true }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    // 「第 N 周」同一行内联显示：三个 Text 若直接放进 Column 会竖排堆叠。
                    // 开学前周次可为 0/负（随真实日期自动定位），不再钳到第 1 周
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "第 ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        // 周数数字滚动切换（水平方向与翻页一致，Expressive 弹性规格）
                        androidx.compose.animation.AnimatedContent(
                            targetState = selectedWeek,
                            transitionSpec = {
                                val move = AppMotion.spatialFast<androidx.compose.ui.unit.IntOffset>()
                                if (targetState > initialState) {
                                    (slideInHorizontally(move) { it / 3 } +
                                        fadeIn(AppMotion.effectsFast()))
                                        .togetherWith(
                                            slideOutHorizontally(move) { -it / 3 } +
                                                fadeOut(AppMotion.effectsFast())
                                        )
                                } else {
                                    (slideInHorizontally(move) { -it / 3 } +
                                        fadeIn(AppMotion.effectsFast()))
                                        .togetherWith(
                                            slideOutHorizontally(move) { it / 3 } +
                                                fadeOut(AppMotion.effectsFast())
                                        )
                                }
                            },
                            label = "weekNumber",
                        ) { week ->
                            Text(
                                "$week",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            " 周",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // 副标题：开学前显示倒计时，其余显示该周日期范围
                    Text(
                        text = when {
                            semesterStart == null -> "未设置开学时间"
                            selectedWeek < 1 -> {
                                val daysToStart =
                                    java.time.temporal.ChronoUnit.DAYS.between(today, semesterStart)
                                if (daysToStart > 0) "距开学还有 $daysToStart 天" else "今天开学"
                            }
                            else -> weekDateRangeLabel(settings.semesterStartDate, selectedWeek)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                // 分享整周课表：离屏绘制 PNG 后调起系统分享
                IconButton(onClick = {
                    if (sharing) return@IconButton
                    val monday = settings.semesterStartDate?.let {
                        WeekCalculator.mondayOfWeek(it, selectedWeek)
                    }
                    if (monday == null) {
                        onShowSnackbar("请先在「我的」设置开学时间")
                        return@IconButton
                    }
                    sharing = true
                    scope.launch {
                        runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val bmp = TimetableShare.renderWeek(
                                    week = selectedWeek,
                                    monday = monday,
                                    entries = entries,
                                    visibleDays = visibleDays,
                                    maxSection = maxSection,
                                    showNonCurrentWeek = settings.showNonCurrentWeek,
                                    timetableName = settings.timetableName,
                                )
                                TimetableShare.share(context, bmp, selectedWeek)
                            }
                        }.onFailure {
                            onShowSnackbar("生成分享图失败：${it.message ?: "未知错误"}")
                        }
                        sharing = false
                    }
                }) {
                    // 图标 ↔ 加载圈交叉淡入，避免瞬切
                    androidx.compose.animation.Crossfade(
                        targetState = sharing,
                        animationSpec = AppMotion.effectsFast(),
                        label = "shareBusy",
                    ) { busy ->
                        if (busy) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "分享本周课表",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onAddClick) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新增课程",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    // 周数滑杆 + 课表切换面板（原"回到本周"职能并入面板）
                    showWeekPicker = true
                }) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = "周数与课表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 标题舱不再包容器背景：标题/图标直接放在磨砂背景之上（亮暗模式由主题色保证可读）
        headerContent()
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
                .copy(alpha = if (glass) 0.35f else 1f)
        )

        // ---- 星期表头（轴角落显示展示周的月份，随滑动切换） ----
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(
                Modifier.width(AXIS_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${cornerMonth(semesterStart, selectedWeek, today)}月",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            for (d in visibleDays) {
                DayHeader(
                    dayIndex = d,
                    date = semesterStart?.let {
                        WeekCalculator.mondayOfWeek(it, selectedWeek).plusDays((d - 1).toLong())
                    },
                    isToday = weekContainsToday(selectedWeek) && today.dayOfWeek.value == d,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 网格主体（外层统一纵向滚动） ----
        val gridHeight = maxSection * ROW_HEIGHT.value
        // 考试周自动调整：轴上被考试覆盖的节次行改显考试的真实时间区间
        val axisExamTimes = remember(examPairs, settledWeek, settings.sectionTimes) {
            buildMap {
                examPairs.forEach { (entry, _) ->
                    if (selectedWeek !in entry.weeks) return@forEach
                    val eff = entry.withEffectiveSections(settings.sectionTimes) ?: return@forEach
                    val st = entry.customStartTime.ifBlank { return@forEach }
                    val en = entry.customEndTime.ifBlank { return@forEach }
                    val s0 = eff.startSection ?: return@forEach
                    val e0 = eff.endSection ?: s0
                    for (sec in s0..e0) put(sec, st to en)
                }
            }
        }
        // 本周放不进网格的考试：真实时间与作息表任何一节都不重叠（如深夜场），
        // 网格里不渲染，列在网格下方避免丢数据（与考试安排列表互为补充）
        val offGridExams = remember(examPairs, settledWeek, settings.sectionTimes) {
            examPairs.mapNotNull { (entry, exam) ->
                if (settledWeek in entry.weeks &&
                    entry.withEffectiveSections(settings.sectionTimes) == null
                ) exam else null
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                SectionAxis(
                    sections = 1..maxSection,
                    sectionTimes = settings.sectionTimes,
                    overrideTimes = axisExamTimes,
                    modifier = Modifier
                        .width(AXIS_WIDTH)
                        .height(gridHeight.dp),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).height(gridHeight.dp),
                ) { page ->
                    val pageWeek = page + minWeek
                    WeekGridPage(
                        week = pageWeek,
                        allEntries = entries + examEntries,
                        visibleDays = visibleDays,
                        maxSection = maxSection,
                        isCurrentWeek = pageWeek == rawCurrentWeek && weekContainsToday(pageWeek),
                        today = today,
                        sectionTimes = settings.sectionTimes,
                        showNonCurrentWeek = settings.showNonCurrentWeek,
                        showTeacherOnBlock = settings.showTeacherOnBlock,
                        showLocationOnBlock = settings.showLocationOnBlock,
                        dynamicColor = dynamicColor,
                        glass = glass,
                    onCourseClick = { entry ->
                        // 考试块（负数 id）点击弹考试详情，不进课程详情
                        val exam = examPairs.firstOrNull { it.first.entryId == entry.entryId }?.second
                        if (exam != null) {
                            Haptics.tick(context)
                            examDetail = exam
                        } else onCourseClick(entry)
                    },
                        onMoveEntry = { entry, day, start, end, week ->
                            // 考试块不可拖动改时间，拖动只对真实课程条目生效
                            if (entry.entryId >= 0) onMoveEntry(entry, day, start, end, week)
                        },
                        onAddAt = onAddAt,
                    )
                }
            }

            // 网格外考试：沿用考试安排列表的卡片样式，点击同样弹考试详情；出现/消失平滑展开
            androidx.compose.animation.AnimatedVisibility(
                visible = offGridExams.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically(AppMotion.spatial()) +
                    fadeIn(AppMotion.effects()),
                exit = androidx.compose.animation.shrinkVertically(AppMotion.spatialFast()) +
                    fadeOut(AppMotion.effectsFast()),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        "不在作息表时间内的考试",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val now = System.currentTimeMillis()
                    offGridExams.forEach { e ->
                        com.buguake.timetable.campus.ui.ExamCard(e, past = e.hasTime && e.startAt < now)
                    }
                }
            }
        }
    }

    if (showWeekPicker) {
        ModalBottomSheet(onDismissRequest = { showWeekPicker = false }) {
            // ---- 周数：滑杆 + 回到本周 ----
            // 滑杆只在学期内取值（第 1 周 ~ 学期最后有课的周）；
            // 首页手势左右滑动不受此限制，仍可滑到学期外的 0/负周与未来周
            val sliderMin = 1f
            val sliderMax = maxOf(settings.totalWeeks, maxEntryWeek).toFloat()
            var panelWeek by remember(selectedWeek) {
                mutableStateOf(selectedWeek.coerceIn(1, maxOf(settings.totalWeeks, maxEntryWeek)).toFloat())
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("周数", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(rawCurrentWeek - minWeek) }
                    Haptics.tick(context)
                    showWeekPicker = false
                }) { Text("回到本周") }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Slider(
                    value = panelWeek,
                    onValueChange = { panelWeek = it },
                    onValueChangeFinished = {
                        Haptics.tick(context)  // 周次落定轻震
                        scope.launch { pagerState.animateScrollToPage(panelWeek.toInt() - minWeek) }
                    },
                    valueRange = sliderMin..sliderMax,
                    steps = (maxOf(settings.totalWeeks, maxEntryWeek) - 2).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${panelWeek.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            // ---- 课表：卡片切换 + 新建/管理 ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("课表", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    showWeekPicker = false
                    onNewTimetable()
                }) { Text("新建课表") }
                TextButton(onClick = {
                    showWeekPicker = false
                    onOpenManage()
                }) { Text("管理") }
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                items(timetables, key = { it.timetable.id }) { info ->
                    TimetableCard(
                        info = info,
                        active = info.timetable.id == settings.timetableId,
                        maxSection = maxSection,
                        onClick = {
                            onSwitchTimetable(info.timetable.id)
                            showWeekPicker = false
                        },
                    )
                }
            }
        }
    }

    // 首页网格中的考试块：点击弹考试详情（与考试安排页同一套弹窗）
    examDetail?.let { e ->
        com.buguake.timetable.campus.ui.ExamDetailDialog(e) { examDetail = null }
    }
}

/** 课表切换卡片：迷你课表缩略图（异步渲染）+ 名称 + 选中勾。 */
@Composable
private fun TimetableCard(
    info: TimetableInfo,
    active: Boolean,
    maxSection: Int,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 缩略图按课表内容异步渲染（与分享图同源绘制，等比缩小）
    val thumb by produceState<android.graphics.Bitmap?>(
        null, info.timetable.id, maxSection,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val entries = ScheduleRepository.getInstance(context).entriesOf(info.timetable.id)
                val tt = info.timetable
                val today = LocalDate.now()
                val startMillis = if (tt.startMillis == 0L) SettingsRepository.DEFAULT_SEMESTER_START_MILLIS else tt.startMillis
                val startDate = java.time.Instant.ofEpochMilli(startMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                val week = WeekCalculator.currentWeek(startDate, today).coerceAtLeast(1)
                val bmp = TimetableShare.renderWeek(
                    week = week,
                    monday = WeekCalculator.mondayOfWeek(startDate, week),
                    entries = entries,
                    visibleDays = (1..7).toList(),
                    maxSection = maxSection,
                    showNonCurrentWeek = true,
                )
                Bitmap.createScaledBitmap(bmp, 220, 264, true)
                    .also { scaled -> if (scaled != bmp) bmp.recycle() }
            }.getOrNull()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 选中态容器色平滑过渡
        val containerColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            animationSpec = AppMotion.effects(),
            label = "ttCardBg",
        )
        Box(
            Modifier
                .size(width = 110.dp, height = 132.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // 缩略图异步渲染完成后交叉淡入，不再瞬现
            androidx.compose.animation.Crossfade(
                targetState = thumb,
                animationSpec = AppMotion.effects(),
                label = "ttThumb",
            ) { bmp ->
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
            }
            // 「使用中」勾选：弹性弹入
            val checkScale = com.buguake.timetable.ui.theme.rememberPopScale(active)
            if (active && checkScale > 0.01f) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "使用中",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                        .background(Color(0x66000000), RoundedCornerShape(50))
                        .padding(4.dp),
                )
            }
        }
        Text(
            info.timetable.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun DayHeader(
    dayIndex: Int,
    date: LocalDate?,
    isToday: Boolean,
    modifier: Modifier = Modifier,
) {
    // 「今天」标识：圆圈弹入 + 文字颜色平滑过渡（翻页到今天所在周时有生命感）
    val cs = MaterialTheme.colorScheme
    val circleScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isToday) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        ),
        label = "todayCircle",
    )
    val weekdayColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isToday) cs.primary else cs.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "todayWeekday",
    )
    val dayColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isToday) cs.onPrimary else cs.onSurfaceVariant,
        animationSpec = AppMotion.effects(),
        label = "todayDay",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            WEEKDAY_NAMES[dayIndex - 1],
            style = MaterialTheme.typography.labelMedium,
            color = weekdayColor,
        )
        Spacer(Modifier.height(2.dp))
        // 固定 28dp 高度：圆圈只在内部缩放出现，行高恒定——
        // 否则滑到含今天的周时表头行高突变，整个网格上下抖动
        Box(contentAlignment = Alignment.Center, modifier = Modifier.requiredSize(28.dp)) {
            if (circleScale > 0.01f) {
                Box(
                    Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = circleScale
                            scaleY = circleScale
                        }
                        .background(cs.primary, CircleShape)
                )
            }
            Text(
                text = date?.dayOfMonth?.toString() ?: "",
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = dayColor,
            )
        }
    }
}

@Composable
internal fun SectionAxis(
    sections: IntRange,
    sectionTimes: List<SectionTime>,
    modifier: Modifier = Modifier,
    // 考试周自动调整：被考试覆盖的节次行改显考试的真实时间区间（"HH:mm" to "HH:mm"）
    overrideTimes: Map<Int, Pair<String, String>> = emptyMap(),
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        for (s in sections) {
            val t = sectionTimes.firstOrNull { it.section == s }
            val ov = overrideTimes[s]
            Column(
                modifier = Modifier.height(ROW_HEIGHT),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    s.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // 考试周自动调整：常规作息 ↔ 考试真实时间，文字交叉淡换
                androidx.compose.animation.AnimatedContent(
                    targetState = ov,
                    transitionSpec = {
                        (fadeIn(AppMotion.effectsFast()) togetherWith fadeOut(AppMotion.effectsFast()))
                    },
                    label = "axisTime",
                ) { o ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            o != null -> {
                                Text(
                                    o.first,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    o.second,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            t != null -> {
                                Text(
                                    TimeUtils.hm(t.start),
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    TimeUtils.hm(t.end),
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeekGridPage(
    week: Int,
    allEntries: List<EntryWithCourse>,
    visibleDays: List<Int>,
    maxSection: Int,
    isCurrentWeek: Boolean,
    today: LocalDate,
    sectionTimes: List<SectionTime>,
    showNonCurrentWeek: Boolean,
    showTeacherOnBlock: Boolean,
    showLocationOnBlock: Boolean,
    dynamicColor: Boolean,
    glass: Boolean,
    onCourseClick: (EntryWithCourse) -> Unit,
    onMoveEntry: (EntryWithCourse, Int, Int, Int, Int) -> Unit,
    onAddAt: (day: Int, section: Int) -> Unit = { _, _ -> },  // 长按空白格添加课程
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }
    // 自定义时间段课次按作息表映射为重叠节次区间参与网格布局；
    // 完全落在网格外（无重叠）的条目不进网格，仅出现在今日页/详情
    val layoutEntries = remember(allEntries, sectionTimes) {
        allEntries.mapNotNull { it.withEffectiveSections(sectionTimes) }
    }
    // 拖拽状态：grab=手指抓取点（块内偏移），pointerLocal=手指当前块内位置（均为块局部像素）
    var drag by remember { mutableStateOf<GridDrag?>(null) }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    // 刚落位的块：唯一弹入一次（拖拽回弹动效）
    var bounceEntryId by remember { mutableStateOf<Long?>(null) }

    fun handleDragEnd() {
        val d = drag
        drag = null
        if (d == null || gridSize.width <= 0 || visibleDays.isEmpty()) return
        // 幽灵块左上角（网格像素坐标）
        val topLeft = d.ghostTopLeft()
        val colW = gridSize.width.toFloat() / visibleDays.size
        val start0 = d.entry.startSection ?: 1
        val end0 = d.entry.endSection ?: start0
        val dur = end0 - start0
        // 目标列按块中心 x；目标起始节按块顶 y 取整
        val dayIdx = ((topLeft.x + d.widthPx / 2) / colW).toInt()
            .coerceIn(0, visibleDays.size - 1)
        val newDay = visibleDays[dayIdx]
        var newStart = (topLeft.y / rowHeightPx).roundToInt() + 1
        newStart = newStart.coerceIn(1, (maxSection - dur).coerceAtLeast(1))
        // 冲突避让：与本周同天其他课程重叠时，向上/向下找最近空位
        fun conflicts(s: Int): Boolean = layoutEntries.any {
            it.entryId != d.entry.entryId && it.dayOfWeek == newDay && it.isInWeek(week) &&
                (it.startSection ?: 1) <= s + dur && (it.endSection ?: it.startSection ?: 1) >= s
        }
        val finalStart = if (!conflicts(newStart)) newStart else {
            var found = -1
            for (off in 1 until maxSection) {
                val up = newStart - off
                if (up >= 1 && !conflicts(up)) { found = up; break }
                val down = newStart + off
                if (down + dur <= maxSection && !conflicts(down)) { found = down; break }
            }
            found
        }
        if (finalStart >= 1) {
            bounceEntryId = d.entry.entryId  // 落位后目标块弹入一次
            onMoveEntry(d.entry, newDay, finalStart, finalStart + dur, week)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                gridCoords = it
                gridSize = it.size
            }
            // 长按空白格：按落点换算星期与节次，交给上层打开添加课程（预填时间）
            .pointerInput(visibleDays, maxSection) {
                detectTapGestures(onLongPress = { offset ->
                    if (visibleDays.isEmpty() || gridSize.width <= 0) return@detectTapGestures
                    val colW = gridSize.width.toFloat() / visibleDays.size
                    val dayIdx = (offset.x / colW).toInt().coerceIn(0, visibleDays.size - 1)
                    val section = (offset.y / rowHeightPx).toInt().coerceIn(0, maxSection - 1)
                    onAddAt(visibleDays[dayIdx], section + 1)
                })
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            for (d in visibleDays) {
                val dayEntries = layoutEntries.filter { it.dayOfWeek == d }
                DayColumn(
                    dayEntries = dayEntries,
                    week = week,
                    maxSection = maxSection,
                    isToday = isCurrentWeek && today.dayOfWeek.value == d,
                    sectionTimes = sectionTimes,
                    showNonCurrentWeek = showNonCurrentWeek,
                    showTeacherOnBlock = showTeacherOnBlock,
                    showLocationOnBlock = showLocationOnBlock,
                    dynamicColor = dynamicColor,
                    glass = glass,
                    draggedEntryId = drag?.entry?.entryId,
                    bounceEntryId = bounceEntryId,
                    onDragStart = { entry, grab, blockCoords, sizePx ->
                        // 自定义时间段课次不可拖拽：拖拽落位写入的是节次坐标，会破坏真实时间语义
                        if (!entry.isCustomTime) {
                            // 块在网格内的位置用 localPositionOf 直接换算，
                            // 不经窗口坐标（窗口坐标不含链上 offset，会跳到列顶）
                            val grid = gridCoords
                            val originInGrid = if (grid != null && blockCoords.isAttached) {
                                grid.localPositionOf(blockCoords, Offset.Zero)
                            } else Offset.Zero
                            drag = GridDrag(
                                entry,
                                originInGrid,
                                grab,
                                grab,
                                sizePx.width.toFloat(),
                                sizePx.height.toFloat(),
                            )
                        }
                    },
                    onDragDelta = { pointerLocal ->
                        // 手指位置为块内绝对坐标，逐帧替换而非累计增量，保证严格跟手
                        drag?.let { drag = it.copy(pointerLocal = pointerLocal) }
                    },
                    onDragEnd = { handleDragEnd() },
                    onCourseClick = onCourseClick,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // 拖拽幽灵块：跟随手指浮于网格之上
        drag?.let { d ->
            DragGhost(
                drag = d,
                dynamicColor = dynamicColor,
            )
        }
    }
}

/** 拖拽中的课程块（位置均为「网格」像素坐标系，不经窗口坐标换算）。 */
private data class GridDrag(
    val entry: EntryWithCourse,
    val blockOrigin: Offset,   // 拖起时块在网格中的位置
    val grab: Offset,          // 手指抓取点（块内偏移）
    val pointerLocal: Offset,  // 手指当前在块内的位置（每帧绝对替换）
    val widthPx: Float,
    val heightPx: Float,
) {
    /** 幽灵块左上角（网格 px）：保持抓取点相对块的位置不变。 */
    fun ghostTopLeft(): Offset = blockOrigin + pointerLocal - grab
}

@Composable
private fun DragGhost(
    drag: GridDrag,
    dynamicColor: Boolean,
) {
    val (container, onContainer) = if (dynamicColor) {
        courseBlockColorsDynamic(drag.entry.colorIndex)
    } else {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        courseBlockColors(drag.entry.colorIndex, isDark)
    }
    // 幽灵块严格跟手（逐帧绝对替换）；仅出现时轻微弹入做质感
    val target = drag.ghostTopLeft()
    val appear = remember { androidx.compose.animation.core.Animatable(0.92f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            1f,
            androidx.compose.animation.core.spring(
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            ),
        )
    }
    Box(
        Modifier
            .absoluteOffset { IntOffset(target.x.roundToInt(), target.y.roundToInt()) }
            .graphicsLayer {
                scaleX = appear.value
                scaleY = appear.value
            }
            .size(with(LocalDensity.current) { drag.widthPx.toDp() }, with(LocalDensity.current) { drag.heightPx.toDp() })
            .shadow(8.dp, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(container, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = drag.entry.courseName + typeSymbol(drag.entry.type),
            color = onContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayColumn(
    dayEntries: List<EntryWithCourse>,
    week: Int,
    maxSection: Int,
    isToday: Boolean,
    sectionTimes: List<SectionTime>,
    showNonCurrentWeek: Boolean,
    showTeacherOnBlock: Boolean,
    showLocationOnBlock: Boolean,
    dynamicColor: Boolean,
    glass: Boolean,
    draggedEntryId: Long?,
    bounceEntryId: Long?,
    onDragStart: (EntryWithCourse, Offset, LayoutCoordinates, IntSize) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onCourseClick: (EntryWithCourse) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val colWidth = maxWidth

        // 节次背景分隔线
        Column(Modifier.fillMaxSize()) {
            repeat(maxSection) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .padding(top = 0.5.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }

        // 课程块（先过滤掉不显示的，再冲突分槽，避免隐藏条目占槽位）
        val visibleEntries = remember(dayEntries, week, showNonCurrentWeek) {
            if (showNonCurrentWeek) dayEntries
            else dayEntries.filter { it.isInWeek(week) }
        }
        val clusters = remember(visibleEntries) { clusterByOverlap(visibleEntries) }
        for (cluster in clusters) {
            val slotCount = cluster.maxOf { it.second } + 1
            val cellW = colWidth / slotCount
            for ((entry, slot) in cluster) {
                // 块的布局坐标（拖拽起点换算用；localPositionOf 需要完整链坐标）
                var blockCoords by remember(entry.entryId) { mutableStateOf<LayoutCoordinates?>(null) }
                CourseBlock(
                    entry = entry,
                    showTeacher = showTeacherOnBlock,
                    showLocation = showLocationOnBlock,
                    dimmed = !entry.isInWeek(week),
                    isDragging = draggedEntryId == entry.entryId,
                    bounce = bounceEntryId == entry.entryId,
                    dynamicColor = dynamicColor,
                    glass = glass,
                    onDragStart = { grab ->
                        blockCoords?.let {
                            onDragStart(
                                entry, grab, it,
                                IntSize(it.size.width, it.size.height),
                            )
                        }
                    },
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd,
                    // 四周留距：块与块/网格线之间保留 2dp 间隙
                    modifier = Modifier
                        .offset(x = cellW * slot + 2.dp, y = blockTop(entry) + 3.dp)
                        .width(cellW - 4.dp)
                        .height(blockHeight(entry) - 6.dp)
                        .onGloballyPositioned { blockCoords = it },
                    onClick = { onCourseClick(entry) },
                )
            }
        }

        // 当前时间指示线（仅今日列）
        if (isToday) {
            NowIndicator(
                sectionTimes = sectionTimes,
                maxSection = maxSection,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

private fun blockTop(e: EntryWithCourse) =
    ((((e.startSection ?: 1) - 1) * ROW_HEIGHT.value) + 1f).dp

private fun blockHeight(e: EntryWithCourse) =
    (((e.endSection ?: e.startSection ?: 1) - (e.startSection ?: 1) + 1) * ROW_HEIGHT.value - 2f).dp

/** 重叠课程聚类：返回若干簇，每簇为 (entry, 槽位)。 */
private fun clusterByOverlap(
    entries: List<EntryWithCourse>
): List<List<Pair<EntryWithCourse, Int>>> {
    val sorted = entries.sortedBy { it.startSection ?: 99 }
    val clusters = mutableListOf<MutableList<EntryWithCourse>>()
    for (e in sorted) {
        val c = clusters.lastOrNull()
        if (c != null && c.any { it.overlaps(e) }) c.add(e) else clusters.add(mutableListOf(e))
    }
    return clusters.map { members ->
        val placed = mutableListOf<Pair<EntryWithCourse, Int>>()
        for (e in members.sortedBy { it.startSection ?: 99 }) {
            var slot = 0
            while (placed.any { (other, s) -> s == slot && other.overlaps(e) }) slot++
            placed.add(e to slot)
        }
        placed
    }
}

@Composable
private fun CourseBlock(
    entry: EntryWithCourse,
    showTeacher: Boolean = true,
    showLocation: Boolean = true,
    dimmed: Boolean,
    dynamicColor: Boolean,
    glass: Boolean,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    bounce: Boolean = false,
    onDragStart: ((Offset) -> Unit)? = null,
    onDragDelta: ((Offset) -> Unit)? = null,   // 参数 = 手指在本块内的位置（绝对坐标）
    onDragEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 动态取色开启：从主题派生三组容器色；关闭：十组品牌色（自动适配亮暗主题）
    val (container, onContainer) = if (dynamicColor) {
        courseBlockColorsDynamic(entry.colorIndex)
    } else {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        courseBlockColors(entry.colorIndex, isDark)
    }
    // 课程名按块宽自适应：保证每行约显示三个字（参考主流课表排版）
    // 按压缩放动效（MD3：0.97，弹簧回弹）
    var pressed by remember(entry.entryId) { mutableStateOf(false) }
    val pressScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed || isDragging) 0.97f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        ),
        label = "blockPress",
    )
    val pressModifier = Modifier.graphicsLayer {
        scaleX = pressScale
        scaleY = pressScale
    }
    // 拖拽中原块 / 非本周淡化：透明度平滑过渡
    val blockAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 0.25f else if (dimmed) 0.38f else 1f,
        animationSpec = AppMotion.effects(),
        label = "blockAlpha",
    )
    // 拖拽落位回弹：仅拖拽落地的目标块弹入一次（0.92→1）
    val bounceScale = remember(entry.entryId) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(bounce) {
        if (bounce) {
            bounceScale.snapTo(0.92f)
            bounceScale.animateTo(
                1f,
                androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                ),
            )
        }
    }
    val scaleModifier = Modifier.graphicsLayer {
        val s = pressScale * bounceScale.value
        scaleX = s
        scaleY = s
    }
    // 长按拖拽换位置（与单击手势独立：短按点击、长按拖起）
    val hapticContext = androidx.compose.ui.platform.LocalContext.current
    val dragModifier = if (onDragStart != null && onDragDelta != null) {
        Modifier.pointerInput(entry.entryId) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    Haptics.tick(hapticContext)  // 课程拖起触感
                    onDragStart?.invoke(it)
                },
                onDrag = { change, _ ->
                    change.consume()
                    // 绝对坐标：每次上报手指在块内的位置，避免增量累计漂移
                    onDragDelta?.invoke(change.position)
                },
                onDragEnd = { onDragEnd?.invoke() },
                onDragCancel = { onDragEnd?.invoke() },
            )
        }
    } else Modifier
    val gestureModifier = Modifier.pointerInput(entry.entryId) {
        detectTapGestures(
            onPress = {
                pressed = true
                try { awaitRelease() } finally { pressed = false }
            },
            onTap = { onClick() },
        )
    }
    if (glass) {
        // 磨砂玻璃模式：块体为玻璃面，顶部 4dp 课程色条做区分，文字用主题色保证可读；
        // 字号随块宽自适应（一行约 3 字，与实色模式一致）
        val cs = MaterialTheme.colorScheme
        BoxWithConstraints(
            modifier = modifier
                .alpha(blockAlpha)
                .then(scaleModifier)
                .then(dragModifier)
        ) {
            // 一行约 3 字：按去掉内边距后的可用宽度计算（CJK 全角 ≈ 字号）
            val nameSize = (((maxWidth.value - 8f) / 3f).coerceIn(8f, 14f))
            com.buguake.timetable.ui.theme.GlassSurface(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .then(gestureModifier)
                        .padding(horizontal = 3.dp, vertical = 4.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .background(container)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.courseName + typeSymbol(entry.type),
                        fontSize = nameSize.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                        lineHeight = (nameSize * 1.22f).sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 6,
                    )
                    val location = condensedLocation(entry)
                    val t = if (showTeacher) entry.teacher else ""
                    val l = if (showLocation) location else ""
                    if (t.isNotBlank() || l.isNotBlank()) {
                        Text(
                            text = buildString {
                                if (t.isNotBlank()) append(t)
                                if (l.isNotBlank()) append(if (t.isNotBlank()) "@" else "").append(l)
                            },
                            fontSize = (nameSize * 0.82f).sp,
                            color = cs.onSurfaceVariant,
                            lineHeight = (nameSize * 0.98f).sp,
                        )
                    }
                }
            }
        }
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .alpha(blockAlpha)
            .then(scaleModifier)
            .then(dragModifier)
            .border(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f),
                        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.40f),
                    )
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            )
            .background(container, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clipToBounds()
            .then(gestureModifier)
            .padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        // 一行约 3 字：按去掉内边距后的可用宽度计算（CJK 全角 ≈ 字号）
        val nameSize = (((maxWidth.value - 8f) / 3f).coerceIn(8f, 14f))
        Column {
            // 课程名 + 类型标记（如 模拟电子线路★）
            Text(
                text = entry.courseName + typeSymbol(entry.type),
                fontSize = nameSize.sp,
                fontWeight = FontWeight.Medium,
                color = onContainer,
                lineHeight = (nameSize * 1.22f).sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 6,
            )
            Spacer(Modifier.height(2.dp))
            // 教师@地点（换行自然铺满块高）
            val location = condensedLocation(entry)
            val t = if (showTeacher) entry.teacher else ""
            val l = if (showLocation) location else ""
            if (t.isNotBlank() || l.isNotBlank()) {
                Text(
                    text = buildString {
                        if (t.isNotBlank()) append(t)
                        if (l.isNotBlank()) append(if (t.isNotBlank()) "@" else "").append(l)
                    },
                    fontSize = (nameSize * 0.82f).sp,
                    color = onContainer.copy(alpha = 0.85f),
                    lineHeight = (nameSize * 0.98f).sp,
                )
            }
        }
    }
}

/** 压缩地点：楼号与场地重复时去重（环宇楼 + 环宇楼A404 → 环宇楼A404）；未排地点只留原文。 */
private fun condensedLocation(e: EntryWithCourse): String {
    if (e.room.isBlank() || e.room == "未排地点") return e.room.ifBlank { e.building.ifBlank { e.campus } }
    val buildingPart = if (!e.room.contains(e.building)) e.building else ""
    return listOf(buildingPart, e.room).filter { it.isNotBlank() }.joinToString("")
}

/** 当前时间红线（今日列）：每分钟自动更新，位置平滑游走。 */
@Composable
private fun NowIndicator(
    sectionTimes: List<SectionTime>,
    maxSection: Int,
    modifier: Modifier = Modifier,
) {
    val now = com.buguake.timetable.ui.theme.rememberNowMinute()
    val nowMinutes = now.hour * 60 + now.minute
    val first = sectionTimes.firstOrNull() ?: return
    val last = sectionTimes.lastOrNull() ?: return
    val firstM = first.start.hour * 60 + first.start.minute
    val lastM = last.end.hour * 60 + last.end.minute
    if (nowMinutes < firstM || nowMinutes > lastM) return

    var yRatio = 0f
    for (i in sectionTimes.indices) {
        val st = sectionTimes[i]
        if (st.section > maxSection) break
        val sM = st.start.hour * 60 + st.start.minute
        val eM = st.end.hour * 60 + st.end.minute
        if (nowMinutes <= eM) {
            yRatio = if (nowMinutes >= sM) {
                (st.section - 1) + (nowMinutes - sM).toFloat() / (eM - sM).toFloat()
            } else {
                (st.section - 1).toFloat()
            }
            break
        }
    }
    // 分钟跳变时平滑游走，不再瞬移
    val animatedRatio by androidx.compose.animation.core.animateFloatAsState(
        targetValue = yRatio.coerceIn(0f, maxSection.toFloat()),
        animationSpec = AppMotion.spatialFast(),
        label = "nowIndicatorY",
    )
    val y = (animatedRatio * ROW_HEIGHT.value).dp
    Box(
        modifier
            .fillMaxWidth()
            .offset(y = y)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .offset(x = (-4).dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
    }
}

private fun weekDateRangeLabel(start: LocalDate?, week: Int): String {
    start ?: return ""
    val mon = WeekCalculator.mondayOfWeek(start, week)
    val sun = mon.plusDays(6)
    return "${mon.monthValue}月${mon.dayOfMonth}日 到 ${sun.monthValue}月${sun.dayOfMonth}日"
}

/** 轴角落月份：跟随展示中的周（开学前回退到今天所在月）。 */
private fun cornerMonth(start: LocalDate?, week: Int, today: LocalDate): Int =
    start?.let { WeekCalculator.mondayOfWeek(it, week).monthValue } ?: today.monthValue

/** 首启引导步骤行：编号圆点 + 标题/说明。 */
@Composable
private fun GuideStep(number: Int, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
