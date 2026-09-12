package com.buguake.timetable.campus.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 周次视图的周序号换算：以第一周周一为基准，且容忍开学日不是周一。 */
class ExamWeekIndexTest {

    @Test
    fun `monday semester start maps first week`() {
        val start = LocalDate.of(2026, 9, 7)  // 周一
        assertEquals(1, weekIndex(start, start))
        assertEquals(1, weekIndex(start.plusDays(6), start))
        assertEquals(2, weekIndex(start.plusDays(7), start))
        assertEquals(17, weekIndex(start.plusWeeks(16), start))
    }

    @Test
    fun `non monday semester start still aligns to that week`() {
        val start = LocalDate.of(2026, 9, 10)  // 周四开学
        assertEquals(1, weekIndex(LocalDate.of(2026, 9, 7), start))
        assertEquals(1, weekIndex(LocalDate.of(2026, 9, 13), start))
        assertEquals(2, weekIndex(LocalDate.of(2026, 9, 14), start))
    }

    @Test
    fun `dates before semester start give non positive week`() {
        val start = LocalDate.of(2026, 9, 7)
        assertEquals(0, weekIndex(start.minusDays(1), start))
        assertEquals(-1, weekIndex(start.minusDays(8), start))
    }
}
