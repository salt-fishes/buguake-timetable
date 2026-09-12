package com.buguake.timetable.campus.exam

import org.json.JSONArray
import org.json.JSONObject

/**
 * 校园本地化：教务系统考试安排。
 *
 * 定位是「校园本地化、暂时性」功能：只服务本校教务（正方 V9），
 * 数据不进课表数据库（Room），只走校园模块自己的加密缓存，随时可清空。
 *
 * [startAt]/[endAt] 为 Asia/Shanghai 的 epoch 毫秒；解析失败时为 0，
 * 此时 [timeText] 保留原始字符串，UI 归入「时间待定」而不是丢数据。
 */
data class CampusExam(
    /** 课程名称（kcmc）。 */
    val courseName: String,
    /** 课程号（kch）。 */
    val courseCode: String,
    /** 考试名称，如「2025-2026-1学期期末考试」（ksmc）。 */
    val examName: String,
    /** 原始考试时间串，如「2026-01-06(09:00-11:00)」（kssj）。 */
    val timeText: String,
    val startAt: Long,
    val endAt: Long,
    /** 考场名称（cdmc）。 */
    val location: String,
    /** 校区（cdxqmc）。 */
    val campus: String,
    /** 座位号（zwh）。 */
    val seat: String,
    /** 考试方式，如「笔试」（ksfs）。 */
    val method: String,
    /** 教师姓名，已从「工号/姓名」清洗为「姓名、姓名」。 */
    val teacher: String,
    /** 学分（xf）。 */
    val credit: String,
    /** 学期码，如「2025-3」（xnm-xqm）。 */
    val termCode: String,
    /** 学期文本，如「2025-2026-1」（xnmc-xqmmc）。 */
    val termText: String,
) {
    val hasTime: Boolean get() = startAt > 0L
}

/** 考试缓存：数据 + 上次同步时间 + 该批数据所属学期（界面标题用）。 */
data class CampusExamBundle(
    val updatedAt: Long,
    val exams: List<CampusExam>,
    /** 读取时选定的学期文本，如「2025-2026 第2学期」；未选（读全部）时为空。 */
    val termLabel: String = "",
)

/** 考试缓存的 JSON 编解码（只依赖 org.json，JVM 单测可覆盖）。 */
object CampusExamCodec {

    private const val VERSION = 1

    fun encode(bundle: CampusExamBundle): String {
        val arr = JSONArray()
        bundle.exams.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("course", e.courseName)
                    put("kch", e.courseCode)
                    put("exam", e.examName)
                    put("timeText", e.timeText)
                    put("start", e.startAt)
                    put("end", e.endAt)
                    put("loc", e.location)
                    put("campus", e.campus)
                    put("seat", e.seat)
                    put("method", e.method)
                    put("teacher", e.teacher)
                    put("credit", e.credit)
                    put("termCode", e.termCode)
                    put("termText", e.termText)
                }
            )
        }
        return JSONObject().apply {
            put("v", VERSION)
            put("updatedAt", bundle.updatedAt)
            put("termLabel", bundle.termLabel)
            put("exams", arr)
        }.toString()
    }

    /** 解析失败返回 null（调用方按"无缓存"处理，不抛异常）。 */
    fun decode(text: String): CampusExamBundle? = runCatching {
        val o = JSONObject(text)
        val arr = o.optJSONArray("exams") ?: JSONArray()
        val exams = (0 until arr.length()).mapNotNull { i ->
            val j = arr.optJSONObject(i) ?: return@mapNotNull null
            val course = j.optString("course").trim()
            if (course.isEmpty()) return@mapNotNull null
            CampusExam(
                courseName = course,
                courseCode = j.optString("kch"),
                examName = j.optString("exam"),
                timeText = j.optString("timeText"),
                startAt = j.optLong("start"),
                endAt = j.optLong("end"),
                location = j.optString("loc"),
                campus = j.optString("campus"),
                seat = j.optString("seat"),
                method = j.optString("method"),
                teacher = j.optString("teacher"),
                credit = j.optString("credit"),
                termCode = j.optString("termCode"),
                termText = j.optString("termText"),
            )
        }
        CampusExamBundle(
            updatedAt = o.optLong("updatedAt"),
            exams = exams,
            termLabel = o.optString("termLabel"),
        )
    }.getOrNull()
}
