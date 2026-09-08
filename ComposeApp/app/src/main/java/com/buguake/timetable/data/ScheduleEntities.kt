package com.buguake.timetable.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 课表（多课表管理的第一实体）：一组课程 + 自己的开学日、总周数与作息时间。 */
@Entity(tableName = "timetables")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMillis: Long,     // 第 1 周周一 00:00；0 = 待从旧设置回填
    val totalWeeks: Int,
    @ColumnInfo(defaultValue = "0") val sectionsPerDay: Int = 0,        // 0 = 未单独设置（继承全局）
    @ColumnInfo(defaultValue = "") val sectionTimesCsv: String = "",    // 空 = 未单独设置（继承全局）
    val createdAt: Long = System.currentTimeMillis(),
)

/** 课程（课表内按名称唯一）。地点/教师随排课条目存储——同一门课不同天可能不同教室。 */
@Entity(
    tableName = "courses",
    indices = [Index(value = ["timetableId", "name"], unique = true), Index("timetableId")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val timetableId: Long = 1,  // 所属课表（迁移默认 1）
    val name: String,
    val type: String,          // 讲课/实验/上机/实践/集中实践
    val credit: String,
    val classNo: String,
    val composition: String,
    val colorIndex: Int,       // 0..2，映射 primary/secondary/tertiary-container 色调对
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,  // 手动隐藏
    @ColumnInfo(defaultValue = "") val remark: String = "",       // 备注（教务导入可带，≤300 字）
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 排课条目：星期几、第几节、哪些周、当天的地点与教师。 */
@Entity(
    tableName = "schedule_entries",
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("courseId"), Index("dayOfWeek")]
)
data class ScheduleEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val dayOfWeek: Int,        // 1=星期一 ... 7=星期日
    val startSection: Int?,
    val endSection: Int?,
    val weeksCsv: String,      // 展开的周次，逗号分隔，如 "1,2,3,5"
    val campus: String = "",
    val building: String = "",
    val room: String = "",
    val teacher: String = "",
    // 自定义时间段课次（教务课次不落在标准节次网格上时使用）：
    // isCustomTime=true 时 start/endSection 仍存「与作息表重叠的节次区间」（供网格落位，
    // 可为空=完全在网格外），真实起止时间以 customStartTime/customEndTime（"HH:MM"）为准
    @ColumnInfo(defaultValue = "0") val isCustomTime: Boolean = false,
    @ColumnInfo(defaultValue = "") val customStartTime: String = "",
    @ColumnInfo(defaultValue = "") val customEndTime: String = "",
)

/** 条目 + 课程名聚合视图（UI/小组件直接消费）。 */
data class EntryWithCourse(
    val entryId: Long,
    val courseId: Long,
    val courseName: String,
    val type: String,
    val credit: String,
    val colorIndex: Int,
    val dayOfWeek: Int,
    val startSection: Int?,
    val endSection: Int?,
    val weeksCsv: String,
    val campus: String,
    val building: String,
    val room: String,
    val teacher: String,
    val isCustomTime: Boolean = false,
    val customStartTime: String = "",
    val customEndTime: String = "",
    val remark: String = "",
) {
    val weeks: Set<Int> get() = weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun isInWeek(week: Int): Boolean = week in weeks

    /** 是否与 [other] 在节次上有重叠。 */
    fun overlaps(other: EntryWithCourse): Boolean {
        val s1 = startSection ?: 0; val e1 = endSection ?: s1
        val s2 = other.startSection ?: 0; val e2 = other.endSection ?: s2
        return dayOfWeek == other.dayOfWeek && s1 <= e2 && s2 <= e1
    }

    /** "环宇楼A404" 风格的短地点（格子内展示）。 */
    val shortLocation: String get() = room.ifBlank { building.ifBlank { campus } }

    /** 完整地点（详情页展示）。 */
    val fullLocation: String
        get() = listOf(campus, building, room).filter { it.isNotBlank() }.joinToString(" ")

    /** "08:00" 形式的自定义起止（仅 isCustomTime 时有意义，解析失败为空串）。 */
    val customTimeLabel: String
        get() = if (!isCustomTime) "" else {
            val s = customStartTime.takeIf { it.isNotBlank() } ?: "--:--"
            val e = customEndTime.takeIf { it.isNotBlank() } ?: "--:--"
            "$s–$e"
        }

    val sectionRangeLabel: String
        get() = when {
            isCustomTime -> "自定义 $customTimeLabel"
            startSection == null -> ""
            endSection != null && endSection != startSection -> "$startSection-$endSection 节"
            else -> "$startSection 节"
        }

    /**
     * 条目当日的上课分钟区间 [开始, 结束)：
     * 自定义时间段直接解析 "HH:MM"（不依赖作息表）；节次条目查作息表。
     * 解析失败返回 null。
     */
    fun minutesOfDay(sectionTimes: List<SectionTime>): Pair<Int, Int>? {
        if (isCustomTime) {
            val s = customStartTime.parseHm() ?: return null
            val e = customEndTime.parseHm() ?: return null
            return s to e
        }
        val start = startSection ?: return null
        val span = TimeUtils.sectionMinutes(sectionTimes, start) ?: return null
        val endSectionMinutes = endSection?.let {
            TimeUtils.sectionMinutes(sectionTimes, it)?.second
        } ?: span.second
        return span.first to maxOf(endSectionMinutes, span.second)
    }

    /**
     * 网格落位用的节次区间：节次条目原样返回；自定义时间段按「时间重叠」映射到
     * 作息表节次（[customTimeSections]），无法映射返回 null（调用方从网格剔除）。
     */
    fun effectiveSections(sectionTimes: List<SectionTime>): IntRange? {
        if (!isCustomTime) {
            val s = startSection ?: return null
            return s..(endSection ?: s)
        }
        val s = customStartTime.parseHm() ?: return null
        val e = customEndTime.parseHm() ?: return null
        return customTimeSections(s, e, sectionTimes)
    }

    /** 副本：把自定义时间条目的起止节次替换为按作息表映射出的节次区间（布局用）。 */
    fun withEffectiveSections(sectionTimes: List<SectionTime>): EntryWithCourse? {
        if (!isCustomTime) return this
        val r = effectiveSections(sectionTimes) ?: return null
        return copy(startSection = r.first, endSection = r.last)
    }

    private fun String.parseHm(): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(trim()) ?: return null
        val (h, mi) = m.destructured
        val hh = h.toInt(); val mm = mi.toInt()
        if (hh !in 0..23 || mm !in 0..59) return null
        return hh * 60 + mm
    }

    companion object {
        /**
         * 自定义时间段 → 作息表节次区间：取「与 [customStart],[customEnd) 有时间重叠」
         * 的最小连续节次范围（半开区间判定：节次.start < customEnd && customStart < 节次.end）。
         * 无任何重叠返回 null。
         */
        fun customTimeSections(
            customStart: Int,
            customEnd: Int,
            sectionTimes: List<SectionTime>,
        ): IntRange? {
            val hits = sectionTimes
                .filter { it.start.hour * 60 + it.start.minute < customEnd &&
                    customStart < it.end.hour * 60 + it.end.minute }
                .map { it.section }
            if (hits.isEmpty()) return null
            return hits.min()..hits.max()
        }
    }
}
