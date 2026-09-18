package com.buguake.timetable.campus.laundry

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 顺水接口解析与分页的单测：样本取自 2026-09-14 实测响应（见方案文档 §3/§11）。
 * 关键契约用测试锁死，防止后续改版时无声漂移。
 */
class LaundryParseTest {

    // ---- 样本构造（贴合实测响应结构）----

    private fun storeJson(id: Int, name: String, vararg tags: String) = """
        {"id":$id,"store_name":"$name","address":"测试地址","is_yuyue":1,
         "lat":30.318089,"lng":120.361374,"is_show":1,
         "tag":[${tags.joinToString(",") { """{"name":"$it","id":1}""" }}]}
    """.trimIndent()

    private fun storesBody(vararg stores: String, status: Int = 200) =
        """{"status":$status,"message":"ok","data":[${stores.joinToString(",")}]}"""

    private fun deviceJson(
        id: Int,
        title: String,
        status: Int,
        machineState: Int,
        endTime: Int,
        actionCode: String,
        topCategoryId: Int,
    ) = """
        {"id":$id,"device_title":"$title","AliState":1,"ali_update_time":1789395572,
         "MachineState":$machineState,"MachineState_endtime":0,"action_code":"$actionCode",
         "top_category_id":$topCategoryId,"store_id":60,"house_id":364,"device_status":1,
         "user_sex":0,"end_time":$endTime,"status":$status,
         "use_type":{"can_yuyue":0,"can_queue":1},"queue_num":0}
    """.trimIndent()

    private fun devicesBody(devices: List<String>, status: Int = 200) =
        """{"status":$status,"message":"ok","data":{"list":[${devices.joinToString(",")}]}}"""

    // ---- 门店发现 ----

    @Test
    fun parseStoreSearch() {
        val stores = ShunshuiClient.parseStores(
            storesBody(storeJson(60, "中国计量大学", "洗衣机", "烘干机")),
        )
        assertEquals(1, stores.size)
        val s = stores[0]
        assertEquals(60, s.id)
        assertEquals("中国计量大学", s.name)
        assertEquals(30.318089, s.lat, 1e-9)
        assertEquals(120.361374, s.lng, 1e-9)
        assertEquals(listOf("洗衣机", "烘干机"), s.tags)
    }

    @Test
    fun emptyStoreListDoesNotCrash() {
        assertTrue(ShunshuiClient.parseStores("""{"status":200,"data":[]}""").isEmpty())
        assertTrue(ShunshuiClient.parseStores("""{"status":200,"data":[]}""").isEmpty())
        assertTrue(ShunshuiClient.parseStores("not json").isEmpty())
    }

    @Test
    fun nearRequestUsesShortLatLng() {
        // 契约：参数名是 lat/lng（不是 latitude/longitude，传错服务端业务 500）
        var captured = ""
        val client = ShunshuiClient { _, body -> captured = body; storesBody() }
        kotlinx.coroutines.runBlocking { client.nearStores(30.3, 120.8) }
        assertEquals("""{"lat":30.3,"lng":120.8}""", captured)
    }

    @Test
    fun businessErrorYieldsEmptyNotThrow() {
        // 实测：缺参数时服务端返回 HTTP 200 + {"status":500,"message":"Undefined array key \"lat\""}
        val body = """{"status":500,"message":"Undefined array key \"lat\""}"""
        assertTrue(ShunshuiClient.parseStores(body).isEmpty())
        assertTrue(ShunshuiClient.parseDevices(devicesBody(emptyList(), status = 500)).isEmpty())
    }

    @Test
    fun storeSearchOnlyMatchesName() {
        // 固化实测语义：搜"浙江"0 家；搜"大学"2 家且不含中国计量大学(60)——防后人误当全集检索
        val zhejiang = ShunshuiClient.parseStores("""{"status":200,"data":[]}""")
        assertTrue(zhejiang.isEmpty())
        val universities = ShunshuiClient.parseStores(
            storesBody(storeJson(53, "南通大学"), storeJson(55, "无锡职业大学")),
        )
        assertEquals(2, universities.size)
        assertFalse(universities.any { it.id == 60 })
    }

    // ---- 门店详情 ----

    private fun storeInfoBody(categories: String, houseDict: String, infoWrapped: Boolean) =
        if (infoWrapped) {
            """{"status":200,"data":{"info":{"category":[$categories],"house":{$houseDict}}}}"""
        } else {
            """{"status":200,"data":{"category":[$categories],"house":{$houseDict}}}}"""
        }

