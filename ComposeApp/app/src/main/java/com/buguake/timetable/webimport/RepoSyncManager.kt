package com.buguake.timetable.webimport

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 导入源仓库描述与索引/脚本同步。
 *
 * 同步策略（比拾光原版的 git 拉取轻量）：
 * - 索引：单文件下载 `school_index.pb`（约 58KB），按 `version_id` 去重；
 * - 脚本：进入导入 WebView 前按需下载单个 `.js` 并本地缓存（懒加载）；
 * - 镜像回退：jsDelivr CDN 优先，raw.githubusercontent 兜底（国内直连不稳）。
 *
 * 安全边界：仓库来源可配置，默认官方仓库（过渡期）；协议版本不匹配时
 * 由调用方提示，不静默失败。
 */

/** 一个脚本仓库（GitHub owner/name + 索引/资源分支）。 */
data class RepoDescriptor(
    val owner: String,
    val name: String,
    val indexBranch: String = "index-pb-release",
    val mainBranch: String = "main",
) {
    val id: String get() = "${owner}_$name"

    companion object {
        /** 默认导入源：拾光官方适配仓库（过渡期；自有 fork 审核后切换）。 */
        val OFFICIAL = RepoDescriptor("XingHeYuZhuan", "shiguang_warehouse")
    }
}

/** 索引刷新结果。 */
data class IndexRefresh(
    /** 本次是否写入了新版本（false = version_id 未变，使用缓存）。 */
    val updated: Boolean,
    val versionId: String,
    val protocolVersion: Int,
    val schoolCount: Int,
    /** 协议版本是否为客户端支持的版本（false 时调用方应提示用户可能不兼容）。 */
    val protocolSupported: Boolean,
)

class RepoSyncManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    private val baseDir = java.io.File(context.filesDir, "webimport")

    // ---- URL 构造（镜像回退顺序即数组顺序） ----

    internal fun candidateUrls(repo: RepoDescriptor, branch: String, path: String): List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/${repo.owner}/${repo.name}@$branch/$path",
        "https://raw.githubusercontent.com/${repo.owner}/${repo.name}/$branch/$path",
    )

    // ---- 下载 ----

    /** 依次尝试镜像下载，全部失败返回聚合错误。 */
    suspend fun download(repo: RepoDescriptor, branch: String, path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            for (url in candidateUrls(repo, branch, path)) {
                try {
                    val body = client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        resp.body?.bytes() ?: throw IOException("空响应体")
                    }
                    return@withContext Result.success(body)
                } catch (e: Exception) {
                    android.util.Log.w("WebImport", "镜像下载失败: $url → ${e.message}")
                    errors.add("$url → ${e.message}")
                }
            }
            Result.failure(IOException("所有镜像下载失败：\n" + errors.joinToString("\n")))
        }

    // ---- 索引 ----

    private fun repoDir(repo: RepoDescriptor) = java.io.File(baseDir, repo.id)
    private fun indexFile(repo: RepoDescriptor) = java.io.File(repoDir(repo), "school_index.pb")
    private fun metaFile(repo: RepoDescriptor) = java.io.File(repoDir(repo), "index_meta.json")

    /**
     * 刷新索引：下载 → 解码校验 → 与本地 version_id 比对 → 原子写入。
     * [force] 为 true 时即使 version_id 相同也重写（用于手动刷新）。
     */
    suspend fun refreshIndex(
        repo: RepoDescriptor = RepoDescriptor.OFFICIAL,
        force: Boolean = false,
    ): Result<IndexRefresh> = runCatching {
        val bytes = download(repo, repo.indexBranch, INDEX_PATH).getOrThrow()
        val index = SchoolIndexDecoder.decode(bytes)
        android.util.Log.i("WebImport", "索引下载成功: v${index.protocolVersion} ${index.versionId}，${index.schools.size} 校")
        if (index.versionId.isBlank()) throw IOException("索引缺少 version_id，数据异常")

        val existing = cachedIndex(repo)
        val unchanged = existing != null && existing.versionId == index.versionId
        if (unchanged && !force) {
            return@runCatching IndexRefresh(
                updated = false, versionId = index.versionId,
                protocolVersion = index.protocolVersion,
                schoolCount = index.schools.size,
                protocolSupported = index.protocolVersion == SchoolIndexDecoder.SUPPORTED_PROTOCOL_VERSION,
            )
        }

        val dir = repoDir(repo)
        dir.mkdirs()
        val tmp = java.io.File(dir, "school_index.pb.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(indexFile(repo))) {
            indexFile(repo).delete()
            if (!tmp.renameTo(indexFile(repo))) throw IOException("索引写入失败")
        }
        metaFile(repo).writeText(
            JSONObject()
                .put("versionId", index.versionId)
                .put("protocolVersion", index.protocolVersion)
                .put("fetchedAt", System.currentTimeMillis())
                .toString()
        )
        IndexRefresh(
            updated = true, versionId = index.versionId,
            protocolVersion = index.protocolVersion,
            schoolCount = index.schools.size,
            protocolSupported = index.protocolVersion == SchoolIndexDecoder.SUPPORTED_PROTOCOL_VERSION,
        )
    }

    /** 本地缓存的索引（未下载过返回 null）。 */
    fun cachedIndex(repo: RepoDescriptor = RepoDescriptor.OFFICIAL): SchoolIndexData? {
        val f = indexFile(repo)
        if (!f.exists()) return null
        return runCatching { SchoolIndexDecoder.decode(f.readBytes()) }.getOrNull()
    }

    // ---- 适配器脚本（懒加载） ----

    /**
     * 获取适配器 JS 内容：本地缓存优先，未命中时从 main 分支
     * `resources/<schoolFolder>/<jsPath>` 下载并缓存。
     */
    suspend fun fetchAdapterScript(
        schoolFolder: String,
        jsPath: String,
        repo: RepoDescriptor = RepoDescriptor.OFFICIAL,
    ): Result<String> = runCatching {
        val file = scriptFile(repo, schoolFolder, jsPath)
        if (file.exists()) return@runCatching file.readText()
        val bytes = download(repo, repo.mainBranch, "resources/$schoolFolder/$jsPath").getOrThrow()
        android.util.Log.i("WebImport", "适配器脚本下载成功: $schoolFolder/$jsPath (${bytes.size}B)")
        val text = String(bytes, Charsets.UTF_8)
        if (!text.contains("shiguangBridge") && !text.contains("AndroidBridge")) {
            throw IOException("脚本内容异常（缺少桥协议调用），已拒绝缓存")
        }
        file.parentFile?.mkdirs()
        file.writeText(text)
        text
    }

    private fun scriptFile(repo: RepoDescriptor, schoolFolder: String, jsPath: String) =
        java.io.File(java.io.File(repoDir(repo), "scripts/$schoolFolder"), jsPath)

    companion object {
        private const val INDEX_PATH = "school_index.pb"
    }
}
