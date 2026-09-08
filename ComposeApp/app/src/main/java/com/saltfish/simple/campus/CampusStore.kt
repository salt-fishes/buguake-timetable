package com.saltfish.simple.campus

import android.content.Context

/**
 * 云莓凭据与会话的本地存储。
 * 账号与密码 MD5 仅存本机 SharedPreferences，使用时直连云莓服务器，不经任何中转。
 */
class CampusStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("campus_prefs", Context.MODE_PRIVATE)

    data class Saved(
        val account: String,
        val passwordMd5: String,
        val userId: String,
        val token: String,
        val schoolNo: String,
        val schoolName: String,
        val serverUrl: String,
        val schoolToken: String,
    )

    fun save(s: Saved) {
        prefs.edit()
            .putString(K_ACCOUNT, s.account)
            .putString(K_PWD_MD5, s.passwordMd5)
            .putString(K_USER_ID, s.userId)
            .putString(K_TOKEN, s.token)
            .putString(K_SCHOOL_NO, s.schoolNo)
            .putString(K_SCHOOL_NAME, s.schoolName)
            .putString(K_SERVER_URL, s.serverUrl)
            .putString(K_SCHOOL_TOKEN, s.schoolToken)
            .apply()
    }

    fun load(): Saved? {
        val account = prefs.getString(K_ACCOUNT, null) ?: return null
        return Saved(
            account = account,
            passwordMd5 = prefs.getString(K_PWD_MD5, "") ?: "",
            userId = prefs.getString(K_USER_ID, "") ?: "",
            token = prefs.getString(K_TOKEN, "") ?: "",
            schoolNo = prefs.getString(K_SCHOOL_NO, "") ?: "",
            schoolName = prefs.getString(K_SCHOOL_NAME, "") ?: "",
            serverUrl = prefs.getString(K_SERVER_URL, "") ?: "",
            schoolToken = prefs.getString(K_SCHOOL_TOKEN, "") ?: "",
        ).takeIf { it.passwordMd5.isNotBlank() && it.schoolNo.isNotBlank() }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val K_ACCOUNT = "account"
        private const val K_PWD_MD5 = "password_md5"
        private const val K_USER_ID = "user_id"
        private const val K_TOKEN = "token"
        private const val K_SCHOOL_NO = "school_no"
        private const val K_SCHOOL_NAME = "school_name"
        private const val K_SERVER_URL = "server_url"
        private const val K_SCHOOL_TOKEN = "school_token"

        @Volatile private var INSTANCE: CampusStore? = null

        fun getInstance(context: Context): CampusStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CampusStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}
