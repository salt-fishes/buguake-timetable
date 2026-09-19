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
import com.buguake.timetable.campus.laundry.DeviceStatus
import com.buguake.timetable.campus.laundry.LaundryStore
import com.buguake.timetable.campus.laundry.LaundryWidgetData
import com.buguake.timetable.campus.laundry.LaundryWidgetRow
import com.buguake.timetable.campus.laundry.ShunshuiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 洗衣房 2×1 小组件：一行速览上次在应用内浏览过的楼栋的空闲台数
 * （"空闲 洗衣 N · 烘干 M"）。
 *
 * 数据来源两层：
 * - 应用内洗衣房页每次刷新设备后写入快照并同步刷新桌面（即时）；
 * - 系统周期回调（最短 30 分钟）时组件自行联网拉取该楼栋各分类状态（兜底），
 *   失败则保留旧值。点击直达应用内该楼栋设备页（LaundryLaunch 总线）。
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
                val store = LaundryStore.getInstance(appContext)
                val data = store.loadWidgetSnapshot()
                // 快照先上屏（无网也不空白）
                val initial = buildViews(appContext, data)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, initial) }
                // 周期兜底：自行拉最新状态；失败保留快照
                val fresh = refreshFromNetwork(store, data) ?: return@launch
                val views = buildViews(appContext, fresh)
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
                val views = buildViews(appContext, LaundryStore.getInstance(appContext).loadWidgetSnapshot())
                manager.updateAppWidget(ids, views)
            }
        }

        /** 逐分类拉取上次浏览楼栋的设备状态并重算空闲数；无数据或失败返回 null。 */
        private suspend fun refreshFromNetwork(
            store: LaundryStore,
            data: LaundryWidgetData?,
        ): LaundryWidgetData? {
            if (data == null || data.categories.isEmpty()) return null
            val client = ShunshuiClient.default()
            val rows = data.categories.mapNotNull { cat ->
                runCatching { client.pagedDevices(data.storeId, data.houseId, cat.id) }
                    .getOrNull()
                    ?.let { list ->
                        LaundryWidgetRow(
                            cat.name,
                            list.count { it.status == DeviceStatus.IDLE && it.online },
                            list.size,
                        )
                    }
            }
            if (rows.isEmpty()) return null
            val fresh = data.copy(rows = rows, updatedAt = System.currentTimeMillis())
            store.saveWidgetSnapshot(fresh)
            return fresh
        }

        private fun buildViews(context: Context, data: LaundryWidgetData?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_laundry_2x1)
            if (data == null) {
                views.setTextViewText(R.id.laundry_house, "洗衣房")
                views.setTextViewText(R.id.laundry_summary, "进入「校园 → 洗衣房」刷新")
            } else {
                views.setTextViewText(R.id.laundry_house, data.houseName)
                views.setTextViewText(R.id.laundry_summary, summaryText(data))
            }
            // 点击直达应用内洗衣房：LaundryLaunch 总线驱动，楼栋由本地缓存自动带出
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .setAction(com.buguake.timetable.campus.LaundryLaunch.ACTION_OPEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.laundry_root, pi)
            return views
        }

        /** "空闲 洗衣 9 · 烘干 1"；门店缺某分类时如实标注。 */
        internal fun summaryText(data: LaundryWidgetData): String {
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
