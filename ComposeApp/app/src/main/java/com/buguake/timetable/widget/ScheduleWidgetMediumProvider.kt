package com.buguake.timetable.widget

import com.buguake.timetable.R

/**
 * 今日课程小组件（3×2 变体）：与列表型共用同一布局与数据源，
 * 仅注册为独立组件（尺寸由 appwidget-provider XML 决定），列表随高度少显几行。
 */
class ScheduleWidgetMediumProvider : ScheduleWidgetProvider() {

    override val layoutRes: Int = R.layout.widget_schedule
    override val compactItems: Boolean = false
}
