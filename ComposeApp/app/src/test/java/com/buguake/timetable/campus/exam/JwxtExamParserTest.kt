package com.buguake.timetable.campus.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 正方 V9 考试查询响应解析。夹具字段取自中国计量大学教务系统真实响应（已脱敏姓名/学号）。
 */
class JwxtExamParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    /** 与教务系统返回结构一致：items + totalResult，字段名逐字对齐。 */
    private fun payload(items: String) =
        """{"currentPage":1,"items":[$items],"limit":15,"showCount":100,"totalCount":2,"totalResult":2}"""

    private val item1 = """
        {"kcmc":"高等数学A1","kch":"08G0000","ksmc":"2025-2026-1学期期末考试",
         "kssj":"2026-01-06(09:00-11:00)","cdmc":"翔宇楼503（智慧教室）","cdxqmc":"东校区",
         "zwh":"54","ksfs":"笔试","jsxx":"18A0803134/张某;00A0104036/李某","xf":"5.0",
         "xnm":"2025","xqm":"3","xnmc":"2025-2026","xqmmc":"1","xh":"2500404134"}
    """.trimIndent()

    private val item2 = """
        {"kcmc":"大学英语5","kch":"11G0007","ksmc":"2025-2026-2学期期末考试",
         "kssj":"2026-07-07(14:00-16:00)","cdmc":"翔宇楼403（智慧教室）","cdxqmc":"东校区",
         "zwh":"39","ksfs":"笔试","jsxx":"06B1203073/王某","xf":"4.0",
         "xnm":"2025","xqm":"12","xnmc":"2025-2026","xqmmc":"2","xh":"2500404134"}
    """.trimIndent()

    @Test
    fun `parses real-shaped payload into campus exams`() {
        val (exams, report) = JwxtExamParser.parse(payload("$item1,$item2")).getOrThrow()

        assertEquals(2, exams.size)
        assertEquals(2, report.parsed)
        assertEquals(0, report.withoutTime)
        assertEquals(0, report.skipped)

        val math = exams.first()
        assertEquals("高等数学A1", math.courseName)
        assertEquals("08G0000", math.courseCode)
        assertEquals("2025-2026-1学期期末考试", math.examName)
        assertEquals("2026-01-06(09:00-11:00)", math.timeText)
        assertEquals("翔宇楼503（智慧教室）", math.location)
        assertEquals("东校区", math.campus)
        assertEquals("54", math.seat)
        assertEquals("笔试", math.method)
        assertEquals("张某、李某", math.teacher)
        assertEquals("5.0", math.credit)
        assertEquals("2025-3", math.termCode)
        assertEquals("2025-2026-1", math.termText)
        assertTrue(math.hasTime)
        assertEquals(millis(2026, 1, 6, 9, 0), math.startAt)
        assertEquals(millis(2026, 1, 6, 11, 0), math.endAt)
    }

    @Test
    fun `sorts by start time ascending`() {
        val (exams, _) = JwxtExamParser.parse(payload("$item2,$item1")).getOrThrow()
        assertEquals(listOf("高等数学A1", "大学英语5"), exams.map { it.courseName })
    }

    @Test
    fun `keeps entry with unparsable time and reports it`() {
        val odd = """{"kcmc":"体育","kssj":"待定","cdmc":"体育馆","zwh":"","jsxx":"","xf":"1.0"}"""
        val (exams, report) = JwxtExamParser.parse(payload(odd)).getOrThrow()

        assertEquals(1, exams.size)
        assertEquals(1, report.withoutTime)
        assertTrue(!exams.first().hasTime)
        assertEquals(0L, exams.first().startAt)
        assertEquals("待定", exams.first().timeText)
    }

    @Test
    fun `drops entries without course name and counts them`() {
        val broken = """{"kcmc":"","kssj":"2026-01-06(09:00-11:00)"}"""
        val (exams, report) = JwxtExamParser.parse(payload(broken)).getOrThrow()

        assertTrue(exams.isEmpty())
        assertEquals(1, report.skipped)
    }

    @Test
    fun `fails when response is not the exam payload`() {
        // 登录页/错误页会走到这里：必须失败，让上层提示"请先登录"
        assertTrue(JwxtExamParser.parse("<html>用户登录</html>").isFailure)
        assertTrue(JwxtExamParser.parse("""{"foo":1}""").isFailure)
    }

    @Test
    fun `parseTerms maps semester codes to chinese`() {
        val json = """
            {"ok":true,"kind":"terms",
             "years":[{"code":"2025","label":"2025-2026"},{"code":"2024","label":"2024-2025"}],
             "semesters":[{"code":"3","label":"1"},{"code":"12","label":"2"},{"code":"16","label":"3"}],
             "currentYear":"2025","currentSemester":"16"}
        """.trimIndent()

        val t = JwxtExamParser.parseTerms(json).getOrThrow()

        assertEquals(listOf("2025-2026", "2024-2025"), t.years.map { it.label })
        assertEquals(listOf("第一学期", "第二学期", "第三学期"), t.semesters.map { it.label })
        assertEquals("2025", t.currentYear)
        assertEquals("16", t.currentSemester)
    }

    @Test
    fun `parseTerms drops blank codes and rejects garbage`() {
        assertTrue(JwxtExamParser.parseTerms("not json").isFailure)

        val t = JwxtExamParser.parseTerms(
            """{"years":[{"code":"","label":"x"},{"code":"2024","label":"2024-2025"}]}"""
        ).getOrThrow()

        assertEquals(1, t.years.size)
        assertEquals("2024", t.years.first().code)
        assertTrue(t.semesters.isEmpty())
    }

    @Test
    fun `parseTime accepts full width parens and rejects garbage`() {
        assertEquals(
            millis(2026, 7, 14, 9, 0) to millis(2026, 7, 14, 11, 0),
            JwxtExamParser.parseTime("2026-07-14（09:00-11:00）"),
        )
        assertNull(JwxtExamParser.parseTime(""))
        assertNull(JwxtExamParser.parseTime("第17周"))
    }

    @Test
    fun `cleanTeachers keeps single name and dedupes`() {
        assertEquals("王某", JwxtExamParser.cleanTeachers("06B1203073/王某"))
        assertEquals("王某", JwxtExamParser.cleanTeachers("王某"))
        assertEquals("张某、李某", JwxtExamParser.cleanTeachers("A/张某;B/李某;A/张某"))
        assertEquals("", JwxtExamParser.cleanTeachers(" ; "))
    }
}
