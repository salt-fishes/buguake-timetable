package com.buguake.timetable.campus.exam

import com.buguake.timetable.data.CalendarExport
import com.buguake.timetable.data.CalendarSync
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 考试 → 系统日历事件 / iCalendar 的映射。
 *
 * 时间直接来自教务系统给出的日期与时段（不依赖开学日/周次），
 * 因此与课表导出不同：一场考试就是一个事件，无需逐周展开。
 */
object ExamCalendar {

    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 系统日历事件（写入「不挂科考试」专属日历）。 */
    fun specs(exams: List<CampusExam>): List<CalendarSync.EventSpec> =
        exams.asSequence()
            .filter { it.hasTime }
            .sortedBy { it.startAt }
            .map { e ->
                CalendarSync.EventSpec(
                    startMillis = e.startAt,
                    endMillis = e.endAt,
                    title = "考试：" + e.courseName,
                    location = locationOf(e),
                    description = describe(e),
                )
            }
            .toList()

    /** iCalendar 事件（.ics 文件导出；[remindMinutesBefore] > 0 时带提醒）。 */
    fun simpleEvents(
        exams: List<CampusExam>,
        remindMinutesBefore: Int,
    ): List<CalendarExport.SimpleEvent> =
        exams.asSequence()
            .filter { it.hasTime }
            .sortedBy { it.startAt }
            .map { e ->
                CalendarExport.SimpleEvent(
                    uid = uid(e),
                    start = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.startAt), ZONE),
                    end = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.endAt), ZONE),
                    summary = "考试：" + e.courseName,
                    location = locationOf(e),
                    description = describe(e),
                    remindMinutesBefore = remindMinutesBefore,
                )
            }
            .toList()

    /** 无时间的记录无法进日历，导出前过滤掉（UI 会提示条数）。 */
    fun exportableCount(exams: List<CampusExam>): Int = exams.count { it.hasTime }

    private fun locationOf(e: CampusExam): String? =
        listOf(e.campus, e.location).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }

    private fun describe(e: CampusExam): String? = buildString {
        if (e.seat.isNotBlank()) append("座位：${e.seat}号")
        if (e.method.isNotBlank()) append(if (isEmpty()) "" else "；").append("方式：${e.method}")
        if (e.teacher.isNotBlank()) append(if (isEmpty()) "" else "；").append("教师：${e.teacher}")
        if (e.termText.isNotBlank()) append(if (isEmpty()) "" else "；").append(e.termText)
        if (e.examName.isNotBlank()) append(if (isEmpty()) "" else "；").append(e.examName)
    }.ifBlank { null }

    /** UID 稳定：同一场考试重复导出/同步天然幂等去重。 */
    private fun uid(e: CampusExam): String {
        val key = (e.courseCode + "|" + e.timeText + "|" + e.location).hashCode()
        return "jkb-exam-" + Integer.toHexString(key) + "@jiankebiao"
    }
}
