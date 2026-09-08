package com.buguake.timetable.campus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.buguake.timetable.ui.theme.LockIcon

/**
 * 校园 tab 的功能入口注册表：新增校园功能只需在此追加一条，
 * 首页入口列表与二级页导航会自动生效，无需改动导航代码。
 */
data class CampusFeature(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val content: @Composable (glass: Boolean, showSnackbar: (String) -> Unit, onBack: () -> Unit) -> Unit,
)

val CAMPUS_FEATURES: List<CampusFeature> = listOf(
    CampusFeature(
        id = "unlock",
        title = "宿舍开门",
        subtitle = "连接宿舍蓝牙门锁，一键开门",
        icon = LockIcon,
        content = { glass, showSnackbar, onBack ->
            CampusUnlockScreen(glass = glass, showSnackbar = showSnackbar, onBack = onBack)
        },
    ),
)
