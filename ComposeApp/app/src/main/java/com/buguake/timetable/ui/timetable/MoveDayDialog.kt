package com.buguake.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buguake.timetable.data.WeekCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("M月d日")
private val DAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 按日期调课弹窗：选「被调日期 → 调到日期」（任意日期，可跨周），落点当天课程直接覆盖。
 * 按日期调课天然是一次性调整，无范围选项。
 */
@Composable
fun MoveDayDialog(
    semesterStart: LocalDate?,
    onConfirm: (fromWeek: Int, fromDay: Int, toWeek: Int, toDay: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromDate by remember { mutableStateOf(LocalDate.now()) }
    var toDate by remember { mutableStateOf(LocalDate.now().plus(1, ChronoUnit.DAYS)) }
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    val fromWeek = WeekCalculator.currentWeek(semesterStart, fromDate)
    val toWeek = WeekCalculator.currentWeek(semesterStart, toDate)
    val fromDay = fromDate.dayOfWeek.value
    val toDay = toDate.dayOfWeek.value
    val sameSlot = fromWeek == toWeek && fromDay == toDay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按日期调课") },
        text = {
            Column {
                DateSlot("把哪一天的课调走？", fromDate, fromWeek) { pickingFrom = true }
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    Text(
                        "↓ 调到",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DateSlot("调到哪一天？", toDate, toWeek) { pickingTo = true }
                Text(
                    "落点当天已有的课程会被直接覆盖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sameSlot,
                onClick = { onConfirm(fromWeek, fromDay, toWeek, toDay) },
            ) { Text("确认调课", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (pickingFrom) {
        DatePick(initial = fromDate, onPick = { fromDate = it; pickingFrom = false }, onDismiss = { pickingFrom = false })
    }
    if (pickingTo) {
        DatePick(initial = toDate, onPick = { toDate = it; pickingTo = false }, onDismiss = { pickingTo = false })
    }
}

@Composable
private fun DateSlot(label: String, date: LocalDate, week: Int, onPick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        "${date.format(DATE_FMT)} ${DAY_NAMES[date.dayOfWeek.value - 1]} · 第 $week 周" +
            if (week < 1) "（学期外）" else "",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                1.5.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(10.dp),
            )
            .clickable { onPick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun DatePick(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}
