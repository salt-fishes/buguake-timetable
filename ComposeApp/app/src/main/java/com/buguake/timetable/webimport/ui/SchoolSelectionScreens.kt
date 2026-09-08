package com.buguake.timetable.webimport.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buguake.timetable.webimport.AdapterCategory
import com.buguake.timetable.webimport.AdapterData
import com.buguake.timetable.webimport.SchoolData
import com.buguake.timetable.webimport.SchoolIndexData
import kotlinx.coroutines.launch

/** 学位帽图标（Material school 图标几何，Apache 2.0；core 图标集不含，本地自绘）。 */
private val SchoolCapIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SchoolCap",
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
            lineTo(21f, 17f)
            horizontalLineTo(23f)
            lineTo(23f, 9f)
            close()
            moveTo(18.82f, 9f)
            lineTo(12f, 12.72f)
            lineTo(5.18f, 9f)
            lineTo(12f, 5.28f)
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

/** 最近访问持久化（SharedPreferences，最多 5 条，存学校 id）。 */
private const val RECENT_PREFS = "webimport_prefs"
private const val RECENT_KEY = "recent_schools"

private fun loadRecentIds(ctx: android.content.Context): List<String> =
    ctx.getSharedPreferences(RECENT_PREFS, android.content.Context.MODE_PRIVATE)
        .getString(RECENT_KEY, "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

private fun touchRecentId(ctx: android.content.Context, id: String): List<String> {
    val prefs = ctx.getSharedPreferences(RECENT_PREFS, android.content.Context.MODE_PRIVATE)
    val next = (listOf(id) + loadRecentIds(ctx).filter { it != id }).take(5)
    prefs.edit().putString(RECENT_KEY, next.joinToString(",")).apply()
    return next
}

private fun removeRecentId(ctx: android.content.Context, id: String): List<String> {
    val prefs = ctx.getSharedPreferences(RECENT_PREFS, android.content.Context.MODE_PRIVATE)
    val next = loadRecentIds(ctx).filter { it != id }
    prefs.edit().putString(RECENT_KEY, next.joinToString(",")).apply()
    return next
}

/** 学校/工具集分类 Tab（对应拾光分类枚举；默认本科/专科）。 */
private val CATEGORY_TABS = listOf(
    AdapterCategory.BACHELOR_AND_ASSOCIATE to "本科/专科",
    AdapterCategory.POSTGRADUATE to "研究生",
    AdapterCategory.GENERAL_TOOL to "通用工具",
)

private data class SchoolRow(val letter: String?, val school: SchoolData?)

/** 学校选择屏：胶囊搜索顶栏 + 分类 Tab + 最近访问 + 首字母分组 + 字母导航条。 */
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AdapterCategory.BACHELOR_AND_ASSOCIATE) }
    var recentIds by remember { mutableStateOf(loadRecentIds(context)) }

    // 当前 Tab 的学校（搜索在 Tab 范围内进行，名称/拼音/代码三路匹配）
    val tabSchools = remember(index, category) {
        index?.schools
            ?.filter { s -> s.adapters.any { it.category == category } }
            ?.sortedWith(compareBy({ it.initial }, { it.name }))
            .orEmpty()
    }
    val filtered = remember(tabSchools, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) tabSchools
        else tabSchools.filter { s ->
            s.name.lowercase().contains(q) ||
                s.initial.lowercase().contains(q) ||
                s.id.lowercase().contains(q)
        }
    }
    val recentSchools = remember(recentIds, index) {
        recentIds.mapNotNull { id -> index?.schools?.firstOrNull { it.id == id } }
    }
    // "最近访问"区块占用的 Lazy 列表项数（字母跳转偏移用）
    val recentBlockCount = if (query.isBlank() && recentSchools.isNotEmpty()) 1 + recentSchools.size else 0

    // 首字母分组（非 A-Z 归入 #），展开为 头/条目 交替行
    val rows = remember(filtered) {
        val grouped = filtered.groupBy { s ->
            s.initial.firstOrNull()?.uppercaseChar()?.toString()?.takeIf { it in "A".."Z" } ?: "#"
        }
        buildList {
            grouped.forEach { (letter, list) ->
                add(SchoolRow(letter, null))
                list.forEach { add(SchoolRow(null, it)) }
            }
        }
    }
    val letterFirstIndex = remember(rows) {
        val map = LinkedHashMap<String, Int>()
        rows.forEachIndexed { i, row -> row.letter?.let { l -> if (l !in map) map[l] = i } }
        map
    }

    // 右滑（横向拖动累计超过阈值）返回上级
    var backDragX by remember { mutableStateOf(0f) }
    val swipeModifier = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { backDragX = 0f },
            onDragEnd = {
                if (backDragX > 260f) onBack()
                backDragX = 0f
            },
            onDragCancel = { backDragX = 0f },
        ) { change, dragAmount ->
            change.consume()
            backDragX += dragAmount
        }
    }

    Scaffold(
        modifier = swipeModifier,
        snackbarHost = { SnackbarHost(snackbarHostState ?: remember { SnackbarHostState() }) },
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
        topBar = {
            // 胶囊搜索框即顶栏：返回箭头内嵌，右侧刷新/搜索图标
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    BasicTextFieldWithPlaceholder(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "选择学校",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新索引")
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 分类 Tab
            TabRow(
                selectedTabIndex = CATEGORY_TABS.indexOfFirst { it.first == category },
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                CATEGORY_TABS.forEach { (cat, label) ->
                    Tab(
                        selected = category == cat,
                        onClick = { category = cat },
                        text = { Text(label) },
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
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(end = 26.dp),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            // 最近访问（无搜索时显示）
                            if (query.isBlank() && recentSchools.isNotEmpty()) {
                                item(key = "recent-header") {
                                    Text(
                                        "最近访问",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                items(recentSchools, key = { "recent-" + it.id }) { school ->
                                    RecentSchoolCard(
                                        school = school,
                                        onClick = { onSelectSchool(school) },
                                        onRemove = { recentIds = removeRecentId(context, school.id) },
                                    )
                                }
                            }
                            // 字母分组列表
                            rows.forEach { row ->
                                if (row.letter != null) {
                                    item(key = "letter-" + row.letter) {
                                        Text(
                                            row.letter,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                                        )
                                    }
                                } else {
                                    row.school?.let { school ->
                                        item(key = school.id) {
                                            SchoolCard(
                                                school = school,
                                                onClick = {
                                                    recentIds = touchRecentId(context, school.id)
                                                    onSelectSchool(school)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            if (filtered.isEmpty() && index != null) {
                                item {
                                    Text(
                                        if (query.isBlank()) "该分类下暂无学校" else "没有匹配的学校",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        // 右侧字母导航条：点击或上下拖动跳转（含最近访问区块偏移）
                        if (letterFirstIndex.isNotEmpty() && query.isBlank()) {
                            val letters = letterFirstIndex.keys.toList()
                            var railHeight by remember { mutableStateOf(0f) }
                            fun jumpTo(idx: Int) {
                                if (idx !in letters.indices) return
                                letterFirstIndex[letters[idx]]?.let { i ->
                                    scope.launch { listState.scrollToItem(i + recentBlockCount) }
                                }
                            }
                            Column(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .widthIn(max = 26.dp)
                                    .onSizeChanged { railHeight = it.height.toFloat() }
                                    .pointerInput(letters, recentBlockCount) {
                                        detectVerticalDragGestures { change, _ ->
                                            change.consume()
                                            if (railHeight > 0f) {
                                                jumpTo(
                                                    ((change.position.y / railHeight) * letters.size)
                                                        .toInt()
                                                        .coerceIn(0, letters.size - 1)
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                letters.forEach { letter ->
                                    Text(
                                        letter,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                letterFirstIndex[letter]?.let { i ->
                                                    scope.launch {
                                                        listState.scrollToItem(i + recentBlockCount)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 学校条目卡片：🎓 图标 + 校名（对齐拾光排版）。 */
@Composable
private fun SchoolCard(school: SchoolData, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                SchoolCapIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                school.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 最近访问卡片：可移除。 */
@Composable
private fun RecentSchoolCard(school: SchoolData, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                SchoolCapIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                school.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 无边框占位文本输入（嵌在胶囊顶栏里）。 */
@Composable
private fun BasicTextFieldWithPlaceholder(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            inner()
        },
        modifier = modifier,
    )
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
                                else append(" · 需自行输入教务网址")
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