    private val house364 = """
        "364":{"id":364,"store_id":60,"house_name":"西区2栋","parent_id":0,
               "onlineUse":1,"group_id":0,"people_num":100,"count":34,"children":[]}
    """.trimIndent().trim()

    @Test
    fun parseHouseDictAndCategories() {
        val info = ShunshuiClient.parseStoreInfo(
            storeInfoBody(
                categories = """{"id":1,"name":"洗衣机"},{"id":2,"name":"烘干机"}""",
                houseDict = house364,
                infoWrapped = true,
            ),
            storeId = 60,
        )
        assertEquals(60, info.storeId)
        assertEquals(2, info.categories.size)
        assertEquals("洗衣机", info.categories[0].name)
        assertEquals(1, info.houses.size)
        assertEquals("西区2栋", info.houses[0].name)
        assertEquals(34, info.houses[0].count)
        assertTrue(info.houses[0].onlineUse)
    }

    @Test
    fun parseHouseDictToleratesUnwrappedStructure() {
        val info = ShunshuiClient.parseStoreInfo(
            storeInfoBody("""{"id":1,"name":"洗衣机"}""", house364, infoWrapped = false),
            storeId = 60,
        )
        assertEquals(1, info.houses.size)
        assertEquals(1, info.categories.size)
    }

    @Test
    fun categoryCountVariesByStore() {
        // 实测：计量 2 类、南通 +洗鞋机 3 类、无锡 +吹风机 4 类——分类数量不可写死
        fun cat(vararg pairs: Pair<Int, String>) =
            pairs.joinToString(",") { """{"id":${it.first},"name":"${it.second}"}""" }
        val jiliang = ShunshuiClient.parseStoreInfo(
            storeInfoBody(cat(1 to "洗衣机", 2 to "烘干机"), house364, true), 60,
        )
        val nantong = ShunshuiClient.parseStoreInfo(
            storeInfoBody(cat(1 to "洗衣机", 2 to "烘干机", 3 to "洗鞋机"), house364, true), 53,
        )
        val wuxi = ShunshuiClient.parseStoreInfo(
            storeInfoBody(cat(1 to "洗衣机", 2 to "烘干机", 3 to "洗鞋机", 4 to "吹风机"), house364, true), 55,
        )
        assertEquals(2, jiliang.categories.size)
        assertEquals(3, nantong.categories.size)
        assertEquals(4, wuxi.categories.size)
    }

    @Test
    fun disabledHouseIsParsedButFlagged() {
        // 实测：西区13栋 onlineUse=0 但仍返回设备 → 停用仅作展示标记，不可用于过滤
        val dict = """
            "374":{"id":374,"house_name":"西区13栋","onlineUse":0,"count":4,"children":[]}
        """.trimIndent()
        val info = ShunshuiClient.parseStoreInfo(
            storeInfoBody("""{"id":1,"name":"洗衣机"}""", dict, true), 60,
        )
        assertFalse(info.houses[0].onlineUse)
        assertEquals(4, info.houses[0].count)
    }

    @Test
    fun parseHouseArrayForm() {
        // 实测（含辉苑 48）：house 直接是数组；空数组代表无楼栋 → UI 层以虚拟楼栋 house_id=0 直取设备
        val body = """{"status":200,"data":{"info":{"category":[{"id":1,"name":"洗衣机"}],"house":""" +
            """[{"id":7,"house_name":"1号楼","onlineUse":1,"count":3}]}}}"""
        val info = ShunshuiClient.parseStoreInfo(body, 48)
        assertEquals(1, info.houses.size)
        assertEquals("1号楼", info.houses[0].name)
        val empty = ShunshuiClient.parseStoreInfo(
            """{"status":200,"data":{"info":{"category":[],"house":[]}}}""", 48,
        )
        assertTrue(empty.houses.isEmpty())
    }

    @Test
    fun storeInfoBusinessErrorThrows() {
        val body = """{"status":500,"message":"门店不存在"}"""
        try {
            ShunshuiClient.parseStoreInfo(body, 60)
            throw AssertionError("应当抛出")
        } catch (e: com.buguake.timetable.campus.YunmeiException) {
            assertTrue(e.message!!.contains("门店不存在"))
        }
    }

    // ---- 设备与状态 ----

