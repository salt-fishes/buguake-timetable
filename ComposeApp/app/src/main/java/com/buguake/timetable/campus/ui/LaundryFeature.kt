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
 * 首次进入（无默认门店）直接进门店选择页，不预填任何硬编码关键词；
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

    var step by remember {
        mutableStateOf<LaundryStep>(
            if (store.defaultStore() == null) LaundryStep.Picker else LaundryStep.Home,
        )
    }

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
            )

            LaundryStep.Picker -> LaundryStorePicker(
                glass = glass,
                store = store,
                client = client,
                showSnackbar = showSnackbar,
                onSelect = { selected ->
                    store.setDefaultStore(selected)
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
