package com.buguake.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.buguake.timetable.MainActivity
import com.buguake.timetable.R
import com.buguake.timetable.data.AppDatabase
import com.buguake.timetable.data.EntryWithCourse
import com.buguake.timetable.data.SettingsRepository
import com.buguake.timetable.data.TimetableEntity
import com.buguake.timetable.data.TimeUtils
import com.buguake.timetable.data.WeekCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * 今日课程小组件：4×2 双栏（左栏今天 / 右栏明天）；3×2 列表与 2×2 紧凑由子类共用列表渲染。
 * 配色按应用内深色模式与「磨砂玻璃风格」设置四变体切换（WidgetTheme）。
 * 遵循「显示周末」开关：隐藏周末时周六/日显示空态；数据变化后由 AppRefresh 触发刷新，
 * 系统兜底每 30 分钟周期刷新（跨天/跨周）。
 */
/**
 * 小组件单行数据（列表 Factory 与 Provider 头部共用）。
 */
data class WidgetRow(
    val entryId: Long,
    val start: String,
    val end: String,
    val name: String,
    val loc: String,
    val past: Boolean,
)

open class ScheduleWidgetProvider : AppWidgetProvider() {

    /** 小组件布局（4×2 双栏；列表型 3×2/2×2 变体覆盖为 widget_schedule）。 */
    protected open val layoutRes: Int = R.layout.widget_schedule_4x2

