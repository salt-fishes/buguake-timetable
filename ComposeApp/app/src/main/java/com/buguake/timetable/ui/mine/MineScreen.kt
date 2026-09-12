package com.buguake.timetable.ui.mine

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buguake.timetable.ui.theme.AppMotion
import com.buguake.timetable.ui.theme.Haptics
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.data.WeekCalculator
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 我的页：学期设置 / 显示开关 / 导入 / 数据。 */
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun MineScreen(
    settings: ScheduleSettings,
    glass: Boolean = false,
    courseCount: Int,
    entryCount: Int,
    onImport: () -> Unit,
    onSetSemesterStart: (LocalDate) -> Unit,
    onSetTotalWeeks: (Int) -> Unit,
    onSetShowWeekend: (Boolean) -> Unit,
    onSetShowNonCurrentWeek: (Boolean) -> Unit,
    onSetShowTeacherOnBlock: (Boolean) -> Unit = {},
    onSetShowLocationOnBlock: (Boolean) -> Unit = {},
    onSetShowExamsOnHome: (Boolean) -> Unit = {},
    onSetMoveScope: (String) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDarkMode: (String) -> Unit,
    onOpenSectionTimes: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenTimetableManage: () -> Unit = {},
    onOpenWidgetBind: () -> Unit = {},
    onOpenCompare: () -> Unit = {},
    onSetCustomBgEnabled: (Boolean) -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onSetCustomBgBlur: (Int) -> Unit,
    onSyncCalendar: () -> Unit,
    onClearCalendar: () -> Unit,
    onExportIcs: () -> Unit,
    onClearData: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showWeekDialog by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showMoveScopeDialog by rememberSaveable { mutableStateOf(false) }
    // 二级设置页："" = 一级（常用），display = 显示与样式，calendar = 日历与导出，data = 数据管理
    var detailPageState by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = detailPageState.isNotEmpty()) { detailPageState = "" }
    val today = remember { LocalDate.now() }
    val context = LocalContext.current

    // 一级页滚动状态：放在 AnimatedContent 之外，进二级页前记住位置，返回时还原到设置入口处
    val firstLevelScroll = rememberScrollState()
    Column(modifier.fillMaxSize()) {
        // 一级 ↔ 二级页整体转场：深度缩放 + 淡切（进入迎面放大、返回缩回，方向随层级变化）
        androidx.compose.animation.AnimatedContent(
            targetState = detailPageState,
            transitionSpec = {
                val deeper = targetState.isNotEmpty() && initialState.isEmpty()
                if (deeper) {
                    com.buguake.timetable.ui.theme.pageEnterCloser()
                        .togetherWith(com.buguake.timetable.ui.theme.pageExitFurther())
                } else {
                    com.buguake.timetable.ui.theme.pageEnterFurther()
                        .togetherWith(com.buguake.timetable.ui.theme.pageExitCloser())
                }
            },
            label = "mineDetail",
        ) { page ->
        com.buguake.timetable.ui.theme.SwipeBackBox(
            onBack = { detailPageState = "" },
            enabled = page.isNotEmpty(),
        ) {
            // 阴影外层状态：内容统一按动画目标帧渲染，无需改动各处引用
            val detailPage = page
            // 每页独立滚动：二级页进入时在头部（位置 0），一级页共用 firstLevelScroll 记住位置
            val pageScroll = if (page.isEmpty()) firstLevelScroll else rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
        // ---- 页面标题：一级「我的」；二级页带返回 ----
        val pageTitle = when (detailPage) {
            "display" -> "显示与样式"
            "calendar" -> "日历与导出"
            "data" -> "数据管理"
            else -> ""
        }
        if (pageTitle.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                IconButton(onClick = { Haptics.tick(context); detailPageState = "" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    pageTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                "我的",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        // ---- 以下为一级页内容：学期 / 常用 / 更多设置（二级页隐藏） ----
        if (detailPage.isEmpty()) {

        // ---- 学期信息：全页视觉重心，最常查看/修改的两项 ----
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val rawWeek = WeekCalculator.currentWeek(settings.semesterStartDate, today)
                if (rawWeek < 1) {
                    // 边界处理：开学前显示倒计时
                    val daysToStart =
                        settings.semesterStartDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
                    Text(
                        text = when {
                            settings.semesterStartDate == null -> "未设置开学时间"
                            daysToStart != null && daysToStart > 0 -> "距开学还有 $daysToStart 天"
                            else -> "今天开学"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        "第 $rawWeek 周",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                settings.semesterStartDate?.let { date ->
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            "开学：${date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "修改",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showWeekDialog = true }
                        .padding(vertical = 4.dp),
                ) {
                    Text("学期周数", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "${settings.totalWeeks} 周",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---- 常用：最高频的四个入口 ----
        SectionHeader("常用")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Button(
                    onClick = onImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("导入课表（教务网页导入）")
                }
                CardDivider()
                ActionRow(
                    "作息时间",
                    "统一设置 / 作息预设 / 逐节微调",
                    trailing = "${fmt(settings.sectionTimes.first().start.hour, settings.sectionTimes.first().start.minute)}" +
                        " - ${fmt(settings.sectionTimes.last().end.hour, settings.sectionTimes.last().end.minute)}",
                ) { Haptics.tick(context); onOpenSectionTimes() }
                CardDivider()
                ActionRow(
                    "课程提醒",
                    if (settings.remindEnabled) {
                        val ahead = if (settings.remindMinutesBefore == 0) "准点提醒"
                        else "提前 ${settings.remindMinutesBefore} 分钟"
                        "已开启 · $ahead · 点击查看权限与诊断"
                    } else {
                        "已关闭 · 点击进入设置"
                    },
                ) { Haptics.tick(context); onOpenReminders() }
                CardDivider()
                ActionRow("桌面小组件", "2×2 / 3×2 / 4×2（今明双栏）三种尺寸，分别绑定课表") { Haptics.tick(context); onOpenWidgetBind() }
            }
        }

        // ---- 更多设置：低频项，各归各的二级页 ----
        SectionHeader("更多设置")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ActionRow("课表管理", "多课表切换 / 重命名 / 复制") { Haptics.tick(context); onOpenTimetableManage() }
                CardDivider()
                ActionRow("课表对比（实验性）", "勾选多张课表，找共同空闲时间") { Haptics.tick(context); onOpenCompare() }
                CardDivider()
                ActionRow("显示与样式", "显示开关 · 长按范围 · 磨砂玻璃背景 · 深色模式") { Haptics.tick(context); detailPageState = "display" }
                CardDivider()
                ActionRow("日历与导出", "同步系统日历 · 清空 · 导出 .ics") { Haptics.tick(context); detailPageState = "calendar" }
                CardDivider()
                ActionRow("数据管理", "课程统计 · 清除当前课表") { Haptics.tick(context); detailPageState = "data" }
                CardDivider()
                ActionRow(
                    "关于不挂科课表",
                    trailing = "版本 ${com.buguake.timetable.BuildConfig.VERSION_NAME}",
                ) { onOpenAbout() }
                CardDivider()
                ActionRow("隐私政策", trailing = "无广告") { onOpenPrivacy() }
            }
        }
        }  // if (detailPage.isEmpty()) 一级页：学期 / 常用 / 更多设置

        // ---- 二级：显示与样式 ----
        if (detailPage == "display") {
        SectionHeader("显示")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SwitchRow("显示周末", settings.showWeekend, onSetShowWeekend, Modifier.padding(horizontal = 16.dp))
                CardDivider()
                SwitchRow(
                    "显示非本周课程（淡化）",
                    settings.showNonCurrentWeek,
                    onSetShowNonCurrentWeek,
                    Modifier.padding(horizontal = 16.dp),
                )
                CardDivider()
                SwitchRow(
                    "课程块显示教师",
                    settings.showTeacherOnBlock,
                    onSetShowTeacherOnBlock,
                    Modifier.padding(horizontal = 16.dp),
                )
                CardDivider()
                SwitchRow(
                    "课程块显示地点",
                    settings.showLocationOnBlock,
                    onSetShowLocationOnBlock,
                    Modifier.padding(horizontal = 16.dp),
                )
                CardDivider()
                SwitchRow(
                    "首页显示考试",
                    settings.showExamsOnHome,
                    onSetShowExamsOnHome,
                    Modifier.padding(horizontal = 16.dp),
                )
                CardDivider()
                ActionRow(
                    "长按移动课程范围",
                    subtitle = "拖动课程块调整时间时的作用范围",
                    trailing = when (settings.moveScope) {
                        com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_THIS_WEEK -> "仅本周"
                        com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_REST -> "以后每周"
                        else -> "每次询问"
                    },
                ) { showMoveScopeDialog = true }
                CardDivider()
                SwitchRow("磨砂玻璃风格", settings.customBgEnabled, onSetCustomBgEnabled, Modifier.padding(horizontal = 16.dp))
                AnimatedVisibility(
                    visible = settings.customBgEnabled,
                    enter = expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects()),
                    exit = shrinkVertically(AppMotion.spatialFast()) + fadeOut(AppMotion.effectsFast()),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickBackground() }
                                .padding(vertical = 8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("选择背景图片（可选）", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (settings.customBgPath.isBlank()) "未设置 · 使用内置渐变背景"
                                    else "已设置 · 自定义图片铺满首页/今日页/底栏",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "选择",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (settings.customBgPath.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "背景模糊",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(72.dp),
                                )
                                Slider(
                                    value = settings.customBgBlurDp.toFloat(),
                                    onValueChange = { onSetCustomBgBlur(((it / 4f).roundToInt() * 4)) },
                                    valueRange = 0f..28f,
                                    steps = 6,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${settings.customBgBlurDp}dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp),
                                )
                            }
                            TextButton(onClick = onClearBackground) {
                                Text("清除背景图片", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            "覆盖首页、今日页与底栏；背景可换为自定义图片",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        }
        }  // GlassCard
        }  // if (detailPage == "display")

        // ---- 外观：主题色相关（仅二级页） ----
        if (detailPage == "display") {
        SectionHeader("外观")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "深色模式",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = settings.darkMode == "system",
                        onClick = {
                            Haptics.tick(context)
                            onSetDarkMode("system")
                        },
                        label = { Text("跟随系统") },
                    )
                    FilterChip(
                        selected = settings.darkMode == "light",
                        onClick = {
                            Haptics.tick(context)
                            onSetDarkMode("light")
                        },
                        label = { Text("亮色") },
                    )
                    FilterChip(
                        selected = settings.darkMode == "dark",
                        onClick = {
                            Haptics.tick(context)
                            onSetDarkMode("dark")
                        },
                        label = { Text("暗色") },
                    )
                }
                CardDivider(Modifier.padding(vertical = 4.dp))
                SwitchRow(
                    "动态取色（Android 12+）",
                    settings.dynamicColor,
                    onSetDynamicColor,
                    Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        }  // if (detailPage == "display")：外观在二级页

        // ---- 二级：日历与导出 ----
        if (detailPage == "calendar") {
        SectionHeader("系统日历")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                ActionRow(
                    "同步到系统日历",
                    "写入系统日历「不挂科课表」，随系统日历提醒",
                    enabled = entryCount > 0,
                ) { onSyncCalendar() }
                CardDivider()
                ActionRow("清空系统日历中的课程", "撤销同步，仅删除本应用写入的课程") { onClearCalendar() }
                CardDivider()
                ActionRow("导出 .ics 文件", "备用：供其他日历应用手动导入", enabled = entryCount > 0) { onExportIcs() }
            }
        }
        }

        // ---- 二级：数据管理 ----
        if (detailPage == "data") {
        SectionHeader("数据")
        GlassCard(glass, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "当前《${settings.timetableName.ifBlank { "我的课表" }}》：$courseCount 门课程 · $entryCount 条排课",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                CardDivider()
                ActionRow("清除当前课表", "删除当前课表全部课程与排课，不可恢复", danger = true) {
                    showClearConfirm = true
                }
            }
        }
        }
        }  // SwipeBackBox
            }  // AnimatedContent 内层 Column
        }  // AnimatedContent
    }

    if (showDatePicker) {
        val initMillis = settings.semesterStartDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val dateState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onSetSemesterStart(d)
                        onShowSnackbar("开学时间已更新")
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    // 长按移动课程的作用范围：选好后调课不再每次弹窗询问
    if (showMoveScopeDialog) {
        val options = listOf(
            com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_ASK to "每次询问",
            com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_THIS_WEEK to "仅本周",
            com.buguake.timetable.data.SettingsRepository.MOVE_SCOPE_REST to "以后每周",
        )
        AlertDialog(
            onDismissRequest = { showMoveScopeDialog = false },
            title = { Text("长按移动课程范围") },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetMoveScope(value)
                                    showMoveScopeDialog = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = settings.moveScope == value,
                                onClick = {
                                    onSetMoveScope(value)
                                    showMoveScopeDialog = false
                                },
                            )
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Text(
                        "选「仅本周/以后每周」后，拖动课程块将直接按该范围调整，不再询问。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveScopeDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除当前课表") },
            text = { Text("将删除《${settings.timetableName.ifBlank { "我的课表" }}》的全部课程与排课（其他课表不受影响），此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearData()
                    showClearConfirm = false
                    onShowSnackbar("已清除")
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showWeekDialog) {
        var sel by rememberSaveable { mutableIntStateOf(settings.totalWeeks) }
        AlertDialog(
            onDismissRequest = { showWeekDialog = false },
            title = { Text("学期总周数") },
            text = {
                Column {
                    Text(
                        "当前 ${sel} 周（课表按此生成周数）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((8..30).toList()) { w ->
                            FilterChip(
                                selected = sel == w,
                                onClick = { sel = w },
                                label = { Text("$w") },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetTotalWeeks(sel)
                    onShowSnackbar("学期周数已更新为 $sel 周")
                    showWeekDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showWeekDialog = false }) { Text("取消") }
            },
        )
    }

    // ---- 作息时间编辑已移至独立页面 SectionTimePage ----
}

/** 玻璃开关卡片容器：glass 开启时为磨砂玻璃面，否则为普通实色 Card。 */
@Composable
private fun GlassCard(
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (glass) {
        com.buguake.timetable.ui.theme.GlassSurface(modifier = modifier) { content() }
    } else {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = modifier,
        ) { content() }
    }
}

private fun fmt(h: Int, m: Int): String = "%02d:%02d".format(h, m)

/** 卡片内分组行之间的细分隔线（左右留出卡片内边距）。 */
@Composable
private fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

/** 设置卡片内的可点击行：主标题 + 可选说明 + 可选右侧值。 */
@Composable
private fun ActionRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        val context = LocalContext.current
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = {
            Haptics.tick(context)
            onChange(it)
        })
    }
}
