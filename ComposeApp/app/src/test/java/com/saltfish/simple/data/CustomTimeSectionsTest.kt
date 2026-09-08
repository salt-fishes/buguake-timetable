package com.saltfish.simple.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * 自定义时间段课次 → 节次映射与当日分钟区间。
 * 作息表采用常见四节制样例：1节 08:00-08:45，2节 08:55-09:40，
 * 3节 10:00-10:45，4节 10:55-11:40。
 */
class CustomTimeSectionsTest {

    private val times = listOf(
        SectionTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
        SectionTime(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
        SectionTime(3, LocalTime.of(10, 0), LocalTime.of(10, 45)),
        SectionTime(4, LocalTime.of(10, 55), LocalTime.of(11, 40)),
    )

    private fun entry(
        isCustomTime: Boolean = false,
        customStart: String = "",
        customEnd: String = "",
        startSection: Int? = 1,
        endSection: Int? = 1,
    ) = EntryWithCourse(
        entryId = 1, courseId = 1, courseName = "测试", type = "", credit = "",
        colorIndex = 0, dayOfWeek = 1, startSection = startSection, endSection = endSection,
        weeksCsv = "1", campus = "", building = "", room = "", teacher = "",
        isCustomTime = isCustomTime, customStartTime = customStart, customEndTime = customEnd,
    )

    @Test
    fun `整含单节 - 映射到该节`() {
        val r = EntryWithCourse.customTimeSections(8 * 60 + 5, 8 * 60 + 40, times)
        assertEquals(1..1, r)
    }

    @Test
    fun `跨越两节 - 映射到连续区间`() {
        // 08:30-09:10 与 1、2 两节均重叠
        val r = EntryWithCourse.customTimeSections(8 * 60 + 30, 9 * 60 + 10, times)
        assertEquals(1..2, r)
    }

    @Test
    fun `部分重叠首尾节 - 端点收敛到实际重叠节`() {
        // 08:40-08:58：与 1 节尾、2 节头重叠
        val r = EntryWithCourse.customTimeSections(8 * 60 + 40, 8 * 60 + 58, times)
        assertEquals(1..2, r)
    }

    @Test
    fun `跨越午休空档 - 区间包含中间节次`() {
        // 08:10-10:20：横跨 1、2、3 节（2、3 之间有空档也落入区间）
        val r = EntryWithCourse.customTimeSections(8 * 60 + 10, 10 * 60 + 20, times)
        assertEquals(1..3, r)
    }

    @Test
    fun `完全在网格前 - 无重叠返回 null`() {
        assertNull(EntryWithCourse.customTimeSections(6 * 60, 6 * 60 + 50, times))
    }

    @Test
    fun `完全在网格后 - 无重叠返回 null`() {
        assertNull(EntryWithCourse.customTimeSections(14 * 60, 15 * 60, times))
    }

    @Test
    fun `effectiveSections - 节次条目原样返回`() {
        val e = entry(startSection = 2, endSection = 4)
        assertEquals(2..4, e.effectiveSections(times))
    }

    @Test
    fun `effectiveSections - 自定义时间映射为重叠节次区间`() {
        val e = entry(isCustomTime = true, customStart = "08:10", customEnd = "10:20")
        assertEquals(1..3, e.effectiveSections(times))
    }

    @Test
    fun `effectiveSections - 无重叠返回 null`() {
        val e = entry(isCustomTime = true, customStart = "06:00", customEnd = "06:50")
        assertNull(e.effectiveSections(times))
    }

    @Test
    fun `withEffectiveSections - 保留自定义时间语义并替换节次`() {
        val e = entry(isCustomTime = true, customStart = "08:10", customEnd = "10:20")
        val copy = e.withEffectiveSections(times)!!
        assertEquals(1, copy.startSection)
        assertEquals(3, copy.endSection)
        assertEquals(true, copy.isCustomTime)
        assertEquals("08:10–10:20", copy.customTimeLabel)
    }

    @Test
    fun `minutesOfDay - 自定义时间直接解析不查作息表`() {
        val e = entry(isCustomTime = true, customStart = "06:00", customEnd = "12:00")
        assertEquals(6 * 60 to 12 * 60, e.minutesOfDay(times))
    }

    @Test
    fun `minutesOfDay - 节次条目按作息表并覆盖到结束节`() {
        val e = entry(startSection = 1, endSection = 2)
        assertEquals(8 * 60 to 9 * 60 + 40, e.minutesOfDay(times))
    }

    @Test
    fun `minutesOfDay - 非法时间格式返回 null`() {
        assertNull(entry(isCustomTime = true, customStart = "8点", customEnd = "09:00").minutesOfDay(times))
        assertNull(entry(isCustomTime = true, customStart = "25:00", customEnd = "26:00").minutesOfDay(times))
    }

    @Test
    fun `sectionRangeLabel - 自定义时间带前缀`() {
        val e = entry(isCustomTime = true, customStart = "06:00", customEnd = "12:00")
        assertEquals("自定义 06:00–12:00", e.sectionRangeLabel)
    }
}
