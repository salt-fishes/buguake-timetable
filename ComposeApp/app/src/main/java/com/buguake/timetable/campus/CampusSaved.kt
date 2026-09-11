package com.buguake.timetable.campus

/**
 * 本机保存的云莓凭据 + 门锁参数。
 *
 * [locks] 里已包含开门所需的全部数据（服务 UUID、写/通知特征值、secret、真实 MAC），
 * 因此**日常开门不需要联网、不需要重新登录**；服务器只在同步/首次绑定时用到。
 * 真实 MAC 另由 [CampusStore.learnedMac] 按锁名记录（快速直连用，退出登录不清除）。
 */
data class CampusSaved(
    val account: String,
    val passwordMd5: String,
    val userId: String,
    val token: String,
    val schoolNo: String,
    val schoolName: String,
    val serverUrl: String,
    val schoolToken: String,
    /** 上次同步到的门锁列表（离线开门的数据源）。 */
    val locks: List<YmLock> = emptyList(),
    /** 默认门锁 = 列表第一把（开门快捷方式与页面首项都用它）。 */
    val defaultLabel: String = "",
    /** 上次成功同步时间（仅用于展示，缓存本身不设过期）。 */
    val updatedAt: Long = 0L,
) {
    /** 默认门锁：优先记录的默认项，否则第一把。 */
    val defaultLock: YmLock?
        get() = locks.firstOrNull { it.label == defaultLabel } ?: locks.firstOrNull()

    /** 默认门锁排在最前，其余保持服务端顺序。 */
    val orderedLocks: List<YmLock>
        get() {
            val def = defaultLock ?: return locks
            return listOf(def) + locks.filter { it !== def }
        }
}
