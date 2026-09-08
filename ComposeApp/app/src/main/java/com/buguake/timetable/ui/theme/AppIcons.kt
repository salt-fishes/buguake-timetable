package com.buguake.timetable.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 锁图标（Material lock 几何，Apache 2.0；core 图标集不含，本地自绘）。 */
val LockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "LockIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
        ) {
            moveTo(18f, 8f)
            lineToRelative(-1f, 0f)
            lineTo(17f, 6f)
            arcTo(5f, 5f, 0f, false, false, 12f, 1f)
            arcTo(5f, 5f, 0f, false, false, 7f, 6f)
            lineTo(7f, 8f)
            lineTo(6f, 8f)
            arcTo(2f, 2f, 0f, false, false, 4f, 10f)
            lineTo(4f, 20f)
            arcTo(2f, 2f, 0f, false, false, 6f, 22f)
            lineToRelative(12f, 0f)
            arcTo(2f, 2f, 0f, false, false, 20f, 20f)
            lineTo(20f, 10f)
            arcTo(2f, 2f, 0f, false, false, 18f, 8f)
            close()
            moveTo(12f, 17f)
            arcTo(2f, 2f, 0f, false, false, 12f, 13f)
            arcTo(2f, 2f, 0f, false, false, 12f, 17f)
            close()
            moveTo(15.1f, 8f)
            lineTo(8.9f, 8f)
            lineTo(8.9f, 6f)
            arcTo(3.1f, 3.1f, 0f, false, true, 12f, 2.9f)
            arcTo(3.1f, 3.1f, 0f, false, true, 15.1f, 6f)
            close()
        }
    }.build()
}

/** 学校/校园图标（Material school 几何，Apache 2.0；core 图标集不含，本地自绘）。 */
val CampusIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CampusIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
        ) {
            moveTo(12f, 3f)
            lineTo(1f, 9f)
            lineToRelative(4f, 2.18f)
            verticalLineToRelative(6f)
            lineTo(12f, 21f)
            lineToRelative(7f, -3.82f)
            verticalLineToRelative(-6f)
            lineToRelative(2f, -1.09f)
            verticalLineTo(17f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            lineTo(12f, 3f)
            close()
            moveToRelative(6.82f, 6f)
            lineTo(12f, 12.72f)
            lineTo(5.18f, 9f)
            lineTo(12f, 5.28f)
            lineTo(18.82f, 9f)
            close()
            moveTo(17f, 15.99f)
            lineToRelative(-5f, 2.73f)
            lineToRelative(-5f, -2.73f)
            verticalLineToRelative(-3.72f)
            lineTo(12f, 15f)
            lineToRelative(5f, -2.73f)
            verticalLineToRelative(3.72f)
            close()
        }
    }.build()
}
