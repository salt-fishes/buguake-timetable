package com.buguake.timetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 纸绘风格图标（线稿）：1.8dp 圆头描边，不填充——像用笔在纸上勾出来的图形。
 * 与贴纸设计系统的墨线描边语言一致；Icon 组件的 tint 会整体着色。
 */
internal fun sketchIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

/** 锁（纸绘线稿）。 */
val LockIcon: ImageVector by lazy {
    sketchIcon(
        "LockIcon",
        "M6.5,11 L6.5,20 L17.5,20 L17.5,11 Z M8.5,11 L8.5,7.5 A3.5,3.5 0 0 1 15.5,7.5 L15.5,11 M12,14 L12,16.5",
    )
}

/** 学校/校园（纸绘线稿：学士帽）。 */
val CampusIcon: ImageVector by lazy {
    sketchIcon(
        "CampusIcon",
        "M2.5,9.5 L12,4.5 L21.5,9.5 L12,14.5 Z M6,12 L6,16.5 A6,6 0 0 0 18,16.5 L18,12 M21.5,9.5 L21.5,15",
    )
}

/** 洗衣机（纸绘线稿：机身 + 滚筒）。 */
val LaundryIcon: ImageVector by lazy {
    sketchIcon(
        "LaundryIcon",
        "M5,3.5 L19,3.5 L19,20.5 L5,20.5 Z M5,8 L19,8 M8.2,5.8 L8.3,5.8 M11.2,5.8 L11.3,5.8 M12,10.3 A3.9,3.9 0 1 0 12,18.1 A3.9,3.9 0 1 0 12,10.3",
    )
}

/** 课表（纸绘线稿：2×2 课程格）。 */
val TimetableIcon: ImageVector by lazy {
    sketchIcon(
        "TimetableIcon",
        "M4.5,4.5 L10,4.5 L10,10 L4.5,10 Z M14,4.5 L19.5,4.5 L19.5,10 L14,10 Z M4.5,14 L10,14 L10,19.5 L4.5,19.5 Z M14,14 L19.5,14 L19.5,19.5 L14,19.5 Z",
    )
}

/** 今日（纸绘线稿：时钟）。 */
val TodayIcon: ImageVector by lazy {
    sketchIcon(
        "TodayIcon",
        "M12,4 A8,8 0 1 0 12,20 A8,8 0 1 0 12,4 M12,7.5 L12,12 L15.5,14",
    )
}

/** 我的（纸绘线稿：人形）。 */
val MineIcon: ImageVector by lazy {
    sketchIcon(
        "MineIcon",
        "M12,4.8 A3.7,3.7 0 1 0 12,12.2 A3.7,3.7 0 1 0 12,4.8 M4.5,20.5 C4.5,16.4 7.9,13.9 12,13.9 C16.1,13.9 19.5,16.4 19.5,20.5",
    )
}
