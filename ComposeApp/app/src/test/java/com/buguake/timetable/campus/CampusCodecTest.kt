package com.buguake.timetable.campus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 缓存 bundle 的编解码：往返一致、脏数据不崩、按需过滤无效门锁。 */
class CampusCodecTest {

    private fun lock(label: String, mac: String = "AA:BB:CC:DD:EE:FF") = YmLock(
        label = label,
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        writeCharUuid = "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
        notifyCharUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
        secret = "s3cr3t-$label",
        mac = mac,
    )

    @Test
    fun `encode then decode keeps everything needed for offline unlock`() {
        val saved = CampusSaved(
            account = "20230001",
            passwordMd5 = "d41d8cd98f00b204e9800998ecf8427e",
            userId = "u-1",
            token = "t-1",
            schoolNo = "1001",
            schoolName = "示例大学",
            serverUrl = "https://school.example.com/",
            schoolToken = "st-1",
            locks = listOf(lock("1号楼-101"), lock("2号楼-202", mac = "")),
            defaultLabel = "2号楼-202",
            updatedAt = 1_700_000_000_000L,
        )

        val decoded = CampusCodec.decode(CampusCodec.encode(saved))

        assertEquals(saved, decoded)
        // 默认门锁与排序：默认项排首位
        assertEquals("2号楼-202", decoded?.defaultLock?.label)
        assertEquals(
            listOf("2号楼-202", "1号楼-101"),
            decoded?.orderedLocks?.map { it.label },
        )
    }

    @Test
    fun `decode filters locks without label or service uuid`() {
        val json = """
            {"v":1,"account":"a","pwdMd5":"p","schoolNo":"1","schoolName":"s",
             "locks":[{"label":"","serv":"x"},{"label":"A","serv":"","secret":"s"},
                      {"label":"B","serv":"svc","write":"w","notify":"n","secret":"sec","mac":"m"}]}
        """.trimIndent()

        val decoded = CampusCodec.decode(json)

        assertEquals(listOf("B"), decoded?.locks?.map { it.label })
    }

    @Test
    fun `decode returns null for broken payload instead of throwing`() {
        assertNull(CampusCodec.decode("not json at all"))
        assertNull(CampusCodec.decode(""))
    }

    @Test
    fun `legacy payload without locks still decodes to credentials only`() {
        val json = """{"v":1,"account":"a","pwdMd5":"p","userId":"u","token":"t",
            "schoolNo":"1","schoolName":"s","serverUrl":"https://x/","schoolToken":"st"}"""

        val decoded = CampusCodec.decode(json)

        assertEquals("a", decoded?.account)
        assertTrue(decoded!!.locks.isEmpty())
        assertNull(decoded.defaultLock)
    }
}
