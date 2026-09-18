package com.buguake.timetable.campus

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 洗衣房小组件 → 应用内直达请求总线（与 [QuickUnlock] 同模式）。
 *
 * 小组件点击以 `action = [ACTION_OPEN]` 拉起 MainActivity，这里只把序号 +1；
 * 界面侧的 AppShell 监听序号切到校园页并打开洗衣房功能，洗衣房再按本地缓存
 * 自动打开上次浏览的楼栋（无缓存则停在主页），避免 Intent 层层透传。
 * 仅打开界面、无任何敏感副作用，因此不做调用方白名单校验。
 */
object LaundryLaunch {

    const val ACTION_OPEN = "com.buguake.timetable.campus.LAUNDRY"

    private val _seq = MutableStateFlow(0)

    /** 每次小组件触发 +1；0 表示从未触发。 */
    val seq: StateFlow<Int> = _seq

    fun notifyIfMatches(intent: Intent?) {
        if (intent?.action == ACTION_OPEN) _seq.value = _seq.value + 1
    }
}
