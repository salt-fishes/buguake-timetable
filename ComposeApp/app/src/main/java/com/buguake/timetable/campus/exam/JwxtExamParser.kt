package com.buguake.timetable.campus.exam

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 正方教务 V9「学生考试信息查询」响应解析（中国计量大学实测通过）。
 *
 * 抓取路径（App 内 WebView 已登录后注入脚本执行）：
 * - 入口 gnmkdm = `N358105`，页面 `/kwgl/kscx_cxXsksxxIndex.html`
 * - 查询 `POST /kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105`
 * - 响应 `{"items":[...],"totalResult":n}`，单条 37 字段
 *
 * 纯函数、无 Android 依赖，可直接 JVM 单测（见 JwxtExamParserTest）。
 */
object JwxtExamParser {

    /** 教务时间串形如「2026-01-06(09:00-11:00)」，括号有全/半角两种。 */
    private val TIME_RE =
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})\s*[(（](\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})[)）]""")

    private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    data class Report(
        val total: Int,
        val parsed: Int,
        /** 时间串无法解析（保留条目、归入「时间待定」）。 */
        val withoutTime: Int,
        /** 无课程名等无效条目，已丢弃。 */
        val skipped: Int,
    )

    /** 解析失败（非 JSON / 无 items）返回 failure，调用方据此提示"请先登录"。 */
    fun parse(responseJson: String): Result<Pair<List<CampusExam>, Report>> = runCatching {
        val root = JSONObject(responseJson)
        val arr = root.optJSONArray("items")
            ?: throw IllegalArgumentException("响应缺少 items 字段（多半是尚未登录）")
        val exams = ArrayList<CampusExam>(arr.length())
        var withoutTime = 0
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            val course = o.optString("kcmc").trim()
            if (course.isEmpty()) {
                skipped++
                continue
            }
            val timeText = o.optString("kssj").trim()
            val span = parseTime(timeText)
            if (span == null) withoutTime++
            exams += CampusExam(
                courseName = course,
                courseCode = o.optString("kch").trim(),
                examName = o.optString("ksmc").trim(),
                timeText = timeText,
                startAt = span?.first ?: 0L,
                endAt = span?.second ?: 0L,
                location = o.optString("cdmc").trim(),
                campus = o.optString("cdxqmc").trim().ifEmpty { o.optString("xqmc").trim() },
                seat = o.optString("zwh").trim(),
                method = o.optString("ksfs").trim(),
                teacher = cleanTeachers(o.optString("jsxx")),
                credit = o.optString("xf").trim(),
                termCode = termCode(o.optString("xnm").trim(), o.optString("xqm").trim()),
                termText = termText(o.optString("xnmc").trim(), o.optString("xqmmc").trim()),
            )
        }
        val sorted = exams.sortedWith(compareBy({ !it.hasTime }, { it.startAt }))
        sorted to Report(
            total = arr.length(),
            parsed = sorted.size,
            withoutTime = withoutTime,
            skipped = skipped,
        )
    }

    /** 「2026-01-06(09:00-11:00)」→ (startMillis, endMillis)；无法解析返回 null。 */
    fun parseTime(text: String): Pair<Long, Long>? {
        val m = TIME_RE.find(text) ?: return null
        val g = m.groupValues
        return runCatching {
            val y = g[1].toInt()
            val mo = g[2].toInt()
            val d = g[3].toInt()
            val start = LocalDateTime.of(y, mo, d, g[4].toInt(), g[5].toInt())
            val rawEnd = LocalDateTime.of(y, mo, d, g[6].toInt(), g[7].toInt())
            // 结束早于开始（跨零点/脏数据）时按 2 小时兜底，与课表导出的兜底策略一致
            val end = if (rawEnd.isAfter(start)) rawEnd else start.plusHours(2)
            start.atZone(ZONE).toInstant().toEpochMilli() to end.atZone(ZONE).toInstant().toEpochMilli()
        }.getOrNull()
    }

    // ---- 学年/学期选项（页面下拉）----

    /** 一个学年或学期选项。 */
    data class TermOption(val code: String, val label: String)

    /**
     * 从页面下拉读到的学期选项。
     * 学年 label 形如「2025-2026」；学期 code 为教务编码（3/12/16），label 已转成中文。
     */
    data class TermOptions(
        val years: List<TermOption>,
        val semesters: List<TermOption>,
        val currentYear: String,
        val currentSemester: String,
    )

    /** 解析抓取脚本回传的学期选项 JSON。 */
    fun parseTerms(termsJson: String): Result<TermOptions> = runCatching {
        val o = JSONObject(termsJson)
        TermOptions(
            years = toOptions(o.optJSONArray("years")),
            semesters = toOptions(o.optJSONArray("semesters"))
                .map { TermOption(it.code, semesterLabel(it.code, it.label)) },
            currentYear = o.optString("currentYear").trim(),
            currentSemester = o.optString("currentSemester").trim(),
        )
    }

    private fun toOptions(arr: JSONArray?): List<TermOption> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val j = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = j.optString("code").trim()
            if (code.isEmpty()) return@mapNotNull null
            TermOption(code, j.optString("label").trim())
        }
    }

    /** 学期码 → 中文：3=第一学期，12=第二学期，16=第三学期；未知码回落原始标签。 */
    fun semesterLabel(code: String, raw: String): String = when (code) {
        "3" -> "第一学期"
        "12" -> "第二学期"
        "16" -> "第三学期"
        else -> raw.ifBlank { code }
    }

    /** 「01A0702020/何涵;19A0705118/王丽丽」→「何涵、王丽丽」。 */
    fun cleanTeachers(raw: String): String = raw.split(';', '；')
        .mapNotNull { part ->
            val trimmed = part.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val name = trimmed.substringAfterLast('/').trim().ifEmpty { trimmed }
            name.takeIf { it.isNotEmpty() }
        }
        .distinct()
        .joinToString("、")

    private fun termCode(xnm: String, xqm: String): String =
        if (xnm.isEmpty()) "" else if (xqm.isEmpty()) xnm else "$xnm-$xqm"

    private fun termText(xnmc: String, xqmmc: String): String {
        val y = xnmc.ifEmpty { "未知学年" }
        return if (xqmmc.isEmpty()) y else "$y-$xqmmc"
    }
}
