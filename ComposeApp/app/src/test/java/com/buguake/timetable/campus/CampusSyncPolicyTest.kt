package com.buguake.timetable.campus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线优先策略：**只有鉴权失败才允许清凭据**；网络失败必须保留缓存与凭据。
 * 这是修复"断一次网就要重新输密码"的核心不变量。
 */
class CampusSyncPolicyTest {

    private fun saved(locks: Int) = CampusSaved(
        account = "20230001",
        passwordMd5 = "pwd-md5",
        userId = "u",
        token = "t",
        schoolNo = "1001",
        schoolName = "示例大学",
        serverUrl = "https://x/",
        schoolToken = "st",
        locks = (1..locks).map {
            YmLock(
                label = "锁$it",
                serviceUuid = "svc",
                writeCharUuid = "w",
                notifyCharUuid = "n",
                secret = "sec",
                mac = "",
            )
        },
    )

    @Test
    fun `network failure keeps local credentials`() {
        val failure = CampusSyncPolicy.classify(YunmeiNetworkException("连不上"))
        assertEquals(CampusFailure.NETWORK, failure)
        assertTrue(CampusSyncPolicy.keepCredentials(failure))
        // 旧实现正是在这里清空凭据，导致每次都要重新输密码
        assertFalse(CampusSyncPolicy.keepCredentials(CampusFailure.AUTH))
    }

    @Test
    fun `auth failures are classified separately from unknown ones`() {
        assertEquals(CampusFailure.AUTH, CampusSyncPolicy.classify(YunmeiAuthException("密码错误")))
        assertEquals(CampusFailure.UNKNOWN, CampusSyncPolicy.classify(IllegalStateException("boom")))
        assertEquals(CampusFailure.NONE, CampusSyncPolicy.classify(null))
        // 子类关系不能反过来把鉴权失败当成网络失败
        assertTrue(YunmeiAuthException("x") is YunmeiException)
        assertTrue(YunmeiNetworkException("x") is YunmeiException)
    }

    @Test
    fun `empty result with local cache is treated as suspicious token failure`() {
        assertTrue(CampusSyncPolicy.suspectTokenFailure(cachedCount = 2, fetchedCount = 0))
        assertFalse(CampusSyncPolicy.suspectTokenFailure(cachedCount = 0, fetchedCount = 0))
        assertFalse(CampusSyncPolicy.suspectTokenFailure(cachedCount = 2, fetchedCount = 2))
    }

    @Test
    fun `initial mode prefers cache then silent then form`() {
        assertEquals(CampusSyncPolicy.Mode.FORM, CampusSyncPolicy.initialMode(null))
        // 有凭据但还没有门锁缓存（首次升级/上次同步失败）：静默同步，不要求输密码
        assertEquals(CampusSyncPolicy.Mode.SILENT, CampusSyncPolicy.initialMode(saved(0)))
        assertEquals(CampusSyncPolicy.Mode.CACHE, CampusSyncPolicy.initialMode(saved(2)))
    }

    @Test
    fun `after auth failure only retry silently when password md5 is still stored`() {
        assertEquals(CampusSyncPolicy.Mode.SILENT, CampusSyncPolicy.afterAuthFailure(true))
        assertEquals(CampusSyncPolicy.Mode.FORM, CampusSyncPolicy.afterAuthFailure(false))
    }
}
