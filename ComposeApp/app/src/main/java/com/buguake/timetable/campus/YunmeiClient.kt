package com.buguake.timetable.campus

import java.security.MessageDigest

/**
 * 云莓智能第三方协议客户端（宿舍蓝牙门锁开门）。
 *
 * 协议移植自 zxy19/yunmei_unintelligent（MIT License, Copyright (c) 2022 zxypp），
 * 该项目通过逆向"云莓智能"官方客户端提取接口——接口可能随官方更新失效。
 * HTTP 层以 okhttp 重新实现（原项目为自写 HttpClient4）。
 */

data class YmSchool(
    val schoolNo: String,
    val name: String,
    /** 该校云莓服务地址（登录后所有业务请求都发往这里）。 */
    val serverUrl: String,
    val token: String,
)

data class YmLock(
    /** 显示名：楼栋-宿舍号。 */
    val label: String,
    val serviceUuid: String,
    val writeCharUuid: String,
    val notifyCharUuid: String,
    val secret: String,
    var mac: String,
)

data class YmSession(
    val account: String,
    val accountMd5: String,
    val passwordMd5: String,
    val userId: String,
) {
    val tokenHeaders: Map<String, String>
        get() = mapOf(
            "token_data" to token,
            "token_userId" to userId,
            "tokenUserId" to userId,
        )

    var token: String = ""
}

class YunmeiClient(
    private val post: suspend (url: String, form: Map<String, String>, headers: Map<String, String>) -> String,
) {
    var session: YmSession? = null
    var school: YmSchool? = null
    var schoolToken: String = ""

    private fun headers(): Map<String, String> {
        val s = session ?: error("未登录")
        return if (school == null) s.tokenHeaders
        else s.tokenHeaders + ("token_data" to schoolToken)
    }

    private fun abs(url: String): String {
        val base = school?.serverUrl?.takeIf { it.isNotBlank() } ?: BASE_URL
        return if (base.endsWith("/")) base + url.removePrefix("/") else "$base/$url"
    }

    private suspend fun postForm(url: String, form: Map<String, String>): String {
        val s = session ?: error("未登录")
        return post(abs(url), form, headers())
    }

    companion object {
        const val BASE_URL = "https://base.yunmeitech.com/"

        fun md5(text: String): String =
            MessageDigest.getInstance("MD5").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }

        /**
         * 登录并拉取学校列表。密码非 MD5 时先做 MD5。
         * @throws YunmeiException 登录失败（携带服务端 msg）或网络/解析失败
         */
        suspend fun login(
            account: String,
            password: String,
            post: suspend (url: String, form: Map<String, String>, headers: Map<String, String>) -> String,
            passwordAlreadyMd5: Boolean = false,
        ): Pair<YunmeiClient, List<YmSchool>> {
            val client = YunmeiClient(post)
            val pwdMd5 = if (passwordAlreadyMd5) password else md5(password)
            val loginBody = runCatching {
                post(
                    "$BASE_URL/login",
                    mapOf("userName" to account, "userPwd" to pwdMd5),
                    emptyMap(),
                )
            }.getOrElse { e ->
                // 网络类失败必须保持类型，调用方据此决定"保留凭据等待重试"而非清库
                if (e is YunmeiException) throw e
                throw YunmeiNetworkException("网络请求失败：${e.message ?: "未知错误"}")
            }
            val loginRes = runCatching { org.json.JSONObject(loginBody) }
                .getOrElse { throw YunmeiException("登录响应解析失败") }
            if (!loginRes.optBoolean("success", false)) {
                // 账号密码错误 / 登录态失效：属于鉴权失败
                throw YunmeiAuthException(loginRes.optString("msg", "登录失败"))
            }
            val o = loginRes.getJSONObject("o")
            client.session = YmSession(
                account = account,
                accountMd5 = md5(account),
                passwordMd5 = pwdMd5,
                userId = o.getString("userId"),
            ).also { it.token = o.getString("token") }

            val schoolArr = org.json.JSONArray(
                client.postForm("/userschool/getbyuserid", mapOf("userId" to client.session!!.userId))
            )
            val schools = (0 until schoolArr.length()).mapNotNull { i ->
                runCatching {
                    val r = schoolArr.getJSONObject(i)
                    val schoolObj = r.getJSONObject("school")
                    YmSchool(
                        schoolNo = r.getString("schoolNo"),
                        name = schoolObj.getString("schoolName"),
                        serverUrl = schoolObj.getString("serverUrl"),
                        token = r.getString("token"),
                    )
                }.getOrNull()
            }
            return client to schools
        }

        /**
         * 用本机缓存恢复会话（不联网）：直接复用保存的 token / 学校 token，
         * 供"进页面先用缓存开门、后台再静默同步"的离线优先流程使用。
         * token 是否仍然有效由首次业务请求暴露（见 [YunmeiAuthException]）。
         */
        fun restore(
            saved: CampusSaved,
            post: suspend (url: String, form: Map<String, String>, headers: Map<String, String>) -> String,
        ): YunmeiClient {
            val client = YunmeiClient(post)
            client.session = YmSession(
                account = saved.account,
                accountMd5 = md5(saved.account),
                passwordMd5 = saved.passwordMd5,
                userId = saved.userId,
            ).also { it.token = saved.token }
            if (saved.schoolNo.isNotBlank()) {
                client.selectSchool(
                    YmSchool(
                        schoolNo = saved.schoolNo,
                        name = saved.schoolName,
                        serverUrl = saved.serverUrl,
                        token = saved.schoolToken,
                    )
                )
            }
            return client
        }
    }

    /** 选定学校：后续业务请求发往该校服务器并用其 token。 */
    fun selectSchool(school: YmSchool) {
        schoolToken = school.token
        this.school = school
    }

    /** 门锁列表（楼栋-宿舍名 + BLE 参数）。 */
    suspend fun getLocks(): List<YmLock> {
        val s = session ?: error("未登录")
        val schoolNo = school?.schoolNo ?: error("未选择学校")
        val arr = org.json.JSONArray(
            postForm("/dormuser/getuserlock", mapOf("schoolNo" to schoolNo, "userId" to s.userId))
        )
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val r = arr.getJSONObject(i)
                YmLock(
                    label = "${r.getString("buildName")}-${r.getString("dormNo")}",
                    serviceUuid = r.getString("lockServiceUuid"),
                    writeCharUuid = r.getString("lockCharacterUuid"),
                    notifyCharUuid = r.getString("lockCharacterUuid").replace("6E400002", "6E400003"),
                    secret = r.getString("lockSecret"),
                    mac = r.getString("lockNo"),
                )
            }.getOrNull()
        }
    }
}

open class YunmeiException(message: String) : Exception(message)

/**
 * 鉴权失败（token 过期、账号密码错误、服务端 success=false）。
 * 只有这一类失败才允许降级：先尝试用本机密码 MD5 静默重登，仍失败才回登录表单。
 */
class YunmeiAuthException(message: String) : YunmeiException(message)

/**
 * 网络不可达 / 超时。凭据与门锁缓存必须原样保留，只标记离线并给出重试入口——
 * 旧版在这里一律 `store.clear()`，导致断一次网就要重新输密码。
 */
class YunmeiNetworkException(message: String) : YunmeiException(message)
