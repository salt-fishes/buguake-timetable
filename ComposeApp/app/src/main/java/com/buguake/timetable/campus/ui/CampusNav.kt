package com.buguake.timetable.campus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 校园模块的层级切换动画宿主：二级（功能页）与三级（功能页内部页）共用。
 *
 * [depth] 给出每一层级的深度：进入更深一层从右滑入、返回时向右滑出，
 * 同层或平级切换则往反方向，保证"进入/退出"方向与用户操作一致。
 * 规格统一走 AppMotion 弹簧（与全应用同一套动效语言）。
 */
@Composable
fun <T> CampusStepHost(
    step: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        // 统一转场：深度缩放 + 淡切（进入更深一层迎面放大，返回反向）；
        // 页面只缩放不平移，透明玻璃页不会出现新旧文字互相穿透
        transitionSpec = {
            val deeper = depth(targetState) > depth(initialState)
            if (deeper) {
                com.buguake.timetable.ui.theme.pageEnterCloser()
                    .togetherWith(com.buguake.timetable.ui.theme.pageExitFurther())
            } else {
                com.buguake.timetable.ui.theme.pageEnterFurther()
                    .togetherWith(com.buguake.timetable.ui.theme.pageExitCloser())
            }
        },
        label = "campusStep",
    ) { current ->
        com.buguake.timetable.ui.theme.SwipeBackBox(
            onBack = { onBack?.invoke() },
            enabled = onBack != null && depth(current) > 0,
        ) {
            content(current)
        }
    }
}

