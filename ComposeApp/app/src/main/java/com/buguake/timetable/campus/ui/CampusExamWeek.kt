package com.buguake.timetable.campus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.exam.CampusExam
import com.buguake.timetable.data.EntryWithCourse
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.WeekCalculator
import com.buguake.timetable.ui.timetable.AXIS_WIDTH
import com.buguake.timetable.ui.timetable.DayHeader
import com.buguake.timetable.ui.timetable.ROW_HEIGHT
import com.buguake.timetable.ui.timetable.SectionAxis
import com.buguake.timetable.ui.timetable.WeekGridPage
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private val FMT_MD: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

/**
 * 周次视图：**直接套用课表界面的网格**（同一个 [WeekGridPage] + [SectionAxis] + [DayHeader]）。
 *
 * 做法：把每场考试转成"自定义时间段"的课表条目（`isCustomTime=true`），
 * 由 `withEffectiveSections` 按作息表映射到节次区间，于是复用课表的落位、配色、
 * 点击详情与重叠聚类逻辑，不需要另写一套网格。
 *
 * 周次像日历一样**只列出有考试的周**，点击即切换到那一周（考试大多在过去，
 * 所以默认落到离当前周最近的有考试的周）。
 */
@Composable
fun ExamWeekView(
    exams: List<CampusExam>,
    settings: ScheduleSettings,
    glass: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val semesterStart = settings.semesterStartDate
    if (semesterStart == null) {
        Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            InfoCard(
                title = "周次视图需要开学时间",
                body = "请在课表设置里设置开学时间，周次才能与课表对齐；也可以先用「列表」视图查看考试。",
            )
        }
        return
    }

    val withTime = remember(exams) { exams.filter { it.hasTime } }
    val weekOfExam = remember(withTime, semesterStart) {
        withTime.associateWith { weekIndex(dateOf(it), semesterStart) }
    }
    val weeks = remember(weekOfExam) { weekOfExam.values.toSortedSet().toList() }
    val currentWeek = WeekCalculator.currentWeek(semesterStart)
    var selected by remember(semesterStart, weeks) {
        mutableStateOf(weeks.minByOrNull { abs(it - currentWeek) } ?: currentWeek)
    }
    var detail by remember { mutableStateOf<CampusExam?>(null) }

    if (weeks.isEmpty()) {
        Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            InfoCard(
                title = "没有可显示的考试",
                body = "已缓存的考试都缺少时间信息，或还没有读取考试安排。",
            )
        }
        return
    }

    val weekExams = withTime.filter { weekOfExam[it] == selected }
    // 考试 → 课表条目（自定义时间），id 用于点击详情回查
    val entries = remember(weekExams, selected) {
        weekExams.mapIndexed { i, e -> e.toTimetableEntry(selected, i.toLong() + 1) }
    }
    val examById = remember(entries, weekExams) {
        entries.mapIndexed { i, en -> en.entryId to weekExams[i] }.toMap()
    }
    val inGrid = remember(entries, settings.sectionTimes) {
        entries.mapNotNull { it.withEffectiveSections(settings.sectionTimes) }
    }
    val offGrid = entries.filter { en -> inGrid.none { it.entryId == en.entryId } }
    val visibleDays = if (settings.showWeekend) (1..7).toList() else (1..5).toList()
    val maxSection = maxOf(settings.sectionsPerDay, settings.sectionTimes.size, 1)
    val monday = WeekCalculator.mondayOfWeek(semesterStart, selected)
    val today = LocalDate.now()
    val isCurrentWeek = selected == currentWeek

    Column(modifier.fillMaxSize()) {
        // 周次：只列有考试的周，并标出各周场次
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(weeks, key = { it }) { w ->
                val count = weekOfExam.count { it.value == w }
                FilterChip(
                    selected = w == selected,
                    onClick = { selected = w },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (w in 1..settings.totalWeeks) "第 $w 周"
                                else FMT_MD.format(WeekCalculator.mondayOfWeek(semesterStart, w)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text("$count 场", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${FMT_MD.format(monday)} - ${FMT_MD.format(monday.plusDays(6))} · ${weekExams.size} 场考试",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (!isCurrentWeek) {
                TextButton(onClick = { selected = currentWeek }) { Text("回到本周") }
            }
            TextButton(
                onClick = {
                    weeks.minByOrNull { abs(it - currentWeek) }?.let { selected = it }
                },
            ) { Text("最近的考试") }
        }

        // ---- 星期表头（与课表一致：轴角落显示月份）----
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(Modifier.width(AXIS_WIDTH), contentAlignment = Alignment.Center) {
                Text(
                    "${monday.monthValue}月",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            visibleDays.forEach { d ->
                DayHeader(
                    dayIndex = d,
                    date = monday.plusDays((d - 1).toLong()),
                    isToday = isCurrentWeek && today.dayOfWeek.value == d,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 网格主体 + 网格外考试：同一个纵向滚动区（行数多时可上下滑动）----
        val gridHeight = maxSection * ROW_HEIGHT.value
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.fillMaxWidth()) {
                SectionAxis(
                    sections = 1..maxSection,
                    sectionTimes = settings.sectionTimes,
                    modifier = Modifier
                        .width(AXIS_WIDTH)
                        .height(gridHeight.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(gridHeight.dp),
                ) {
                    WeekGridPage(
                        week = selected,
                        allEntries = inGrid,
                        visibleDays = visibleDays,
                        maxSection = maxSection,
                        isCurrentWeek = isCurrentWeek,
                        today = today,
                        sectionTimes = settings.sectionTimes,
                        showNonCurrentWeek = false,
                        showTeacherOnBlock = true,
                        showLocationOnBlock = true,
                        dynamicColor = settings.dynamicColor,
                        glass = glass,
                        onCourseClick = { entry -> examById[entry.entryId]?.let { detail = it } },
                        onMoveEntry = { _, _, _, _, _ -> },
                    )
                }
            }

            // 作息表之外的时间（如晚上的考试）进不了网格，单独列出，避免丢数据
            if (offGrid.isNotEmpty()) {
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
                    offGrid.mapNotNull { examById[it.entryId] }.forEach { ExamCard(it) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    detail?.let { e ->
        ExamDetailDialog(e) { detail = null }
    }
}

/** 考试详情（点击网格中的块；首页课表合并展示的考试块也复用）。 */
@Composable
internal fun ExamDetailDialog(e: CampusExam, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.courseName, textAlign = TextAlign.Start) },
        text = {
            Column {
                DetailRow("考试", e.examName)
                DetailRow("时间", examDateText(e) + " " + examTimeText(e))
                DetailRow("考场", listOf(e.campus, e.location).filter { it.isNotBlank() }.joinToString(" "))
                DetailRow("座位", if (e.seat.isNotBlank()) "${e.seat} 号" else "")
                DetailRow("方式", e.method)
                DetailRow("教师", e.teacher)
                DetailRow("学分", e.credit)
                DetailRow("学期", e.termText)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 考试 → 课表条目：自定义时间段 + 只属于该周，其余交给课表自己的映射逻辑。
 *  id 用负数（首页课表合并展示时借此与真实课程条目区分，避免与数据库自增 id 冲突）。
 *  块内副标题不显示教师，改为「学分 · 座位号」+ 换行的地点（完整信息看详情弹窗/列表）。 */
internal fun CampusExam.toTimetableEntry(week: Int, id: Long): EntryWithCourse {
    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), ZONE_EXAM)
    val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endAt), ZONE_EXAM)
    fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)
    val seatLine = listOfNotNull(
        credit.takeIf { it.isNotBlank() }?.let { "$it 学分" },
        seat.takeIf { it.isNotBlank() }?.let { "$it 号" },
    ).joinToString(" · ")
    return EntryWithCourse(
        entryId = id,
        courseId = id,
        courseName = courseName,
        type = "",
        credit = credit,
        colorIndex = abs((courseCode + timeText).hashCode()) % 3,  // 课表调色板只有 0..2
        dayOfWeek = start.dayOfWeek.value,
        startSection = null,
        endSection = null,
        weeksCsv = week.toString(),
        campus = "",
        building = "",
        room = "",
        // 地点放在 teacher 文本里用换行与座位号分行（块内地点行取 room 字段，这里置空）
        teacher = listOf(seatLine, location).filter { it.isNotBlank() }.joinToString("\n"),
        isCustomTime = true,
        customStartTime = hm(start),
        customEndTime = hm(end),
        remark = "",
    )
}

internal fun dateOf(e: CampusExam): LocalDate =
    LocalDate.ofInstant(Instant.ofEpochMilli(e.startAt), ZONE_EXAM)

/** 某天属于第几周（以第一周周一为基准，容忍开学日不是周一）。 */
internal fun weekIndex(date: LocalDate, semesterStart: LocalDate): Int {
    val startMonday = semesterStart.with(DayOfWeek.MONDAY)
    val days = ChronoUnit.DAYS.between(startMonday, date.with(DayOfWeek.MONDAY))
    return (days / 7).toInt() + 1
}
