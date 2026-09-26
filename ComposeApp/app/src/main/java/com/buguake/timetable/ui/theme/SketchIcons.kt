package com.buguake.timetable.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 纸绘风格通用图标集（对应原系统图标，线稿 1.8dp 圆头描边）。
 * 全应用图标统一从这里取用，不再直接引用系统图标集。
 */
val SketchArrowBack: ImageVector by lazy {
    sketchIcon("SketchArrowBack", "M20,12 L4.5,12 M10.5,6 L4.5,12 L10.5,18")
}

val SketchArrowForward: ImageVector by lazy {
    sketchIcon("SketchArrowForward", "M4,12 L19.5,12 M13.5,6 L19.5,12 L13.5,18")
}

val SketchChevronRight: ImageVector by lazy {
    sketchIcon("SketchChevronRight", "M9,5 L16,12 L9,19")
}

val SketchSearch: ImageVector by lazy {
    sketchIcon(
        "SketchSearch",
        "M10.5,4 A6.5,6.5 0 1 0 10.5,17 A6.5,6.5 0 1 0 10.5,4 M15.3,15.2 L20.5,20.4",
    )
}

val SketchRefresh: ImageVector by lazy {
    sketchIcon(
        "SketchRefresh",
        "M19.8,8.6 A8.2,8.2 0 1 0 20.2,13.4 M20.6,3.6 L20.1,8.7 L15.1,8.2",
    )
}

val SketchInfo: ImageVector by lazy {
    sketchIcon(
        "SketchInfo",
        "M12,3.5 A8.5,8.5 0 1 0 12,20.5 A8.5,8.5 0 1 0 12,3.5 M12,11.2 L12,16.4 M12,7.4 L12,7.5",
    )
}

val SketchCalendar: ImageVector by lazy {
    sketchIcon(
        "SketchCalendar",
        "M4.5,6.5 L4.5,20 L19.5,20 L19.5,6.5 Z M4.5,10.5 L19.5,10.5 M8.5,4 L8.5,8 M15.5,4 L15.5,8",
    )
}

val SketchClose: ImageVector by lazy {
    sketchIcon("SketchClose", "M6,6 L18,18 M18,6 L6,18")
}

val SketchAdd: ImageVector by lazy {
    sketchIcon("SketchAdd", "M12,5 L12,19 M5,12 L19,12")
}

val SketchEdit: ImageVector by lazy {
    sketchIcon(
        "SketchEdit",
        "M4.5,19.5 L5.3,15.7 L15.8,5.2 L18.8,8.2 L8.3,18.7 Z M13.9,7.1 L16.9,10.1",
    )
}

val SketchCheck: ImageVector by lazy {
    sketchIcon("SketchCheck", "M4.5,12.5 L10,18 L19.5,6.5")
}

val SketchStar: ImageVector by lazy {
    sketchIcon(
        "SketchStar",
        "M12,4 L14.4,9.2 L20,9.9 L15.9,13.8 L17,19.4 L12,16.6 L7,19.4 L8.1,13.8 L4,9.9 L9.6,9.2 Z",
    )
}

val SketchShare: ImageVector by lazy {
    sketchIcon(
        "SketchShare",
        "M8.1,10.9 L14.9,6.6 M8.1,13.1 L14.9,17.4 M6,9.8 A2.2,2.2 0 1 0 6,14.2 A2.2,2.2 0 1 0 6,9.8 M17,3.3 A2.2,2.2 0 1 0 17,7.7 A2.2,2.2 0 1 0 17,3.3 M17,16.3 A2.2,2.2 0 1 0 17,20.7 A2.2,2.2 0 1 0 17,16.3",
    )
}

val SketchSettings: ImageVector by lazy {
    sketchIcon(
        "SketchSettings",
        "M12,8.5 A3.5,3.5 0 1 0 12,15.5 A3.5,3.5 0 1 0 12,8.5 M12,3.2 L12,5.6 M12,18.4 L12,20.8 M3.2,12 L5.6,12 M18.4,12 L20.8,12 M5.8,5.8 L7.5,7.5 M16.5,16.5 L18.2,18.2 M18.2,5.8 L16.5,7.5 M7.5,16.5 L5.8,18.2",
    )
}

val SketchSend: ImageVector by lazy {
    sketchIcon("SketchSend", "M3.5,11.5 L20.5,4 L14,20.5 L11,13.5 Z M11,13.5 L20.5,4")
}

val SketchBell: ImageVector by lazy {
    sketchIcon(
        "SketchBell",
        "M6,17 L6,11 A6,6 0 0 1 18,11 L18,17 Z M10.2,19.6 A2,2 0 0 0 13.8,19.6 M12,4.2 L12,5",
    )
}

val SketchLock: ImageVector by lazy {
    sketchIcon(
        "SketchLock",
        "M6.5,11 L6.5,20 L17.5,20 L17.5,11 Z M8.5,11 L8.5,7.5 A3.5,3.5 0 0 1 15.5,7.5 L15.5,11 M12,14 L12,16.5",
    )
}

val SketchPin: ImageVector by lazy {
    sketchIcon(
        "SketchPin",
        "M12,21 C12,21 5.5,14.8 5.5,10 A6.5,6.5 0 0 1 18.5,10 C18.5,14.8 12,21 12,21 Z M12,7.8 A2.4,2.4 0 1 0 12,12.6 A2.4,2.4 0 1 0 12,7.8",
    )
}

val SketchList: ImageVector by lazy {
    sketchIcon(
        "SketchList",
        "M8.5,6.5 L20,6.5 M8.5,12 L20,12 M8.5,17.5 L20,17.5 M4.5,6.5 L4.6,6.5 M4.5,12 L4.6,12 M4.5,17.5 L4.6,17.5",
    )
}

val SketchFace: ImageVector by lazy {
    sketchIcon(
        "SketchFace",
        "M12,3.5 A8.5,8.5 0 1 0 12,20.5 A8.5,8.5 0 1 0 12,3.5 M9,10 L9,10.1 M15,10 L15,10.1 M8.5,14.3 A4.6,4.6 0 0 0 15.5,14.3",
    )
}

val SketchTrash: ImageVector by lazy {
    sketchIcon(
        "SketchTrash",
        "M5,7 L19,7 M9.5,7 L9.5,5 L14.5,5 L14.5,7 M7,7 L7.7,20 L16.3,20 L17,7 M10.2,10.5 L10.4,16.5 M13.8,10.5 L13.6,16.5",
    )
}

val SketchWrench: ImageVector by lazy {
    sketchIcon(
        "SketchWrench",
        "M14.6,6.4 A4.6,4.6 0 0 0 8.2,12 L4,16.2 L7.8,20 L12,15.8 A4.6,4.6 0 0 0 17.6,9.4 L14.9,12.1 L11.9,9.1 Z",
    )
}
