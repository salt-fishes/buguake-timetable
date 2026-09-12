package com.buguake.timetable.campus.exam

import com.buguake.timetable.data.CalendarExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 考试 → 日历事件 / iCalendar 的映射（时间来自教务系统，不依赖开学日）。 */
class ExamCalendarTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun exam(seat: String = "54") = CampusExam(
        courseName = "高等数学A1",
        courseCode = "08G0000",
        examName = "2025-2026-1学期期末考试",
        timeText = "2026-01-06(09:00-11:00)",
        startAt = at(2026, 1, 6, 9, 0),
        endAt = at(2026, 1, 6, 11, 0),
        location = "翔宇楼503（智慧教室）",
        campus = "东校区",
        seat = seat,
        method = "笔试",
        teacher = "张某",
        credit = "5.0",
        termCode = "2025-3",
        termText = "2025-2026-1",
    )

    @Test
    fun `specs carry title location and seat`() {
        val specs = ExamCalendar.specs(listOf(exam()))

        assertEquals(1, specs.size)
        val s = specs.first()
        assertEquals(at(2026, 1, 6, 9, 0), s.startMillis)
        assertEquals(at(2026, 1, 6, 11, 0), s.endMillis)
        assertEquals("考试：高等数学A1", s.title)
        assertEquals("东校区 翔宇楼503（智慧教室）", s.location)
        assertTrue(s.description!!.contains("座位：54号"))
        assertTrue(s.description!!.contains("方式：笔试"))
    }

    @Test
    fun `entries without time are excluded from calendar export`() {
        val noTime = exam().copy(startAt = 0L, endAt = 0L, timeText = "待定")

        assertTrue(ExamCalendar.specs(listOf(noTime)).isEmpty())
        assertTrue(ExamCalendar.simpleEvents(listOf(noTime), 60).isEmpty())
        assertEquals(0, ExamCalendar.exportableCount(listOf(noTime)))
        assertEquals(1, ExamCalendar.exportableCount(listOf(noTime, exam())))
    }

    @Test
    fun `ics output uses Asia-Shanghai local time and includes reminder`() {
        val ics = CalendarExport.buildSimpleIcs(
            events = ExamCalendar.simpleEvents(listOf(exam()), remindMinutesBefore = 24 * 60),
            calendarName = "考试安排",
            now = LocalDateTime.of(2026, 1, 1, 0, 0),
        )

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260106T090000"))
        assertTrue(ics.contains("DTEND;TZID=Asia/Shanghai:20260106T110000"))
        assertTrue(ics.contains("SUMMARY:考试：高等数学A1"))
        assertTrue(ics.contains("BEGIN:VALARM"))
        assertTrue(ics.contains("TRIGGER:-PT1440M"))
    }

    @Test
    fun `stable uid makes re-export idempotent`() {
        val a = ExamCalendar.simpleEvents(listOf(exam()), 0).first().uid
        val b = ExamCalendar.simpleEvents(listOf(exam()), 60).first().uid
        val other = ExamCalendar.simpleEvents(listOf(exam(seat = "55")), 0).first().uid

        // UID 只由课程号+时间+考场决定：改提醒设置不会产生重复事件
        assertEquals(a, b)
        assertEquals(other, a)
        assertFalse(a.isBlank())
    }
}
