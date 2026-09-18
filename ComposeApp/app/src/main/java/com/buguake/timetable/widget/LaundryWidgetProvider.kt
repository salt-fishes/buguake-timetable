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
import com.buguake.timetable.campus.laundry.LaundryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 洗衣房 2×2 小组件：展示上次在应用内浏览过的楼栋，各分类的空闲台数
 * （只突出洗衣机/烘干机两行）。数据来自应用内每次刷新设备状态时写入的快照，
 * 小组件自身**不发起网络请求**——进入应用洗衣房页刷新后桌面同步更新。
 */
class LaundryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(appContext)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** 应用内设备数据刷新后调用：同步刷新桌面上所有洗衣房小组件。 */
        fun updateAll(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(ComponentName(appContext, LaundryWidgetProvider::class.java))
            if (ids.isEmpty()) return
            runCatching {
                val views = buildViews(appContext)
                manager.updateAppWidget(ids, views)
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_laundry_2x2)
            val data = LaundryStore.getInstance(context).loadWidgetSnapshot()
            if (data == null) {
                views.setTextViewText(R.id.laundry_house, "洗衣房")
                views.setTextViewText(R.id.laundry_washer, "进入「校园 → 洗衣房」")
                views.setTextViewText(R.id.laundry_dryer, "选择楼栋后这里显示空闲台数")
                views.setTextViewText(R.id.laundry_updated, "")
            } else {
                views.setTextViewText(R.id.laundry_house, data.houseName)
                // 只展示洗衣机/烘干机两行；门店没有烘干机时如实标注
                val washer = data.rows.firstOrNull { it.name.contains("洗衣") }
                val dryer = data.rows.firstOrNull { it.name.contains("烘干") }
                views.setTextViewText(
                    R.id.laundry_washer,
                    if (washer == null) "暂无洗衣机" else "洗衣机 ${washer.idle} 台空闲",
                )
                views.setTextViewText(
                    R.id.laundry_dryer,
                    if (dryer == null) "暂无烘干机" else "烘干机 ${dryer.idle} 台空闲",
                )
                views.setTextViewText(
                    R.id.laundry_updated,
                    if (data.updatedAt > 0) "更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.updatedAt)) else "",
                )
            }
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.laundry_root, pi)
            return views
        }
    }
}
