package com.buguake.timetable.campus

import android.content.Context

/**
 * 云莓凭据与门锁参数的本地存储（离线开门的唯一数据源）。
 *
 * - 凭据 + 门锁参数打包成一个 bundle，用 [LocalCrypto]（Keystore AES/GCM）加密后写入；
 * - 旧版本分散 key 的明文数据在读取时自动迁移（首次同步后即升级为加密 bundle）；
 * - 扫描学到的真实 MAC 仍按 `mac_<门锁名>` 单独存放：它与账号无关，退出登录不清除；
 * - 「开门后自动退出」「开门前验证」两个开关不含敏感信息，单独存放。
 */
class CampusStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("campus_prefs", Context.MODE_PRIVATE)
    private val crypto = LocalCrypto()

    /** 开门成功后自动退出应用（默认关）。 */
    var autoExitOnUnlock: Boolean
        get() = prefs.getBoolean(K_AUTO_EXIT, false)
        set(value) {
            prefs.edit().putBoolean(K_AUTO_EXIT, value).apply()
        }

    /** 开门前验证指纹/面容（默认关）。 */
    var gateEnabled: Boolean
        get() = prefs.getBoolean(K_GATE, false)
        set(value) {
            prefs.edit().putBoolean(K_GATE, value).apply()
        }

    /** 上次登录过的账号：退出登录后仍保留，用于输入框预填。 */
    fun lastAccount(): String = prefs.getString(K_LAST_ACCOUNT, "") ?: ""

    /** 读取缓存；无缓存/解密失败返回 null（调用方走登录流程）。 */
    fun load(): CampusSaved? {
        val saved = prefs.getString(K_BUNDLE, null)
            ?.let { crypto.decrypt(it) }
            ?.let { CampusCodec.decode(it) }
            ?: loadLegacy()
            ?: return null
        return saved.takeIf { it.passwordMd5.isNotBlank() && it.schoolNo.isNotBlank() }
    }

    /** 覆盖写入完整 bundle（登录成功、手动同步成功后调用）。 */
    fun save(saved: CampusSaved) {
        prefs.edit()
            .putString(K_BUNDLE, crypto.encrypt(CampusCodec.encode(saved)))
            .putString(K_LAST_ACCOUNT, saved.account)
            .apply()
    }

    /** 只更新门锁缓存（后台静默同步成功后调用），凭据保持不变。 */
    fun saveLocks(locks: List<YmLock>, defaultLabel: String, updatedAt: Long) {
        val cur = load() ?: return
        save(cur.copy(locks = locks, defaultLabel = defaultLabel, updatedAt = updatedAt))
    }

    /** 设置默认门锁（开门快捷方式与列表首项都用它）。 */
    fun setDefaultLock(label: String) {
        val cur = load() ?: return
        save(cur.copy(defaultLabel = label))
    }

    /** 扫描连接成功后记住真实 MAC，下次即可跳过扫描快速开门。 */
    fun saveLearnedMac(label: String, mac: String) {
        if (label.isBlank() || mac.isBlank()) return
        prefs.edit().putString(K_MAC_PREFIX + label, mac).apply()
    }

    /** 已学到的真实 MAC；未学过返回空串。 */
    fun learnedMac(label: String): String = prefs.getString(K_MAC_PREFIX + label, "") ?: ""

    /**
     * 清空凭据与门锁缓存（「退出登录」/鉴权彻底失效）。
     * 保留：上次账号（预填）、两个开关、已学到的真实 MAC。
     */
    fun clearCredentials() {
        prefs.edit()
            .remove(K_BUNDLE)
            .remove(K_ACCOUNT).remove(K_PWD_MD5).remove(K_USER_ID).remove(K_TOKEN)
            .remove(K_SCHOOL_NO).remove(K_SCHOOL_NAME).remove(K_SERVER_URL).remove(K_SCHOOL_TOKEN)
            .apply()
    }

    /** 旧版（明文分散 key）数据读取，用于升级迁移。 */
    private fun loadLegacy(): CampusSaved? {
        val account = prefs.getString(K_ACCOUNT, null) ?: return null
        return CampusSaved(
            account = account,
            passwordMd5 = prefs.getString(K_PWD_MD5, "") ?: "",
            userId = prefs.getString(K_USER_ID, "") ?: "",
            token = prefs.getString(K_TOKEN, "") ?: "",
            schoolNo = prefs.getString(K_SCHOOL_NO, "") ?: "",
            schoolName = prefs.getString(K_SCHOOL_NAME, "") ?: "",
            serverUrl = prefs.getString(K_SERVER_URL, "") ?: "",
            schoolToken = prefs.getString(K_SCHOOL_TOKEN, "") ?: "",
        )
    }

    companion object {
        private const val K_BUNDLE = "campus_bundle_v1"
        private const val K_LAST_ACCOUNT = "last_account"
        private const val K_AUTO_EXIT = "auto_exit"
        private const val K_GATE = "gate_biometric"
        private const val K_MAC_PREFIX = "mac_"

        // 旧版 key（迁移用，写入只写 bundle）
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
