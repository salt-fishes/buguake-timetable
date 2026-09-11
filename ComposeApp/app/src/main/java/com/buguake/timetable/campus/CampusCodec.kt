package com.buguake.timetable.campus

import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地缓存 bundle 的 JSON 编解码。
 * 只依赖 org.json（JVM 单测可直接覆盖），不碰任何 Android API。
 */
object CampusCodec {

    private const val VERSION = 1

    fun encode(s: CampusSaved): String {
        val locks = JSONArray()
        s.locks.forEach { l ->
            locks.put(
                JSONObject().apply {
                    put("label", l.label)
                    put("serv", l.serviceUuid)
                    put("write", l.writeCharUuid)
                    put("notify", l.notifyCharUuid)
                    put("secret", l.secret)
                    put("mac", l.mac)
                }
            )
        }
        return JSONObject().apply {
            put("v", VERSION)
            put("account", s.account)
            put("pwdMd5", s.passwordMd5)
            put("userId", s.userId)
            put("token", s.token)
            put("schoolNo", s.schoolNo)
            put("schoolName", s.schoolName)
            put("serverUrl", s.serverUrl)
            put("schoolToken", s.schoolToken)
            put("locks", locks)
            put("default", s.defaultLabel)
            put("updatedAt", s.updatedAt)
        }.toString()
    }

    /** 解析失败返回 null（调用方按"无缓存"处理，不抛异常）。 */
    fun decode(text: String): CampusSaved? = runCatching {
        val o = JSONObject(text)
        val arr = o.optJSONArray("locks") ?: JSONArray()
        val locks = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { j ->
                YmLock(
                    label = j.optString("label"),
                    serviceUuid = j.optString("serv"),
                    writeCharUuid = j.optString("write"),
                    notifyCharUuid = j.optString("notify"),
                    secret = j.optString("secret"),
                    mac = j.optString("mac"),
                )
            }
        }.filter { it.label.isNotBlank() && it.serviceUuid.isNotBlank() }
        CampusSaved(
            account = o.optString("account"),
            passwordMd5 = o.optString("pwdMd5"),
            userId = o.optString("userId"),
            token = o.optString("token"),
            schoolNo = o.optString("schoolNo"),
            schoolName = o.optString("schoolName"),
            serverUrl = o.optString("serverUrl"),
            schoolToken = o.optString("schoolToken"),
            locks = locks,
            defaultLabel = o.optString("default"),
            updatedAt = o.optLong("updatedAt"),
        )
    }.getOrNull()
}
