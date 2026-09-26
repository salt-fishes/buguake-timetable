package com.buguake.timetable.ui.mine

import com.buguake.timetable.ui.theme.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.buguake.timetable.data.ScheduleSettings
import com.buguake.timetable.ui.theme.Haptics
import com.buguake.timetable.data.SectionTime

/** 作息时间页：一日节数调整 + 各节起止时间编辑（独立全屏页面；玻璃模式透出背景）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SectionTimePage(
    settings: ScheduleSettings,
    glass: Boolean = false,
    presets: List<com.buguake.timetable.data.SectionPreset> = emptyList(),
    onSavePreset: (String) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onSetSectionTimes: (List<SectionTime>) -> Unit,
    onSetSectionsPerDay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var editingSection by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<java.time.LocalTime?>(null) }
    var showSavePreset by rememberSaveable { mutableStateOf(false) }
    var pendingDeletePreset by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // 统一设置课程时间：起始 + 每节时长 + 节间休息
    var uniformStart by remember {
        mutableStateOf(settings.sectionTimes.firstOrNull()?.start ?: java.time.LocalTime.of(8, 0))
    }
    var uniformDur by rememberSaveable { mutableIntStateOf(45) }
    var uniformGap by rememberSaveable { mutableIntStateOf(10) }
    var pickingUniformStart by rememberSaveable { mutableStateOf(false) }

    BackHandler { onBack() }

    Surface(
        Modifier.fillMaxSize(),
        color = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
    ) {
        Column {
            // ---- 顶栏：返回 + 标题（避让状态栏） ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(SketchArrowBack, contentDescription = "返回")
                }
                Text("作息时间设置", style = MaterialTheme.typography.titleMedium)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "按课程表的节次数调整每日作息；修改后课表左侧节次轴同步更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // ---- 作息表预设：保存当前作息，一键切换（置于页首便于快速切换） ----
                GlassCard(glass) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("作息表预设", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "把当前作息存成预设，点一下即可整表切换（节数一并对齐）；右侧 × 删除预设",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { showSavePreset = true }) { Text("保存当前") }
                        }
                        if (presets.isNotEmpty()) {
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(presets.size) { i ->
                                    val p = presets[i]
                                    androidx.compose.material3.AssistChip(
                                        modifier = Modifier.animateItem(),
                                        onClick = {
                                            Haptics.tick(context)
                                            val times = com.buguake.timetable.data.SettingsRepository
                                                .decodeSections(p.csv)
                                            if (times.isNotEmpty()) {
                                                // 套用预设的同时把一日节数对齐到预设表
                                                onSetSectionsPerDay(times.size)
                                                onSetSectionTimes(times)
                                            }
                                        },
                                        label = { Text(p.name) },
                                        trailingIcon = {
                                            Icon(
                                                SketchClose,
                                                contentDescription = "删除预设 ${p.name}",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { pendingDeletePreset = p.name },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ---- 一日节数步进器（4..16） ----
                GlassCard(glass) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("一日节数", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "课表网格与作息表行数随之变化",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onSetSectionsPerDay(settings.sectionsPerDay - 1) },
                            enabled = settings.sectionsPerDay > 4,
                        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                        com.buguake.timetable.ui.theme.RollingNumber(
                            value = settings.sectionsPerDay,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                                                IconButton(
                            onClick = { onSetSectionsPerDay(settings.sectionsPerDay + 1) },
                            enabled = settings.sectionsPerDay < 16,
                        ) { Icon(SketchAdd, contentDescription = "增加一节") }
                    }
                }

                // ---- 统一设置课程时间：起始 + 每节时长 + 节间休息 → 一键生成整表 ----
                GlassCard(glass) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("统一设置课程时间", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "按统一的节时长与节间休息生成整张作息表",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("第一节开始", Modifier.weight(1f))
                            TextButton(onClick = { pickingUniformStart = true }) {
                                Text(
                                    fmt(uniformStart.hour, uniformStart.minute),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        StepperRow("每节时长（分钟）", uniformDur, 20, 90, 5) { uniformDur = it }
                        StepperRow("节间休息（分钟）", uniformGap, 0, 30, 5) { uniformGap = it }
                        Spacer(Modifier.size(2.dp))
                        Button(
                            onClick = {
                                val times = (1..settings.sectionsPerDay).map { s ->
                                    val st = uniformStart.plusMinutes(((s - 1) * (uniformDur + uniformGap)).toLong())
                                    SectionTime(s, st, st.plusMinutes(uniformDur.toLong()))
                                }
                                onSetSectionTimes(times)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("应用到全部节次") }
                    }
                }

                // ---- 各节起止时间 ----
                GlassCard(glass) {
                    LazyColumn(Modifier.fillMaxWidth().height((settings.sectionTimes.size * 44).dp)) {
                        itemsIndexed(settings.sectionTimes) { i, st ->
                            if (i > 0) HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingSection = st.section; pickingEnd = false }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text("第 ${st.section} 节", Modifier.weight(1f))
                                androidx.compose.animation.AnimatedContent(
                                    targetState = "${fmt(st.start.hour, st.start.minute)} - ${fmt(st.end.hour, st.end.minute)}",
                                    transitionSpec = {
                                        androidx.compose.animation.fadeIn(com.buguake.timetable.ui.theme.AppMotion.effectsFast())
                                            .togetherWith(androidx.compose.animation.fadeOut(com.buguake.timetable.ui.theme.AppMotion.effectsFast()))
                                    },
                                    label = "sectionTime",
                                ) { t ->
                                    Text(t, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.size(6.dp))
                                Icon(
                                    SketchChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- 统一设置的起始时间弹窗 ----
    if (pickingUniformStart) {
        val timeState = rememberTimePickerState(
            initialHour = uniformStart.hour,
            initialMinute = uniformStart.minute,
            is24Hour = true,
        )
        Dialog(
            onDismissRequest = { pickingUniformStart = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("第一节开始时间", style = MaterialTheme.typography.titleMedium)
                    TimePicker(state = timeState)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickingUniformStart = false }) { Text("取消") }
                        TextButton(onClick = {
                            uniformStart = java.time.LocalTime.of(timeState.hour, timeState.minute)
                            pickingUniformStart = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }

    // ---- 保存预设弹窗：输入名称 ----
    if (showSavePreset) {
        var presetName by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSavePreset = false },
            title = { Text("保存作息预设") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("预设名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        onSavePreset(presetName.trim())
                        showSavePreset = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePreset = false }) { Text("取消") }
            },
        )
    }

    // ---- 删除预设确认 ----
    pendingDeletePreset?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeletePreset = null },
            title = { Text("删除预设") },
            text = { Text("删除作息预设「$name」？当前作息不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(name)
                    pendingDeletePreset = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePreset = null }) { Text("取消") }
            },
        )
    }
    androidx.compose.runtime.key(editingSection to pickingEnd) {
        editingSection?.let { sec ->
            val current = settings.sectionTimes.firstOrNull { it.section == sec }
            if (current != null) {
                val initial = if (pickingEnd) current.end else current.start
                val timeState = rememberTimePickerState(
                    initialHour = initial.hour,
                    initialMinute = initial.minute,
                    is24Hour = true,
                )
                Dialog(
                    onDismissRequest = { editingSection = null; pendingStart = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (pickingEnd) "第 $sec 节 · 结束时间" else "第 $sec 节 · 开始时间",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TimePicker(state = timeState)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editingSection = null; pendingStart = null }) {
                                    Text("取消")
                                }
                                TextButton(onClick = {
                                    val picked = java.time.LocalTime.of(timeState.hour, timeState.minute)
                                    if (!pickingEnd) {
                                        pendingStart = picked
                                        pickingEnd = true
                                    } else {
                                        val start = pendingStart ?: current.start
                                        onSetSectionTimes(settings.sectionTimes.map {
                                            if (it.section == sec) it.copy(start = start, end = picked) else it
                                        })
                                        editingSection = null
                                        pendingStart = null
                                    }
                                }) { Text(if (pickingEnd) "确定" else "下一步") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(h: Int, m: Int): String = "%02d:%02d".format(h, m)

/** 统一设置卡片里的「标签 − 数值 +」步进行。 */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        com.buguake.timetable.ui.theme.RollingNumber(
            value = value,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
            Icon(SketchAdd, contentDescription = "增加")
        }
    }
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