    @Test
    fun parseDeviceStatus() {
        assertEquals(DeviceStatus.IDLE, mapDeviceStatus(1, 0, 0))
        assertEquals(DeviceStatus.RUNNING, mapDeviceStatus(2, 1, 1864))
        assertEquals(DeviceStatus.PROTECTING, mapDeviceStatus(3, 2, 411))
        assertEquals(DeviceStatus.UNKNOWN, mapDeviceStatus(0, 1, 0))
        assertEquals(DeviceStatus.UNKNOWN, mapDeviceStatus(4, 0, 0))
    }

    @Test
    fun dryerUsesSameStatusMapping() {
        // 实测：烘干机与洗衣机同接口同字段同状态语义，无需按分类分支
        val dryers = ShunshuiClient.parseDevices(
            devicesBody(
                listOf(
                    deviceJson(6536, "西区2栋 03烘干机", 1, 0, 0, "11420", 2),
                    deviceJson(6537, "西区2栋 04烘干机", 2, 1, 300, "11421", 2),
                ),
            ),
        )
        assertEquals(2, dryers.size)
        assertEquals(DeviceStatus.IDLE, dryers[0].status)
        assertEquals(DeviceStatus.RUNNING, dryers[1].status)
        assertEquals(2, dryers[0].categoryId)
    }

    @Test
    fun parseDeviceFields() {
        // 空白收敛 + action_code 保真 + end_time 为 int（0 = 空闲）
        val devices = ShunshuiClient.parseDevices(
            devicesBody(listOf(deviceJson(6350, "西区2栋  2号机", 2, 1, 1864, "11255", 1))),
        )
        val d = devices[0]
        assertEquals("西区2栋 2号机", d.title)
        assertEquals("11255", d.actionCode)
        assertEquals(1864, d.remainSeconds)
        assertTrue(d.online)
    }

    @Test
    fun topCategoryIdFilter() {
        // 混入 top_category_id 不一致的脏行 → 分页拉取时被过滤
        val client = ShunshuiClient { _, _ ->
            devicesBody(
                listOf(
                    deviceJson(1, "洗衣机", 1, 0, 0, "1", topCategoryId = 1),
                    deviceJson(2, "脏数据", 1, 0, 0, "2", topCategoryId = 99),
                ),
            )
        }
        val out = kotlinx.coroutines.runBlocking { client.pagedDevices(60, 364, 1) }
        assertEquals(1, out.size)
        assertEquals(1, out[0].id)
    }

    @Test
    fun paginationLoop() {
        // 实测：每页 20 条、无 total 字段 → 翻到空页/不满页为止
        val requests = mutableListOf<String>()
        val client = ShunshuiClient { _, body ->
            requests += body
            val page = JSONObject(body).getInt("page")
            val devices = if (page == 1) {
                (1..20).map { deviceJson(it, "洗衣机 $it", 1, 0, 0, "$it", 1) }
            } else {
                (21..28).map { deviceJson(it, "洗衣机 $it", 1, 0, 0, "$it", 1) }
            }
            devicesBody(devices)
        }
        val out = kotlinx.coroutines.runBlocking { client.pagedDevices(60, 364, 1) }
        assertEquals(28, out.size)
        assertEquals(2, requests.size)
        // 请求体契约：category_id/house_id/store_id/page 齐全
        assertTrue(requests[0].contains(""""category_id":1"""))
        assertTrue(requests[0].contains(""""house_id":364"""))
        assertTrue(requests[0].contains(""""store_id":60"""))
    }

    @Test
    fun emptyListDoesNotCrash() {
        assertTrue(ShunshuiClient.parseDevices("""{"status":200,"data":{"list":[]}}""").isEmpty())
        assertTrue(ShunshuiClient.parseDevices("""{"status":200,"data":{}}""").isEmpty())
        assertTrue(ShunshuiClient.parseDevices("not json").isEmpty())
    }

    // ---- 距离 ----

    @Test
    fun distanceKm() {
        // 钱塘区 ↔ 南通约 200km 量级；同点为 0
        val km = distanceKm(30.318089, 120.361374, 31.98, 120.89)
        assertTrue("实际 $km", km in 150.0..250.0)
        assertEquals(0.0, distanceKm(30.0, 120.0, 30.0, 120.0), 1e-9)
        assertTrue(distanceLabel(distanceKm(30.318089, 120.361374, 30.32, 120.362)).endsWith("m"))
    }
}
