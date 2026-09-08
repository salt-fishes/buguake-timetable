package com.buguake.timetable.webimport

import com.buguake.timetable.data.SectionTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** 桥 JSON → 内部模型转换（课程/自定义时间/配置/节次）。 */
class WebImportConverterTest {

    private val sectionTimes = listOf(
        SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        SectionTime(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
        SectionTime(3, LocalTime.of(10, 0), LocalTime.of(10, 45)),
        SectionTime(4, LocalTime.of(10, 55), LocalTime.of(11, 40)),
    )

    // ---- convertCourses ----

    @Test
    fun `普通课程 - 字段映射与同名分组`() {
        val json = """
        [
          {"name":"高等数学","teacher":"张三","position":"教一 301","day":1,
           "startSection":1,"endSection":2,"weeks":[1,2,3]},
          {"name":"高等数学","teacher":"张三","position":"教一 302","day":3,
           "startSection":3,"endSection":3,"weeks":[1,2,3]},
          {"name":"大学英语","teacher":"李四","position":"文B 202","day":2,
           "startSection":3,"endSection":4,"weeks":[1,2,3,4]}
        ]
        """.trimIndent()
        val (parsed, report) = WebImportConverter.convertCourses(json, sectionTimes).getOrThrow()

        assertEquals(2, report.imported)
        assertEquals(0, report.skipped)
        assertEquals(2, parsed.courses.size)
        val math = parsed.courses.first { it.name == "高等数学" }
        assertEquals("张三", math.teacher)
        val mathEntries = parsed.entries.filter { it.course == "高等数学" }
        assertEquals(2, mathEntries.size)
        assertEquals("教一 301", mathEntries[0].room)
        assertEquals("教一 302", mathEntries[1].room)
        assertEquals(listOf(1, 2, 3), mathEntries[0].weeks)
    }

    @Test
    fun `自定义时间课次 - 映射重叠节次并保留真实时间`() {
        val json = """
        [{"name":"晨间实验","teacher":"","position":"","day":2,
          "weeks":[1],"isCustomTime":true,"customStartTime":"08:10","customEndTime":"10:20",
          "remark":"需预习"}]
        """.trimIndent()
        val (parsed, _) = WebImportConverter.convertCourses(json, sectionTimes).getOrThrow()
        val e = parsed.entries.single()
        assertEquals(1, e.startSection)
        assertEquals(3, e.endSection)
        assertTrue(e.isCustomTime)
        assertEquals("08:10", e.customStartTime)
        assertEquals("需预习", parsed.courses.single().remark)
    }

    @Test
    fun `自定义时间完全在网格外 - 节次为 null 但不丢课`() {
        val json = """
        [{"name":"晨训","teacher":"","position":"","day":1,
          "weeks":[1],"isCustomTime":true,"customStartTime":"06:00","customEndTime":"06:50"}]
        """.trimIndent()
        val (parsed, _) = WebImportConverter.convertCourses(json, sectionTimes).getOrThrow()
        val e = parsed.entries.single()
        assertNull(e.startSection)
        assertNull(e.endSection)
        assertEquals(1, parsed.courses.size)
    }

    @Test
    fun `无效条目 - 逐条跳过并计数`() {
        val json = """
        [
          {"name":"","teacher":"","position":"","day":1,"startSection":1,"weeks":[1]},
          {"name":"坏星期","teacher":"","position":"","day":9,"startSection":1,"weeks":[1]},
          {"name":"坏时间","teacher":"","position":"","day":1,
           "weeks":[1],"isCustomTime":true,"customStartTime":"8点","customEndTime":"09:00"},
          {"name":"好课","teacher":"","position":"","day":1,"startSection":1,"endSection":1,"weeks":[1]}
        ]
        """.trimIndent()
        val (parsed, report) = WebImportConverter.convertCourses(json, sectionTimes).getOrThrow()
        assertEquals(4, report.totalReceived)
        assertEquals(1, report.imported)
        assertEquals(3, report.skipped)
        assertEquals(listOf("好课"), parsed.courses.map { it.name })
    }

    @Test
    fun `空课程数组 - 报错而非空课表`() {
        val result = WebImportConverter.convertCourses("[]", sectionTimes)
        assertTrue(result.isFailure)
    }

    @Test
    fun `备注超长截断到 300 字`() {
        val json = """[{"name":"课","teacher":"","position":"","day":1,"startSection":1,
            "endSection":1,"weeks":[1],"remark":"${"甲".repeat(400)}"}]"""
        val (parsed, _) = WebImportConverter.convertCourses(json, sectionTimes).getOrThrow()
        assertEquals(300, parsed.courses.single().remark.length)
    }

    // ---- parseCourseConfig ----

    @Test
    fun `配置解析 - 开学日与总周数`() {
        val cfg = WebImportConverter.parseCourseConfig(
            """{"semesterStartDate":"2026-09-07","semesterTotalWeeks":18}"""
        ).getOrThrow()
        assertEquals(LocalDate.of(2026, 9, 7), cfg.semesterStartDate)
        assertEquals(18, cfg.semesterTotalWeeks)
    }

    @Test
    fun `配置解析 - 坏日期报错`() {
        val result = WebImportConverter.parseCourseConfig("""{"semesterStartDate":"明年开学"}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun `开学日转第 1 周周一 - 非周一开学归到所在周`() {
        // 2026-09-09 是周三 → 第 1 周周一为 2026-09-07
        val millis = WebImportConverter.semesterStartMillis(LocalDate.of(2026, 9, 9))
        val expected = LocalDate.of(2026, 9, 7).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        assertEquals(expected, millis)
    }

    // ---- parseTimeSlots ----

    @Test
    fun `节次表 - 有效行解析并按节次排序`() {
        val slots = WebImportConverter.parseTimeSlots(
            """[{"number":2,"startTime":"08:55","endTime":"09:40"},
               {"number":1,"startTime":"08:00","endTime":"08:45"},
               {"number":0,"startTime":"x","endTime":"y"}]"""
        ).getOrThrow()
        assertEquals(2, slots.size)
        assertEquals(1, slots[0].section)
        assertEquals(LocalTime.of(8, 55), slots[1].start)
    }

    @Test
    fun `节次表 - 全部无效报错`() {
        assertTrue(WebImportConverter.parseTimeSlots("""[{"foo":1}]""").isFailure)
    }

    @Test
    fun `HH MM 解析 - 边界与非法`() {
        assertEquals(0, WebImportConverter.parseHm("00:00"))
        assertEquals(23 * 60 + 59, WebImportConverter.parseHm("23:59"))
        assertNull(WebImportConverter.parseHm("24:00"))
        assertNull(WebImportConverter.parseHm("8:0"))
        assertNull(WebImportConverter.parseHm(""))
    }
}
