package com.buguake.timetable.campus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.buguake.timetable.campus.laundry.LaundryHouse
import com.buguake.timetable.campus.laundry.LaundryStore
import com.buguake.timetable.campus.laundry.LaundryStoreInfo
import com.buguake.timetable.campus.laundry.ShunshuiClient

/** 洗衣房内部步骤（二级 = 主页 / 门店选择，三级 = 设备状态）。 */
private sealed interface LaundryStep {
    data object Home : LaundryStep
    data object Picker : LaundryStep
    data class Devices(val house: LaundryHouse, val info: LaundryStoreInfo) : LaundryStep
}

/**
 * 校园「洗衣房」功能壳：步骤编排与转场动画。
 *
 * 进入时按缓存自动直达：
 * - 上次浏览过某楼栋 → 直接打开该楼栋的设备页；
 * - 门店没有楼栋信息（实测如含辉苑，house 为空数组）→ 直接进入"全部设备"
 *   （虚拟楼栋 house_id=0，实测可用）。
 * 首次进入（无默认门店）进门店选择页，不预填任何硬编码关键词。
 * 只读查询第三方（顺水）接口，下单跳官方小程序，本模块不碰支付与设备控制。
 */
@Composable
fun LaundryFeature(
    glass: Boolean = false,
    showSnackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { LaundryStore.getInstance(context) }
    val client = remember { ShunshuiClient.default() }

    // 进入时按缓存决定初始页（无缓存则进主页异步加载，由 onInfoLoaded 兜底直达）
    val initialStep = remember {
        val d = store.defaultStore()
        val cached = d?.let { store.loadStoreInfo(it.id) }
        when {
            d == null -> LaundryStep.Picker
            cached == null -> LaundryStep.Home
            else -> {
                val house = cached.houses.firstOrNull { it.id == store.lastHouseId(d.id) }
                when {
                    house != null -> LaundryStep.Devices(house, cached)
                    // 无楼栋门店：以门店整体作为虚拟楼栋（house_id=0）直取设备
                    cached.houses.isEmpty() ->
                        LaundryStep.Devices(LaundryHouse(0, d.name, true, 0), cached)
                    else -> LaundryStep.Home
                }
            }
        }
    }
    var step by remember { mutableStateOf(initialStep) }
    // 自动直达只做一次：用户从设备页返回主页后不再强行跳回
    var autoOpened by remember { mutableStateOf(initialStep is LaundryStep.Devices) }

    BackHandler(enabled = step !is LaundryStep.Devices) { onBack() }

    CampusStepHost(
        step = step,
        depth = {
            when (it) {
                LaundryStep.Home -> 0
                LaundryStep.Picker -> 1
                is LaundryStep.Devices -> 1
            }
        },
        onBack = { step = LaundryStep.Home },
    ) { current ->
        when (current) {
            LaundryStep.Home -> LaundryHome(
                glass = glass,
                store = store,
                client = client,
                showSnackbar = showSnackbar,
                onPickStore = { step = LaundryStep.Picker },
                onOpenHouse = { house, info -> step = LaundryStep.Devices(house, info) },
                onBack = onBack,
                onInfoLoaded = { loaded ->
                    val d = store.defaultStore()
                    if (!autoOpened && loaded != null && loaded.houses.isEmpty() && d != null) {
                        autoOpened = true
                        step = LaundryStep.Devices(LaundryHouse(0, d.name, true, 0), loaded)
                    }
                },
            )

            LaundryStep.Picker -> LaundryStorePicker(
                glass = glass,
                store = store,
                client = client,
                showSnackbar = showSnackbar,
                onSelect = { selected ->
                    store.setDefaultStore(selected)
                    autoOpened = false // 换门店后重新按新门店信息直达
                    step = LaundryStep.Home
                },
                onBack = { step = LaundryStep.Home },
            )

            is LaundryStep.Devices -> LaundryDevices(
                glass = glass,
                store = store,
                client = client,
                info = current.info,
                house = current.house,
                showSnackbar = showSnackbar,
                onBack = { step = LaundryStep.Home },
            )
        }
    }
}
