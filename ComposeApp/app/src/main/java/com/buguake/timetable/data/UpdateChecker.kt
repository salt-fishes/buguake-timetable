package com.buguake.timetable.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 检测到的新版本信息。 */
data class UpdateInfo(
    val version: String,
    val name: String,
    val notes: String,
    val url: String,
)

/**
 * 手动检查更新：查询 GitHub 仓库最新版本。
 * 优先 releases/latest；仓库尚无 Release 时回退到最新 tag（此时只能给出标签页链接）。
 * 仅在用户点击「检查更新」时调用，不做任何后台轮询。
 */
object UpdateChecker {

    private const val OWNER = "salt-fishes"
    private const val REPO = "buguake-timetable"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 返回新版本；已是最新或无可用版本时返回 null；网络/接口异常抛出。 */
    fun check(currentVersion: String): UpdateInfo? {
        val release = getJsonOrNull("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
        if (release != null) {
            val tag = release.optString("tag_name").trim()
            if (tag.isBlank()) return null
            val version = normalize(tag)
            if (!isNewer(version, currentVersion)) return null
            return UpdateInfo(
                version = version,
                name = release.optString("name").trim().ifBlank { tag },
                notes = release.optString("body").trim(),
                url = release.optString("html_url").trim()
                    .ifBlank { "https://github.com/$OWNER/$REPO/releases" },
            )
        }

        val tags = getArray("https://api.github.com/repos/$OWNER/$REPO/tags")
        val best = (0 until tags.length())
            .mapNotNull { tags.optJSONObject(it)?.optString("name")?.trim()?.takeIf { n -> n.isNotBlank() } }
            .maxWithOrNull(versionComparator)
            ?: return null
        val version = normalize(best)
        if (!isNewer(version, currentVersion)) return null
        return UpdateInfo(
            version = version,
            name = best,
            notes = "",
            url = "https://github.com/$OWNER/$REPO/releases/tag/$best",
        )
    }

    private fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    private fun versionParts(v: String): List<Int> =
        normalize(v).split('.', '-', '_', '+').mapNotNull { it.toIntOrNull() }

    private fun isNewer(candidate: String, current: String): Boolean =
        versionComparator.compare(candidate, current) > 0

    private val versionComparator = Comparator<String> { a, b ->
        val pa = versionParts(a)
        val pb = versionParts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return@Comparator x - y
        }
        0
    }

    private fun get(url: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "buguake-timetable")
            .build()
        client.newCall(request).execute().use { response ->
            return response.code to (response.body?.string() ?: "")
        }
    }

    private fun getJsonOrNull(url: String): JSONObject? {
        val (code, body) = get(url)
        if (code == 404) return null
        if (code !in 200..299) throw IOException("GitHub 返回 HTTP $code")
        return runCatching { JSONObject(body) }.getOrElse { throw IOException("响应解析失败") }
    }

    private fun getArray(url: String): JSONArray {
        val (code, body) = get(url)
        if (code !in 200..299) throw IOException("GitHub 返回 HTTP $code")
        return runCatching { JSONArray(body) }.getOrElse { throw IOException("响应解析失败") }
    }
}
