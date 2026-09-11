package com.buguake.timetable.campus

/** 同步失败的语义分类。 */
enum class CampusFailure { NONE, NETWORK, AUTH, UNKNOWN }

/**
 * 离线优先同步策略（纯逻辑，便于 JVM 单测）。
 *
 * 核心不变量：**只有鉴权类失败才允许降级清理本机凭据**；网络类失败一律保留缓存与凭据。
 * 旧实现的 bug 正是把网络失败也当成"凭据过期"，一次断网就清空免登录状态。
 */
object CampusSyncPolicy {

    fun classify(t: Throwable?): CampusFailure = when (t) {
        null -> CampusFailure.NONE
        is YunmeiNetworkException -> CampusFailure.NETWORK
        is YunmeiAuthException -> CampusFailure.AUTH
        else -> CampusFailure.UNKNOWN
    }

    /** 该失败是否必须保留本机凭据（网络类：保留）。 */
    fun keepCredentials(failure: CampusFailure): Boolean = failure == CampusFailure.NETWORK

    /**
     * token 复用返回空、但本机缓存里明明有门锁 → 视为"token 可能已失效"，
     * 需要走一次密码 MD5 静默重登核对，而不是直接认定账号下没有门锁。
     */
    fun suspectTokenFailure(cachedCount: Int, fetchedCount: Int): Boolean =
        cachedCount > 0 && fetchedCount == 0

    /** 进入页面时的初始模式。 */
    enum class Mode {
        /** 有门锁缓存：立刻渲染并开门，后台静默刷新。 */
        CACHE,

        /** 有凭据但还没门锁缓存（首次升级/上次同步失败）：静默同步，不要求输密码。 */
        SILENT,

        /** 无凭据：显示登录表单。 */
        FORM,
    }

    fun initialMode(saved: CampusSaved?): Mode = when {
        saved == null -> Mode.FORM
        saved.locks.isNotEmpty() -> Mode.CACHE
        else -> Mode.SILENT
    }

    /** 鉴权失败后的下一步：本机还留着密码 MD5 才能静默重登，否则回登录表单。 */
    fun afterAuthFailure(canRetryWithPassword: Boolean): Mode =
        if (canRetryWithPassword) Mode.SILENT else Mode.FORM
}
