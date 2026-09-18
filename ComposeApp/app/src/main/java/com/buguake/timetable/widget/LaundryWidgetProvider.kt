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

/**
 * 洗衣房 2×1 小组件：一行速览上次在应用内浏览过的楼栋的空闲台数
 * （"空闲 洗衣 N · 烘干 M"）。数据来自应用内每次刷新设备状态时写入的快照，
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
            val views = RemoteViews(context.packageName, R.layout.widget_laundry_2x1)
            val data = LaundryStore.getInstance(context).loadWidgetSnapshot()
            if (data == null) {
                views.setTextViewText(R.id.laundry_house, "洗衣房")
                views.setTextViewText(R.id.laundry_summary, "进入「校园 → 洗衣房」刷新")
            } else {
                views.setTextViewText(R.id.laundry_house, data.houseName)
                views.setTextViewText(R.id.laundry_summary, summaryText(data))
            }
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.laundry_root, pi)
            return views
        }

        /** "空闲 洗衣 9 · 烘干 1"；门店缺某分类时如实标注。 */
        internal fun summaryText(data: com.buguake.timetable.campus.laundry.LaundryWidgetData): String {
            val parts = mutableListOf<String>()
            val washer = data.rows.firstOrNull { it.name.contains("洗衣") }
            val dryer = data.rows.firstOrNull { it.name.contains("烘干") }
            if (washer != null) parts += "洗衣 ${washer.idle}"
            if (dryer != null) parts += "烘干 ${dryer.idle}"
            if (parts.isEmpty()) return "暂无洗衣机/烘干机"
            return "空闲 " + parts.joinToString(" · ")
        }
    }
}