    /** 列表项是否用紧凑布局（随 RemoteAdapter intent 传给 Service）。 */
    protected open val compactItems: Boolean = false

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) logWidgetSize(appWidgetManager, id)
        val pending = goAsync()
        val appContext = context.applicationContext
        val layout = layoutRes
        val compact = compactItems
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 每个实例按自己的绑定课表独立渲染
                for (id in appWidgetIds) {
                    val views = buildViews(
                        appContext, layout, compact, resolveTimetableId(appContext, id),
                    )
                    appWidgetManager.updateAppWidget(id, views)
                }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        logWidgetSize(appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    /** 尺寸诊断：打印 launcher 分配的真实 dp 范围（判断格子长宽比/换算）。 */
    private fun logWidgetSize(appWidgetManager: AppWidgetManager, id: Int) {
        val o = appWidgetManager.getAppWidgetOptions(id)
        val minW = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = o.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        android.util.Log.i(
            "WebImport",
            "widget[$id] 尺寸dp: 竖屏=${minW}x$maxH 横屏=$maxW x$minH"
        )
    }

    companion object {

        /** 实例绑定的课表：未设置(0)时跟随当前活动课表。 */
        private fun resolveTimetableId(context: Context, widgetId: Int): Long =
            getWidgetBinding(context, widgetId) ?: resolveActiveTimetableId(context)

        /** 绑定 id（>0），未绑定返回 null。供 Service 工厂兜底使用。 */
        internal fun getWidgetBinding(context: Context, widgetId: Int): Long? =
            SettingsRepository.getInstance(context).getWidgetTimetableId(widgetId)
                .takeIf { it > 0L }

        internal fun resolveActiveTimetableId(context: Context): Long =
            SettingsRepository.getInstance(context).activeTimetableId

        /** 数据变化后的主动刷新入口（更新 4×2 / 3×2 / 2×2 的所有实例，并剪除失效绑定）。 */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val mgr = AppWidgetManager.getInstance(appContext)
            val settingsRepo = SettingsRepository.getInstance(appContext)
            val targets = listOf(
                Triple(
                    ComponentName(appContext, ScheduleWidgetProvider::class.java),
                    R.layout.widget_schedule_4x2, false,
                ),
                Triple(
                    ComponentName(appContext, ScheduleWidgetMediumProvider::class.java),
                    R.layout.widget_schedule, false,
                ),
                Triple(
                    ComponentName(appContext, ScheduleWidgetCompactProvider::class.java),
                    R.layout.widget_schedule_compact, true,
                ),
            )
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val validIds = mutableSetOf<Int>()
                    for ((cn, layout, compact) in targets) {
                        val ids = mgr.getAppWidgetIds(cn)
                        validIds.addAll(ids.toList())
                        for (id in ids) {
                            val views = buildViews(
                                appContext, layout, compact,
                                resolveTimetableId(appContext, id),
                            )
                            mgr.updateAppWidget(id, views)
                        }
                    }
                    settingsRepo.pruneWidgetBindings(validIds)
                } catch (_: Throwable) {
                }
            }
        }

        /** 加载指定课表某天的课程行；返回 (周次标签, 行列表)。date 缺省 = 今天。 */
        internal suspend fun loadRows(
            context: Context,
            timetableId: Long,
            date: LocalDate = LocalDate.now(),
        ): Pair<String, List<WidgetRow>> {
            val settings = SettingsRepository.getInstance(context).current
            val dao = AppDatabase.getInstance(context).scheduleDao()
            // 绑定的课表（可能非活动课表）：开学日/周数/作息/名称都取自它
            val timetable = dao.getTimetable(timetableId)
                ?: TimetableEntity(
                    name = settings.timetableName.ifBlank { "我的课表" },
                    startMillis = settings.semesterStart,
                    totalWeeks = settings.totalWeeks,
                )
            val sectionTimes = SettingsRepository.sectionTimesFor(
                timetable.sectionTimesCsv, timetable.sectionsPerDay, settings.sectionTimes,
            )
            val startDate = if (timetable.startMillis == 0L) null
            else java.time.Instant.ofEpochMilli(timetable.startMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val rawWeek = WeekCalculator.currentWeek(startDate, date)
            // 开学前（周数 <= 0）不展示任何课程，避免提前泄露开学后的安排
            if (rawWeek < 1) return "未开学" to emptyList()
            // 遵循「显示周末」开关：隐藏周末则周六/日不展示课程
            if (!settings.showWeekend && date.dayOfWeek.value >= 6) {
                return "第 $rawWeek 周" to emptyList()
            }
            val entries = dao.getAllEntries(timetableId)
                .filter { it.dayOfWeek == date.dayOfWeek.value && it.isInWeek(rawWeek) }
                .sortedBy { it.startSection ?: 99 }
            // 「已结束」淡化只对今天有意义（明天/后天的课都不算上完）
            val isToday = date == LocalDate.now()
            val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
            val rows = entries.map { e ->
                // 开始时间取起始节次、结束时间取结束节次：连堂课（如 6-8 节）显示最后一节的下课时间
                val startSpan = TimeUtils.sectionMinutes(sectionTimes, e.startSection ?: 1)
                val endSpan = TimeUtils.sectionMinutes(
                    sectionTimes,
                    e.endSection ?: e.startSection ?: 1,
                )
                WidgetRow(
                    entryId = e.entryId,
                    start = startSpan?.let { fmt(it.first) } ?: "",
                    end = endSpan?.let { fmt(it.second) } ?: "",
                    name = e.courseName + typeSymbol(e.type),
                    loc = shortLocation(e),
                    past = isToday && endSpan != null && nowMinutes > endSpan.second,
                )
            }
            return "第 $rawWeek 周" to rows
        }

        internal fun typeSymbol(type: String): String = when (type) {
            "讲课" -> "★"
            "实验" -> "○"
            "上机" -> "●"
            "实践" -> "◇"
            "集中实践" -> ":"
            else -> ""
        }

        internal fun shortLocation(e: EntryWithCourse): String =
            e.room.ifBlank { e.building.ifBlank { e.campus } }

        internal fun fmt(minutesOfDay: Int): String =
            "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

        private suspend fun buildViews(
            context: Context,
            layoutRes: Int,
            compactItems: Boolean,
            timetableId: Long,
        ): RemoteViews {
            // 配色：按应用内深色模式/玻璃设置选变体（背景资源 + 程序化文字色）
            val theme = WidgetTheme.resolve(context)
            // 点击整块打开应用
            val pi = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return if (layoutRes == R.layout.widget_schedule_4x2) {
                buildWideViews(context, timetableId, theme, pi)
            } else {
                buildListViews(context, layoutRes, compactItems, timetableId, theme, pi)
            }
        }

        /** 4×2 双栏：左栏今天、右栏明天（每栏最多 4 行，超出忽略；无课显示空态）。 */
        private suspend fun buildWideViews(
            context: Context,
            timetableId: Long,
            theme: WidgetTheme.Palette,
            pi: PendingIntent,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x2)
            views.setInt(R.id.widget_root, "setBackgroundResource", theme.backgroundRes)
            views.setTextColor(R.id.widget_title, theme.textPrimary)
            views.setTextColor(R.id.widget_count, theme.accent)
            views.setTextColor(R.id.widget_day_today, theme.textSecondary)
            views.setTextColor(R.id.widget_day_tomorrow, theme.textSecondary)
            views.setTextColor(R.id.widget_empty_today, theme.textSecondary)
            views.setTextColor(R.id.widget_empty_tomorrow, theme.textSecondary)
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val (weekLabel, todayRows) = loadRows(context, timetableId, today)
            val (_, tomorrowRows) = loadRows(context, timetableId, tomorrow)
            val timetableName = AppDatabase.getInstance(context).scheduleDao()
                .getTimetable(timetableId)?.name ?: "课表"
            views.setTextViewText(R.id.widget_title, timetableName)
            views.setTextViewText(
                R.id.widget_count,
                "${today.monthValue}月${today.dayOfMonth}日 · $weekLabel",
            )
            views.setTextViewText(
                R.id.widget_day_today,
                "今天 · ${dayNames[today.dayOfWeek.value - 1]}",
            )
            views.setTextViewText(
                R.id.widget_day_tomorrow,
                "明天 · ${dayNames[tomorrow.dayOfWeek.value - 1]}",
            )
            fillColumn(context, views, R.id.widget_col_today, R.id.widget_empty_today, todayRows.take(4), theme)
            fillColumn(context, views, R.id.widget_col_tomorrow, R.id.widget_empty_tomorrow, tomorrowRows.take(4), theme)
            return views
        }

        /** 双栏之一：逐行 addView 条目，空则显示空态文本。 */
        private fun fillColumn(
            context: Context,
            views: RemoteViews,
            colId: Int,
            emptyId: Int,
            rows: List<WidgetRow>,
            theme: WidgetTheme.Palette,
        ) {
            views.removeAllViews(colId)
            views.setViewVisibility(
                emptyId,
                if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE,
            )
            for (r in rows) {
                val item = RemoteViews(context.packageName, R.layout.widget_schedule_item)
                item.setTextViewText(R.id.item_time_start, r.start)
                item.setTextViewText(R.id.item_time_end, r.end)
                item.setTextViewText(R.id.item_name, r.name)
                item.setTextViewText(R.id.item_loc, r.loc)
                item.setTextColor(R.id.item_time_start, theme.accent)
                item.setTextColor(R.id.item_time_end, theme.textSecondary)
                item.setTextColor(R.id.item_name, theme.textPrimary)
                item.setTextColor(R.id.item_loc, theme.textSecondary)
                views.addView(colId, item)
            }
        }

        /** 列表型（3×2 / 2×2）：标题 + 今日课程 RemoteAdapter 列表。 */
        private suspend fun buildListViews(
            context: Context,
            layoutRes: Int,
            compactItems: Boolean,
            timetableId: Long,
            theme: WidgetTheme.Palette,
            pi: PendingIntent,
        ): RemoteViews {
            val (week, rows) = loadRows(context, timetableId)
            val timetableName = AppDatabase.getInstance(context).scheduleDao()
                .getTimetable(timetableId)?.name ?: "课表"
            val views = RemoteViews(context.packageName, layoutRes)

            views.setInt(R.id.widget_root, "setBackgroundResource", theme.backgroundRes)
            views.setTextColor(R.id.widget_title, theme.textPrimary)
            views.setTextColor(R.id.widget_subtitle, theme.textSecondary)
            views.setTextColor(R.id.widget_count, theme.accent)
            views.setTextColor(R.id.widget_empty, theme.textSecondary)

            views.setOnClickPendingIntent(R.id.widget_root, pi)
            // 标题显示所绑定课表的名称
            views.setTextViewText(R.id.widget_title, timetableName)

            val today = LocalDate.now()
            val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            views.setTextViewText(
                R.id.widget_subtitle,
                "${today.monthValue}月${today.dayOfMonth}日 ${dayNames[today.dayOfWeek.value - 1]} · $week",
            )
            views.setTextViewText(R.id.widget_count, if (rows.isNotEmpty()) "${rows.size} 节" else "")

            views.setRemoteAdapter(
                R.id.widget_list,
                Intent(context, ScheduleWidgetService::class.java)
                    .putExtra(ScheduleWidgetService.EXTRA_COMPACT_ITEMS, compactItems)
                    .putExtra(ScheduleWidgetService.EXTRA_TIMETABLE_ID, timetableId),
            )
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            return views
        }
    }
}
