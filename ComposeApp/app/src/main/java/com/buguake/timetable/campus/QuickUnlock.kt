package com.buguake.timetable.campus

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「长按应用图标 → 快速开锁」的直达请求总线。
 *
 * MainActivity 收到快捷方式 Intent 后只把序号 +1；界面侧的 AppRoot 监听序号切到校园页，
 * 由 CampusUnlockScreen 用默认门锁直接开门，避免 Intent 层层透传。
 */
object QuickUnlock {

    const val ACTION_UNLOCK = "com.buguake.timetable.campus.UNLOCK"

    private val _seq = MutableStateFlow(0)

    /** 每次快捷方式触发 +1；0 表示从未触发。 */
    val seq: StateFlow<Int> = _seq

    fun notify(intent: Intent?) {
        if (intent?.action == ACTION_UNLOCK) _seq.value = _seq.value + 1
    }
}
