package com.buguake.timetable.campus.laundry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 跳转 scheme 构造：与官方落地页复刻样例逐字符比对（实测样本 code=11477 / 11420）。 */
class LaundryJumpTest {

    @Test
    fun buildWechatScheme() {
        assertEquals(
            "weixin://dl/business/?appid=wx50e9776c81087219" +
                "&path=pages/device/togo&query=code%3D11477&env_version=release",
            LaundryJump.wechatScheme("11477"),
        )
    }

    @Test
    fun buildAlipayScheme() {
        assertEquals(
            "alipays://platformapi/startapp?appId=2021004146616939" +
                "&page=pages/device/togo&query=code%3D11477",
            LaundryJump.alipayScheme("11477"),
        )
    }

    @Test
    fun dryerSchemeSharesSameStructure() {
        // 烘干机与洗衣机共用同一跳转，仅 code 不同
        assertEquals(
            LaundryJump.wechatScheme("11420").replace("11420", "11477"),
            LaundryJump.wechatScheme("11477"),
        )
        assertEquals(
            LaundryJump.alipayScheme("11420").replace("11420", "11477"),
            LaundryJump.alipayScheme("11477"),
        )
    }

    @Test
    fun h5Fallbacks() {
        assertEquals(
            "https://batch.shunshuikj.cn/scan?code=11477",
            LaundryJump.wechatH5("11477"),
        )
        assertTrue(LaundryJump.alipayH5("11477").startsWith("https://ulink.alipay.com/?scheme="))
    }
}
