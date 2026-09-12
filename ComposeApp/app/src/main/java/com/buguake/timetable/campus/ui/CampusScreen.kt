package com.buguake.timetable.campus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 校园 tab 首页：以功能入口列表承载校园相关功能。
 * 新增功能只需在 [CAMPUS_FEATURES] 追加一条，首页与二级页导航自动生效。
 *
 * 二级页（功能页）与三级页（功能页内部页）统一由 [CampusStepHost] 提供
 * 进入/退出方向一致的滑动 + 淡入淡出动画。
 */
@Composable
fun CampusScreen(
    glass: Boolean = false,
    showSnackbar: (String) -> Unit,
    /** 「长按应用图标 → 快速开锁」快捷方式序号（>0 时直接进入宿舍开门页并开门）。 */
    openUnlockSeq: Int = 0,
    /** 开门页消费完本次直达请求后回传序号，供上层做一次性放行（防切页/返回重复开门）。 */
    onUnlockConsumed: (Int) -> Unit = {},
) {
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }

    // 快捷方式进入：直达宿舍开门页（页内会用默认门锁自动开门）
    LaunchedEffect(openUnlockSeq) {
        if (openUnlockSeq > 0) activeId = CAMPUS_FEATURES.first().id
    }

    // 兜底返回：功能页自身若未处理返回，则退回首页
    BackHandler(enabled = activeId != null) { activeId = null }

    CampusStepHost(
        step = activeId,
        depth = { if (it == null) 0 else 1 },
        onBack = { activeId = null },
    ) { id ->
        val feature = CAMPUS_FEATURES.firstOrNull { it.id == id }
        if (feature == null) {
            CampusHome(glass = glass, onOpen = { activeId = it })
        } else {
            feature.content(glass, showSnackbar, openUnlockSeq, onUnlockConsumed) { activeId = null }
        }
    }
}

/** 校园首页列表。 */
@Composable
private fun CampusHome(glass: Boolean, onOpen: (String) -> Unit) {
    Scaffold(containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
    else MaterialTheme.colorScheme.surface) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 104.dp),
        ) {
            Text(
                "校园",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                "学习与生活服务入口",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            CAMPUS_FEATURES.forEach { f ->
                CampusEntryCard(f) { onOpen(f.id) }
            }
        }
    }
}

/** 功能入口卡片：图标 + 标题 + 副标题 + 右向箭头。 */
@Composable
private fun CampusEntryCard(feature: CampusFeature, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(feature.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    feature.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
