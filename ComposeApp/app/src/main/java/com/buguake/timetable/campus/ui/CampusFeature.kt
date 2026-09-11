package com.buguake.timetable.campus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.buguake.timetable.ui.theme.LockIcon

/**
 * 校园 tab 的功能入口注册表：新增校园功能只需在此追加一条，
 * 首页入口列表与二级页导航会自动生效，无需改动导航代码。
 *
 * [content] 的 `unlockSeq` 由「长按应用图标 → 快速开锁」快捷方式驱动（0 = 非快捷方式进入）。
 */
data class CampusFeature(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val content: @Composable (
        glass: Boolean,
        showSnackbar: (String) -> Unit,
        unlockSeq: Int,
        onBack: () -> Unit,
    ) -> Unit,
)

val CAMPUS_FEATURES: List<CampusFeature> = listOf(
    CampusFeature(
        id = "unlock",
        title = "宿舍开门",
        subtitle = "一次登录后本机开门，日常无需联网",
        icon = LockIcon,
        content = { glass, showSnackbar, unlockSeq, onBack ->
            CampusUnlockScreen(
                glass = glass,
                showSnackbar = showSnackbar,
                autoUnlockSeq = unlockSeq,
                onBack = onBack,
            )
        },
    ),
)
