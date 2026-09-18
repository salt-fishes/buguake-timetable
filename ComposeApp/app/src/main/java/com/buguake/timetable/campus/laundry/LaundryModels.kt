package com.buguake.timetable.campus.laundry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 洗衣房（顺水平台）数据模型。
 *
 * 接口契约来自实测（见本地《洗衣房功能-开发方案.md》）：
 * - 门店发现走 `/wash/store/near`（按坐标，主通道）+ `/wash/store/search`（按门店名，兜底）；
 * - 设备分类随门店变化（洗衣机/烘干机/洗鞋机/吹风机），**不可写死**；
 * - 设备类别一律以 `top_category_id` 判别（device_title 命名规则不统一，不可解析）。
 */

/** 门店（near / search 两条通道返回结构一致）。 */
data class LaundryStoreItem(
    val id: Int,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    /** 展示用标签（洗衣机 / 烘干机 / 可预约…），不据此判断能力。 */
    val tags: List<String>,
) {
    fun encode(): String = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("address", address)
        put("lat", lat)
        put("lng", lng)
        put("tags", org.json.JSONArray(tags))
    }.toString()

    companion object {
        fun decode(json: String): LaundryStoreItem? = runCatching {
            val o = org.json.JSONObject(json)
            LaundryStoreItem(
                id = o.getInt("id"),
                name = o.getString("name"),
                address = o.optString("address"),
                lat = o.getDouble("lat"),
                lng = o.getDouble("lng"),
                tags = o.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
            )
        }.getOrNull()
    }
}

/** 门店的设备分类：来自 store/info 的 data.info.category，随门店变化。 */
data class LaundryCategory(val id: Int, val name: String)

/** 楼栋（store/info 的 data.info.house，key 为 house_id 的对象字典）。 */
data class LaundryHouse(
    val id: Int,
    val name: String,
    /** 1 在用 / 0 停用。注意停用不代表无设备，仅作展示标记。 */
    val onlineUse: Boolean,
    /** 登记设备数（各分类之和），可作"是否拉全"的校验口径。 */
    val count: Int,
)

/** 门店详情：分类 + 楼栋。 */
data class LaundryStoreInfo(
    val storeId: Int,
    val categories: List<LaundryCategory>,
    val houses: List<LaundryHouse>,
)

enum class DeviceStatus { IDLE, RUNNING, PROTECTING, UNKNOWN }

/** 楼栋内单台设备。 */
data class LaundryDevice(
    val id: Int,
    /** 已收敛空白（原始 device_title 空格不规范）。 */
    val title: String,
    /** 跳官方小程序下单用（即机器二维码里的 code）。 */
    val actionCode: String,
    val status: DeviceStatus,
    /** device_status == 1。 */
    val online: Boolean,
    /** 运行剩余秒数（end_time），空闲为 0。 */
    val remainSeconds: Int,
    /** 设备实际类别——分组/判类的唯一依据。 */
    val categoryId: Int,
    val queueNum: Int,
)

/**
 * 状态映射（实测订正，原文档 `0=空闲/1=工作中` 的说法是错的）：
 * 1=空闲（MachineState=0 且 end_time=0）、2=运行中、3=保护中/待取衣、
 * 0/4 未观测到 → UNKNOWN 渲染「未知」，不猜文案。
 * 烘干机已实测与洗衣机同构，设备级通用。
 */
fun mapDeviceStatus(status: Int, machineState: Int, endTime: Int): DeviceStatus = when {
    status == 1 && machineState == 0 && endTime == 0 -> DeviceStatus.IDLE
    status == 2 -> DeviceStatus.RUNNING
    status == 3 -> DeviceStatus.PROTECTING
    else -> DeviceStatus.UNKNOWN
}

/** 两点球面距离（km），门店接口不返回 distance，由客户端计算展示。 */
fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** 距离的展示文案：1km 内用米，其余保留一位小数。 */
fun distanceLabel(km: Double): String =
    if (km < 1.0) "${(km * 1000).roundToInt()} m" else "约 %.1f km".format(km)
