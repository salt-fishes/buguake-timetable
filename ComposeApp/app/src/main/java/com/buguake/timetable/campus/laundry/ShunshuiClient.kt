package com.buguake.timetable.campus.laundry

import com.buguake.timetable.campus.YunmeiException
import org.json.JSONArray
import org.json.JSONObject

/**
 * 顺水洗衣平台只读客户端（门店发现 / 楼栋 / 逐分类设备状态）。
 *
 * - 免 token、无签名，只需 JSON POST；HTTP 层恒 200，业务状态在响应体 `status` 字段（200=成功）；
 * - 只读：不调用任何下单/支付/设备控制接口，下单跳官方小程序；
 * - 接口可能随官方改版失效，全部 HTTP 收敛在本类，单测锁契约。
 */
class ShunshuiClient(
    private val post: suspend (url: String, jsonBody: String) -> String,
) {

    /** 附近门店（主通道）：参数名是短名 lat/lng（传全称服务端会报业务 500）。按距离升序返回。 */
    suspend fun nearStores(lat: Double, lng: Double): List<LaundryStoreItem> =
        parseStores(post("$BASE_URL/wash/store/near", """{"lat":$lat,"lng":$lng}"""))

    /**
     * 按门店名搜索（兜底）：只匹配门店名子串、忽略分页、结果严重不全
     * （实测搜"大学"漏掉含"大学"的本店），不可当门店全集用。
     */
    suspend fun searchStores(keyword: String): List<LaundryStoreItem> =
        parseStores(
            post("$BASE_URL/wash/store/search", """{"searchval":${JSONObject.quote(keyword)}}"""),
        )

    /** 门店详情 → 分类 + 楼栋。注意本接口参数用全称 latitude/longitude，与 near 的 lat/lng 不同。 */
    suspend fun storeInfo(store: LaundryStoreItem): LaundryStoreInfo =
        parseStoreInfo(
            post(
                "$BASE_URL/wash/store/info",
                """{"id":${store.id},"latitude":${store.lat},"longitude":${store.lng}}""",
            ),
            store.id,
        )

    /** 逐分类拉全某楼栋设备（每类各自分页，翻到空页为止）。 */
    suspend fun devicesByCategory(
        storeId: Int,
        houseId: Int,
        categories: List<LaundryCategory>,
    ): Map<Int, List<LaundryDevice>> =
        categories.associate { it.id to pagedDevices(storeId, houseId, it.id) }

    internal suspend fun pagedDevices(storeId: Int, houseId: Int, categoryId: Int): List<LaundryDevice> {
        val out = mutableListOf<LaundryDevice>()
        var page = 1
        while (page <= MAX_PAGES) {
            val body = post(
                "$BASE_URL/wash/device/list",
                """{"category_id":$categoryId,"house_id":$houseId,"store_id":$storeId,"page":$page}""",
            )
            val list = parseDevices(body)
            // 脏数据过滤：只收 top_category_id 与请求一致的行
            out += list.filter { it.categoryId == categoryId }
            if (list.size < PAGE_SIZE) break
            page++
        }
        return out
    }

    companion object {
        const val BASE_URL = "https://batch.shunshuikj.cn"
        const val PAGE_SIZE = 20
        const val MAX_PAGES = 10

        fun default() = ShunshuiClient { url, body -> com.buguake.timetable.campus.httpPostJson(url, body) }

        /** 业务状态码：非 200 返回 null（部分接口失败时 data 直接是空列表，调用方按空处理）。 */
        internal fun businessStatus(body: String): Int? = runCatching {
            JSONObject(body).optInt("status", -1).takeIf { it >= 0 }
        }.getOrNull()

        /** 解析门店列表（near / search 共用）。业务失败或空数据 → 空列表，不抛异常。 */
        internal fun parseStores(body: String): List<LaundryStoreItem> {
            if (businessStatus(body) != 200) return emptyList()
            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
                ?: return emptyList()
            return (0 until data.length()).mapNotNull { i ->
                runCatching {
                    val o = data.getJSONObject(i)
                    LaundryStoreItem(
                        id = o.getInt("id"),
                        name = o.optString("store_name"),
                        address = o.optString("address"),
                        lat = o.optDouble("lat", 0.0),
                        lng = o.optDouble("lng", 0.0),
                        tags = o.optJSONArray("tag")?.let { tag ->
                            (0 until tag.length()).mapNotNull { j ->
                                tag.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }
                            }
                        } ?: emptyList(),
                    )
                }.getOrNull()
            }
        }

        /** 解析门店详情：分类 + 楼栋。楼栋字典在 data.info.house（兼容 data.house 两种结构）。 */
        internal fun parseStoreInfo(body: String, storeId: Int): LaundryStoreInfo {
            val status = businessStatus(body)
            if (status != 200) {
                val msg = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
                throw YunmeiException(msg.ifBlank { "门店信息获取失败（业务码 $status）" })
            }
            val root = runCatching { JSONObject(body).optJSONObject("data") }.getOrNull()
                ?: return LaundryStoreInfo(storeId, emptyList(), emptyList())
            val info = root.optJSONObject("info") ?: root
            val categories = info.optJSONArray("category")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let {
                        LaundryCategory(it.optInt("id"), it.optString("name"))
                    }
                }
            } ?: emptyList()
            val houseDict = info.optJSONObject("house") ?: root.optJSONObject("house")
            val houses = mutableListOf<LaundryHouse>()
            houseDict?.keys()?.forEach { key ->
                flattenHouse(houseDict.optJSONObject(key), houses)
            }
            return LaundryStoreInfo(storeId, categories, houses)
        }

        /** 楼栋字典递归展开（children 为子楼栋时一并铺平）。 */
        private fun flattenHouse(o: JSONObject?, out: MutableList<LaundryHouse>) {
            o ?: return
            val id = o.optInt("id", 0)
            val name = o.optString("house_name")
            if (id != 0 && name.isNotBlank()) {
                out += LaundryHouse(
                    id = id,
                    name = name,
                    onlineUse = o.optInt("onlineUse", 1) == 1,
                    count = o.optInt("count", 0),
                )
            }
            o.optJSONArray("children")?.let { children: JSONArray ->
                (0 until children.length()).forEach { i ->
                    flattenHouse(children.optJSONObject(i), out)
                }
            }
        }

        /** 解析一页设备列表。业务失败或空数据 → 空列表（同时是分页终止条件）。 */
        internal fun parseDevices(body: String): List<LaundryDevice> {
            if (businessStatus(body) != 200) return emptyList()
            val list = runCatching { JSONObject(body).optJSONObject("data")?.optJSONArray("list") }
                .getOrNull()
                ?: return emptyList()
            return (0 until list.length()).mapNotNull { i ->
                runCatching {
                    val o = list.getJSONObject(i)
                    LaundryDevice(
                        id = o.optInt("id"),
                        // 原始 device_title 空白不规范（"西区2栋  10号机"），渲染前收敛
                        title = o.optString("device_title").replace(Regex("\\s+"), " ").trim(),
                        actionCode = o.optString("action_code"),
                        status = mapDeviceStatus(
                            o.optInt("status", -1),
                            o.optInt("MachineState", -1),
                            o.optInt("end_time", 0),
                        ),
                        online = o.optInt("device_status", 0) == 1,
                        remainSeconds = o.optInt("end_time", 0),
                        categoryId = o.optInt("top_category_id", 0),
                        queueNum = o.optInt("queue_num", 0),
                    )
                }.getOrNull()
            }
        }
    }
}
