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

    /** 展示名。 */
    val label: String
        get() = when (id) {
            OURS.id -> "本项目镜像（推荐）"
            OFFICIAL.id -> "拾光官方"
            else -> "自定义"
        }

    companion object {
        /** 本项目维护的镜像仓库：当前默认导入源。上游失效时在此先同步修复。 */
        val OURS = RepoDescriptor("salt-fishes", "shiguang_warehouse")

        /** 拾光官方上游仓库（上游恢复后可切换回去）。 */
        val OFFICIAL = RepoDescriptor("XingHeYuZhuan", "shiguang_warehouse")

        /** 预置仓库（顺序即展示顺序：本项目镜像在上）。 */
        val PRESETS = listOf(OURS, OFFICIAL)

        /**
         * 从用户输入解析仓库：支持 "owner/name" 与完整 GitHub 仓库网址。
         * 格式非法返回 null。
         */
        fun fromInput(text: String): RepoDescriptor? {
            val t = text.trim().removePrefix("https://").removePrefix("http://")
            val path = t.removePrefix("github.com/").removePrefix("raw.githubusercontent.com/").trim('/')
            val seg = path.split("/", "?", "#").filter { it.isNotBlank() }
            if (seg.size < 2) return null
            val owner = seg[0]
            val name = seg[1].removeSuffix(".git")
            val ok = Regex("^[A-Za-z0-9_.-]+$")
            if (!ok.matches(owner) || !ok.matches(name)) return null
            return RepoDescriptor(owner, name)
        }
    }
}

/**
 * 导入源仓库的本地选择与自定义列表（SharedPreferences，非敏感）。
 *
 * 预置两项：本项目镜像（默认）与拾光官方上游；用户可另加自定义仓库。
 * 索引与脚本缓存按仓库 id 隔离（filesDir/webimport/<owner>_<name>/），切换即换源，
 * 不需要清缓存。历史版本（v1.4/v1.5.0）固定使用官方仓库且无本设置，升级后默认
 * 切到本项目镜像——上游失效期间这是唯一可用源。
 */
class RepoStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("webimport_repo", Context.MODE_PRIVATE)

    /** 当前导入源（从未选择时为本项目镜像）。 */
    fun selected(): RepoDescriptor =
        prefs.getString(K_SELECTED, null)?.let { decode(it) } ?: RepoDescriptor.OURS

    fun select(repo: RepoDescriptor) {
        prefs.edit().putString(K_SELECTED, encode(repo)).apply()
    }

    /** 用户添加的自定义仓库。 */
    fun customs(): List<RepoDescriptor> =
        prefs.getString(K_CUSTOMS, null)
            ?.let { runCatching { org.json.JSONArray(it) }.getOrNull() }
            ?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> decode(arr.optString(i)) }
            }
            ?: emptyList()

    /** 添加自定义仓库；与预置/已有重复时忽略。 */
    fun addCustom(repo: RepoDescriptor) {
        if (RepoDescriptor.PRESETS.any { it.id == repo.id }) return
        val updated = (customs() + repo).distinctBy { it.id }
        saveCustoms(updated)
    }

    fun removeCustom(repoId: String) {
        saveCustoms(customs().filterNot { it.id == repoId })
        if (selected().id == repoId) select(RepoDescriptor.OURS)
    }

    /** 全部可选仓库（预置在前，顺序即 UI 展示顺序）。 */
    fun choices(): List<RepoDescriptor> = (RepoDescriptor.PRESETS + customs()).distinctBy { it.id }

    private fun saveCustoms(list: List<RepoDescriptor>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(encode(it)) }
        prefs.edit().putString(K_CUSTOMS, arr.toString()).apply()
    }

    private fun encode(r: RepoDescriptor) = org.json.JSONObject()
        .put("owner", r.owner).put("name", r.name)
        .put("indexBranch", r.indexBranch).put("mainBranch", r.mainBranch)
        .toString()

    private fun decode(json: String): RepoDescriptor? = runCatching {
        val o = org.json.JSONObject(json)
        RepoDescriptor(
            o.getString("owner"), o.getString("name"),
            o.optString("indexBranch", "index-pb-release"),
            o.optString("mainBranch", "main"),
        )
    }.getOrNull()

    companion object {
        private const val K_SELECTED = "selected_repo"
        private const val K_CUSTOMS = "custom_repos"

        @Volatile private var INSTANCE: RepoStore? = null

        fun getInstance(context: Context): RepoStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RepoStore(context.applicationContext).also { INSTANCE = it }
            }
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
