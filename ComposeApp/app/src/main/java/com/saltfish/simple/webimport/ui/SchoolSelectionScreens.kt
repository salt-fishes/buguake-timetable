package com.saltfish.simple.webimport.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saltfish.simple.webimport.AdapterCategory
import com.saltfish.simple.webimport.AdapterData
import com.saltfish.simple.webimport.SchoolData
import com.saltfish.simple.webimport.SchoolIndexData

/** 学校/工具集分类筛选（null = 全部）。 */
private val CATEGORY_FILTERS = listOf(
    null to "全部",
    AdapterCategory.BACHELOR_AND_ASSOCIATE to "本科/专科",
    AdapterCategory.POSTGRADUATE to "研究生",
    AdapterCategory.GENERAL_TOOL to "通用工具",
)

/** 学校选择屏：搜索（名称/拼音首字母/代码）+ 分类筛选 + 索引刷新。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSelectionScreen(
    index: SchoolIndexData?,
    loading: Boolean,
    error: String?,
    snackbarHostState: SnackbarHostState? = null,
    onSelectSchool: (SchoolData) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    glass: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<AdapterCategory?>(null) }

    val filtered = remember(index, query, category) {
        val q = query.trim().lowercase()
        index?.schools
            ?.filter { s ->
                val catOk = category == null || s.adapters.any { it.category == category }
                val qOk = q.isBlank() ||
                    s.name.lowercase().contains(q) ||
                    s.initial.lowercase().contains(q) ||
                    s.id.lowercase().contains(q)
                catOk && qOk
            }
            ?.sortedWith(compareBy({ it.initial }, { it.name }))
            .orEmpty()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState ?: remember { SnackbarHostState() }) },
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("选择学校") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新索引")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索学校名称 / 拼音首字母 / 代码") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CATEGORY_FILTERS.forEach { (cat, label) ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(label) },
                    )
                }
            }
            when {
                loading && index == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                error != null && index == null -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "索引加载失败：\n$error\n\n请检查网络后点击右上角刷新",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Text(
                                index?.let {
                                    "共 ${it.schools.size} 个学校/工具集 · 索引 ${it.versionId}" +
                                        if (it.protocolVersion != 2) " · ⚠ 协议 v${it.protocolVersion}" else ""
                                } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        items(filtered, key = { it.id }) { school ->
                            ListItem(
                                headlineContent = { Text(school.name) },
                                supportingContent = {
                                    Text(
                                        "${school.id} · ${school.adapters.size} 个适配器",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        school.initial,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier = Modifier.clickable { onSelectSchool(school) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }
                        if (filtered.isEmpty() && index != null) {
                            item {
                                Text(
                                    "没有匹配的学校",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 适配器选择屏：某学校/工具集下的适配器列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterSelectionScreen(
    school: SchoolData,
    snackbarHostState: SnackbarHostState? = null,
    onSelectAdapter: (AdapterData) -> Unit,
    onBack: () -> Unit,
    glass: Boolean = false,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState ?: remember { SnackbarHostState() }) },
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(school.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
                    else MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(school.adapters, key = { it.adapterId }) { adapter ->
                Card(
                    onClick = { onSelectAdapter(adapter) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                adapter.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(adapter.category.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                        if (adapter.description.isNotBlank()) {
                            Text(
                                adapter.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            buildString {
                                append("维护者：${adapter.maintainer.ifBlank { "社区" }}")
                                if (adapter.importUrl.isNotBlank()) append(" · 有默认入口")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
