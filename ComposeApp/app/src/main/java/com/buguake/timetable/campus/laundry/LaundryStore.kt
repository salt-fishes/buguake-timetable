package com.buguake.timetable.campus.laundry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 洗衣房本地存储（SharedPreferences，**非敏感**：无账号、无 token、无定位轨迹）。
 *
 * key 按门店/楼栋隔离，换门店不串数据；定位坐标只在「附近门店」当次请求使用，
 * 仅 [LaundryStoreItem] 里已选门店的公开坐标会随门店对象落盘。
 */
class LaundryStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("laundry_prefs", Context.MODE_PRIVATE)

    // ---- 默认门店与最近选择 ----

    /** 用户选定的默认门店（唯一事实源）；未选过返回 null（进门店选择页）。 */
    fun defaultStore(): LaundryStoreItem? =
        prefs.getString(K_DEFAULT_STORE, null)?.let { LaundryStoreItem.decode(it) }

    /** 选定门店：整体写入默认，并记入最近选择（去重、最多 [MAX_RECENT] 家）。 */
    fun setDefaultStore(item: LaundryStoreItem) {
        prefs.edit()
            .putString(K_DEFAULT_STORE, item.encode())
            .putString(K_RECENT, encodeRecent(listOf(item) + recentStores()).toString())
            .apply()
    }

    /** 最近选择的门店（供门店选择页"最近选择"快速切换）。 */
    fun recentStores(): List<LaundryStoreItem> =
        prefs.getString(K_RECENT, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { decodeRecent(it) }
            ?: emptyList()

    /** 清除默认门店（"换门店"时重选用；最近选择保留）。 */
    fun clearDefaultStore() {
        prefs.edit().remove(K_DEFAULT_STORE).apply()
    }

    /** 上次搜索关键词（仅用于预填输入框，不作为默认门店）。 */
    var lastKeyword: String
        get() = prefs.getString(K_LAST_KEYWORD, "") ?: ""
        set(value) { prefs.edit().putString(K_LAST_KEYWORD, value).apply() }

    // ---- 附近门店候选缓存（离线时仍可展示与切换）----

    fun saveCandidates(stores: List<LaundryStoreItem>) {
        prefs.edit().putString(
            K_CANDIDATES,
            JSONObject()
                .put("data", encodeRecent(stores))
                .put("fetchedAt", System.currentTimeMillis())
                .toString(),
        ).apply()
    }

    /** 上次「附近门店」结果与时间；无缓存返回 null。 */
    fun loadCandidates(): Pair<List<LaundryStoreItem>, Long>? {
        val o = prefs.getString(K_CANDIDATES, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val list = decodeRecent(o)
        return if (list.isEmpty()) null else list to o.optLong("fetchedAt", 0L)
    }

    // ---- 门店详情缓存（分类 + 楼栋，按门店隔离；离线可用）----

    fun saveStoreInfo(storeId: Int, info: LaundryStoreInfo) {
        val houses = JSONArray()
        info.houses.forEach {
            houses.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("onlineUse", it.onlineUse).put("count", it.count),
            )
        }
        val cats = JSONArray()
        info.categories.forEach { cats.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString(
            K_STORE_INFO + storeId,
            JSONObject().put("categories", cats).put("houses", houses).toString(),
        ).apply()
    }

    fun loadStoreInfo(storeId: Int): LaundryStoreInfo? {
        val o = prefs.getString(K_STORE_INFO + storeId, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val cats = o.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i) }.getOrNull()
                    ?.let { LaundryCategory(it.optInt("id"), it.optString("name")) }
            }
        }.orEmpty()
        val houses = o.optJSONArray("houses")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val h = arr.getJSONObject(i)
                    LaundryHouse(
                        h.getInt("id"), h.getString("name"),
                        h.optBoolean("onlineUse", true), h.optInt("count"),
                    )
                }.getOrNull()
            }
        }.orEmpty()
        if (houses.isEmpty() && cats.isEmpty()) return null
        return LaundryStoreInfo(storeId, cats, houses)
    }

    /** 该门店上次选中楼栋（换门店不串）。 */
    fun lastHouseId(storeId: Int): Int = prefs.getInt(K_LAST_HOUSE + storeId, 0)

    fun setLastHouseId(storeId: Int, houseId: Int) {
        prefs.edit().putInt(K_LAST_HOUSE + storeId, houseId).apply()
    }

    // ---- 设备快照（离线/失败时展示"更新于 xx:xx"）----

    fun saveSnapshot(storeId: Int, houseId: Int, devices: List<LaundryDevice>) {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id).put("title", d.title).put("actionCode", d.actionCode)
                    .put("status", d.status.name).put("online", d.online)
                    .put("remainSeconds", d.remainSeconds).put("categoryId", d.categoryId)
                    .put("queueNum", d.queueNum),
            )
        }
        prefs.edit().putString(
            "$K_SNAPSHOT$storeId _$houseId",
            JSONObject().put("devices", arr).put("at", System.currentTimeMillis()).toString(),
        ).apply()
    }

    fun loadSnapshot(storeId: Int, houseId: Int): Pair<List<LaundryDevice>, Long>? {
        val o = prefs.getString("$K_SNAPSHOT$storeId _$houseId", null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val arr = o.optJSONArray("devices") ?: return null
        val devices = (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val d = arr.getJSONObject(i)
                LaundryDevice(
                    id = d.getInt("id"),
                    title = d.getString("title"),
                    actionCode = d.getString("actionCode"),
                    status = runCatching { DeviceStatus.valueOf(d.getString("status")) }
                        .getOrDefault(DeviceStatus.UNKNOWN),
                    online = d.getBoolean("online"),
                    remainSeconds = d.optInt("remainSeconds"),
                    categoryId = d.optInt("categoryId"),
                    queueNum = d.optInt("queueNum"),
                )
            }.getOrNull()
        }
        return devices to o.optLong("at", 0L)
    }

    // ---- 编解码 ----

    private fun encodeRecent(items: List<LaundryStoreItem>): JSONArray {
        val arr = JSONArray()
        items.distinctBy { it.id }.take(MAX_RECENT).forEach { arr.put(JSONObject(it.encode())) }
        return arr
    }

    private fun decodeRecent(json: JSONObject?): List<LaundryStoreItem> {
        val arr = json?.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { LaundryStoreItem.decode(arr.getJSONObject(i).toString()) }.getOrNull()
        }
    }

    companion object {
        private const val K_DEFAULT_STORE = "default_store"
        private const val K_RECENT = "recent_stores"
        private const val K_LAST_KEYWORD = "last_keyword"
        private const val K_CANDIDATES = "store_candidates"
        private const val K_STORE_INFO = "store_info_"
        private const val K_LAST_HOUSE = "last_house_"
        private const val K_SNAPSHOT = "snapshot_"
        private const val MAX_RECENT = 3

        @Volatile private var INSTANCE: LaundryStore? = null

        fun getInstance(context: Context): LaundryStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LaundryStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}
