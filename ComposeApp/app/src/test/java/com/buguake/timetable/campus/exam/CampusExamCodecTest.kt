package com.buguake.timetable.campus.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 考试缓存编解码：往返一致、脏数据不崩、无课程名的条目过滤。 */
class CampusExamCodecTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(y: Int, mo: Int, d: Int, h: Int): Long =
        LocalDateTime.of(y, mo, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun exam(name: String, startAt: Long, endAt: Long, withTime: Boolean = true) = CampusExam(
        courseName = name,
        courseCode = "08G0000",
        examName = "2025-2026-1学期期末考试",
        timeText = if (withTime) "2026-01-06(09:00-11:00)" else "待定",
        startAt = startAt,
        endAt = endAt,
        location = "翔宇楼503（智慧教室）",
        campus = "东校区",
        seat = "54",
        method = "笔试",
        teacher = "张某",
        credit = "5.0",
        termCode = "2025-3",
        termText = "2025-2026-1",
    )

    @Test
    fun `encode then decode keeps every field`() {
        val bundle = CampusExamBundle(
            updatedAt = 1_800_000_000_000L,
            exams = listOf(
                exam("高等数学A1", at(2026, 1, 6, 9), at(2026, 1, 6, 11)),
                exam("体育", 0L, 0L, withTime = false),
            ),
        )

        val decoded = CampusExamCodec.decode(CampusExamCodec.encode(bundle))

        assertEquals(bundle, decoded)
        assertEquals(2, decoded?.exams?.size)
        assertTrue(decoded!!.exams[1].startAt == 0L)
    }

    @Test
    fun `decode tolerates garbage and drops nameless entries`() {
        // 非 JSON：返回 null，调用方按"无缓存"处理
        assertNull(CampusExamCodec.decode("not json"))

        // "{}" 是合法 JSON：返回空 bundle（不崩），由上层按"无数据"走读取流程
        val empty = CampusExamCodec.decode("{}")
        assertEquals(0, empty?.exams?.size)
        assertEquals(0L, empty?.updatedAt)

        val withJunk = """{"v":1,"updatedAt":5,"exams":[{"course":""},{"course":"线性代数B","start":1}]}"""
        val decoded = CampusExamCodec.decode(withJunk)
        assertEquals(1, decoded?.exams?.size)
        assertEquals("线性代数B", decoded?.exams?.first()?.courseName)
        assertEquals(5L, decoded?.updatedAt)
    }
}
