package com.saltfish.simple.webimport

import com.saltfish.simple.data.EntryWithCourse
import com.saltfish.simple.data.SectionTime
import com.saltfish.simple.schedule.ParsedCourse
import com.saltfish.simple.schedule.ParsedEntry
import com.saltfish.simple.schedule.ParsedSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 桥 JSON → 简课表内部模型的转换层。
 *
 * 字段契约与拾光课程表 `CourseImportExport.kt` 一致（JSON 字段名逐字对齐）：
 * 课程 {name, teacher, position, day, startSection?, endSection?, weeks[],
 * isCustomTime, customStartTime?, customEndTime?, color?, remark?}；
 * 配置 {semesterStartDate?, semesterTotalWeeks=20, defaultClassDuration=45,
 * defaultBreakDuration=10, firstDayOfWeek=1}；节次 {number, startTime, endTime, alias?}。
 * color 字段忽略（本项目按课程名稳定哈希分配色板）。
 */
object WebImportConverter {

    // ---- 课程 ----

    /**
     * 桥 saveImportedCourses 的课程 JSON → [ParsedSchedule]。
     *
     * 自定义时间段课次（isCustomTime=true）：真实时间保留在条目上，同时按
     * [sectionTimes] 把时间重叠节次写入 start/endSection 供网格落位；
     * 完全在网格外的课次 startSection 为 null（仅今日页/详情可见，不丢课）。
     * 无法解析的条目跳过并计入 [ConvertReport.skipped]。
     */
    fun convertCourses(
        coursesJsonString: String,
        sectionTimes: List<SectionTime>,
    ): Result<Pair<ParsedSchedule, ConvertReport>> = runCatching {
        val arr = JSONArray(coursesJsonString)
        val courses = LinkedHashMap<String, ParsedCourse>()
        val entriesByCourse = LinkedHashMap<String, MutableList<ParsedEntry>>()
        val report = ConvertReport()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { report.skipped++; continue }
            val name = o.optString("name", "").trim()
            if (name.isEmpty()) { report.skipped++; continue }
            val day = o.optInt("day", -1)
            if (day !in 1..7) { report.skipped++; continue }
            val weeks = o.optJSONArray("weeks")?.let { w ->
                (0 until w.length()).mapNotNull { j -> w.optInt(j, -1).takeIf { it > 0 } }
            } ?: emptyList()
            if (weeks.isEmpty()) { report.skipped++; continue }

            val isCustom = o.optBoolean("isCustomTime", false)
            var startSection: Int? = o.optInt("startSection", -1).takeIf { it > 0 }
            var endSection: Int? = o.optInt("endSection", -1).takeIf { it > 0 }
            var customStart = ""
            var customEnd = ""
            if (isCustom) {
                customStart = o.optString("customStartTime", "").trim()
                customEnd = o.optString("customEndTime", "").trim()
                val sm = parseHm(customStart)
                val em = parseHm(customEnd)
                if (sm == null || em == null) {
                    report.skipped++; continue
                }
                // 时间重叠节次 → 网格落位区间；无重叠则保持在网格外
                val mapped = EntryWithCourse.customTimeSections(sm, em, sectionTimes)
                startSection = mapped?.first
                endSection = mapped?.last
            } else if (startSection == null) {
                // 非自定义课次必须有节次
                report.skipped++; continue
            }

            val teacher = o.optString("teacher", "").trim()
            val remark = o.optString("remark", "").trim()
            if (name !in courses) {
                courses[name] = ParsedCourse(
                    name = name, type = "", credit = "",
                    teacher = teacher, campus = "", building = "", room = "",
                    classNo = "", composition = "",
                    remark = remark.take(300),
                )
                entriesByCourse[name] = mutableListOf()
            } else if (remark.isNotBlank() && courses[name]!!.remark.isBlank()) {
                // 同名课多条目：取首条非空备注
                courses[name] = courses[name]!!.copy(remark = remark.take(300))
            }
            entriesByCourse.getValue(name).add(
                ParsedEntry(
                    course = name, dayOfWeek = day,
                    startSection = startSection, endSection = endSection,
                    weeks = weeks.distinct().sorted(),
                    room = o.optString("position", "").trim(),
                    teacher = teacher,
                    isCustomTime = isCustom,
                    customStartTime = customStart, customEndTime = customEnd,
                )
            )
        }
        if (courses.isEmpty()) throw IOException("未解析到有效课程（共 ${arr.length()} 条）")
        report.imported = courses.size
        report.totalReceived = arr.length()
        ParsedSchedule(
            courses = courses.values.toList(),
            entries = entriesByCourse.values.flatten(),
        ) to report
    }

    // ---- 配置 ----

    data class CourseConfig(
        val semesterStartDate: LocalDate?,
        val semesterTotalWeeks: Int?,
    )

    fun parseCourseConfig(configJsonString: String): Result<CourseConfig> = runCatching {
        val o = JSONObject(configJsonString)
        val dateStr = o.optString("semesterStartDate", "").trim()
        CourseConfig(
            semesterStartDate = dateStr.takeIf { it.isNotBlank() }?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
                    ?: throw IOException("开学日期格式无效：$it（应为 yyyy-MM-dd）")
            },
            semesterTotalWeeks = if (o.has("semesterTotalWeeks")) o.getInt("semesterTotalWeeks") else null,
        )
    }

    // ---- 节次时间表 ----

    /** 桥 savePresetTimeSlots → SectionTime 列表（无效行过滤；空结果视为错误）。 */
    fun parseTimeSlots(timeSlotsJsonString: String): Result<List<SectionTime>> = runCatching {
        val arr = JSONArray(timeSlotsJsonString)
        val slots = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val number = o.optInt("number", -1)
            if (number <= 0) return@mapNotNull null
            val start = parseHm(o.optString("startTime", "")) ?: return@mapNotNull null
            val end = parseHm(o.optString("endTime", "")) ?: return@mapNotNull null
            SectionTime(
                section = number,
                start = LocalTime.of(start / 60, start % 60),
                end = LocalTime.of(end / 60, end % 60),
            )
        }.distinctBy { it.section }.sortedBy { it.section }
        if (slots.isEmpty()) throw IOException("节次时间表为空或全部无效（需 number/startTime/endTime 字段）")
        slots
    }

    /** "HH:mm" → 当日分钟；非法返回 null。 */
    fun parseHm(text: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mm = m.groupValues[2].toInt()
        if (h !in 0..23 || mm !in 0..59) return null
        return h * 60 + mm
    }

    /** LocalDate → 第 1 周周一 00:00 的 epoch 毫秒（开学日所在 ISO 周的周一，对齐 WeekCalculator 语义）。 */
    fun semesterStartMillis(date: LocalDate): Long =
        com.saltfish.simple.data.WeekCalculator.mondayOfWeek(date, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** 转换汇总（提示用户用）。 */
data class ConvertReport(
    var totalReceived: Int = 0,
    var imported: Int = 0,
    var skipped: Int = 0,
)
