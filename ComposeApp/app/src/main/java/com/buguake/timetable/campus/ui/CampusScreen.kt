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
 */
@Composable
fun CampusScreen(glass: Boolean = false, showSnackbar: (String) -> Unit) {
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    val feature = CAMPUS_FEATURES.firstOrNull { it.id == activeId }

    BackHandler(enabled = feature != null) { activeId = null }

    if (feature != null) {
        feature.content(glass, showSnackbar) { activeId = null }
        return
    }

    Scaffold(containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
    else MaterialTheme.colorScheme.surface) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
                CampusEntryCard(f) { activeId = f.id }
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
