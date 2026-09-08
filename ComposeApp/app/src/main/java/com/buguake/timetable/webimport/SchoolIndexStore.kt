package com.buguake.timetable.webimport

import android.content.Context
import java.io.IOException

/**
 * 学校索引门面：UI 层的唯一入口。
 * - [loadCached]：立即返回本地缓存（冷启动快速展示）
 * - [refresh]：联网刷新（结果写缓存后返回最新索引）
 * - [adapterJs]：进入导入 WebView 前取适配器脚本（懒加载 + 缓存）
 */
class SchoolIndexStore private constructor(context: Context) {

    private val sync = RepoSyncManager(context)
    private val repo = RepoDescriptor.OFFICIAL

    /** 本地缓存索引（首次使用前为 null）。 */
    fun loadCached(): SchoolIndexData? = sync.cachedIndex(repo)

    /**
     * 联网刷新索引。version_id 未变且未强制时直接返回缓存，不重复下载。
     * 协议版本不受支持时抛出 IOException（调用方展示，不静默使用未知协议）。
     */
    suspend fun refresh(force: Boolean = false): Result<SchoolIndexData> =
        sync.refreshIndex(repo, force).mapCatching { r ->
            if (!r.protocolSupported) {
                throw IOException(
                    "适配器索引协议版本 v${r.protocolVersion} 超出当前客户端支持范围" +
                        "（支持 v${SchoolIndexDecoder.SUPPORTED_PROTOCOL_VERSION}），请升级应用"
                )
            }
            sync.cachedIndex(repo) ?: throw IOException("索引刷新后读取失败")
        }

    /** 刷新失败时的降级路径：缓存可用则返回缓存。 */
    suspend fun refreshOrCached(force: Boolean = false): Result<SchoolIndexData> {
        val r = refresh(force)
        if (r.isSuccess) return r
        return loadCached()?.let { Result.success(it) }
            ?: r
    }

    /** 适配器脚本全文（缓存优先，未命中联网下载）。 */
    suspend fun adapterJs(school: SchoolData, adapter: AdapterData): Result<String> =
        sync.fetchAdapterScript(school.folder, adapter.jsPath, repo)

    companion object {
        @Volatile private var INSTANCE: SchoolIndexStore? = null

        fun getInstance(context: Context): SchoolIndexStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SchoolIndexStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}
