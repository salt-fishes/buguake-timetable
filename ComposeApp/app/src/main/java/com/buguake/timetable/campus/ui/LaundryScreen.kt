package com.buguake.timetable.campus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buguake.timetable.campus.laundry.DeviceStatus
import com.buguake.timetable.campus.laundry.LaundryDevice
import com.buguake.timetable.campus.laundry.LaundryHouse
import com.buguake.timetable.campus.laundry.LaundryJump
import com.buguake.timetable.campus.laundry.LaundryStore
import com.buguake.timetable.campus.laundry.LaundryStoreInfo
import com.buguake.timetable.campus.laundry.ShunshuiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 洗衣房 · 主页（当前门店 + 楼栋列表）。
 * 门店栏可点击换门店；门店详情（分类+楼栋）缓存离线可用，失败时提示并保留缓存。
 */
@Composable
fun LaundryHome(
    glass: Boolean = false,
    store: LaundryStore,
    client: ShunshuiClient,
    showSnackbar: (String) -> Unit,
    onPickStore: () -> Unit,
    onOpenHouse: (LaundryHouse, LaundryStoreInfo) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val default = store.defaultStore()
    var info by remember { mutableStateOf<LaundryStoreInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    fun loadInfo() {
        val d = default ?: return
        scope.launch {
            loading = true
            loadError = null
            runCatching { client.storeInfo(d) }
                .onSuccess {
                    info = it
                    store.saveStoreInfo(d.id, it)
                }
                .onFailure { e ->
                    loadError = e.message
                    if (info == null) info = store.loadStoreInfo(d.id)
                }
            loading = false
        }
    }

    LaunchedEffect(default?.id) {
        if (default != null) {
            if (info == null) info = store.loadStoreInfo(default.id)
            loadInfo()
        }
    }

    Scaffold(
        containerColor = if (glass) androidx.compose.ui.graphics.Color.Transparent
        else MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                // 门店栏可点：换校区/换门店
                TextButton(onClick = onPickStore) {
                    Text(
                        default?.name ?: "未选择门店",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (default != null) {
                    IconButton(onClick = { loadInfo() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                }
            }

            if (default == null) {
                EmptyHint("还没有选择门店，先选一家吧", actionLabel = "去选择门店") { onPickStore() }
            } else {
                loadError?.let { err ->
                    Text(
                        err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                val houses = info?.houses.orEmpty()
                if (houses.isEmpty() && !loading) {
                    EmptyHint(
                        loadError ?: "该门店暂无楼栋信息，试试换一家门店",
                        actionLabel = "换门店",
                    ) { onPickStore() }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(houses, key = { it.id }) { h ->
                            HouseCard(h, highlighted = h.id == store.lastHouseId(default.id)) {
                                store.setLastHouseId(default.id, h.id)
                                info?.let { onOpenHouse(h, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 楼栋卡片：楼栋名 / 登记设备数 / 在线或停用。 */
@Composable
private fun HouseCard(h: LaundryHouse, highlighted: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(h.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${h.count} 台" + if (h.onlineUse) "" else " · 已停用",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, actionLabel: String, action: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = action) { Text(actionLabel) }
    }
}

/**
 * 洗衣房 · 设备状态页（洗衣机/烘干机等多分类，随门店动态渲染）。
 *
 * - 首次进入逐分类拉全（每类各自分页，翻到空页为止）；15s 轮询只重拉当前选中分类；
 * - 剩余时间为本地倒计时，不做每秒请求；
 * - 仅页面组合存活（可见）时轮询，本模块无任何后台任务。
 */
@Composable
fun LaundryDevices(
    glass: Boolean = false,
    store: LaundryStore,
    client: ShunshuiClient,
    info: LaundryStoreInfo,
    house: LaundryHouse,
    showSnackbar: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val categories = info.categories
    var selected by rememberSaveable { mutableStateOf(0) }
    // categoryId → 设备列表；fetchAtMs 记录各类拉取时间（本地倒计时基准）
    val devices = remember { mutableStateMapOf<Int, List<LaundryDevice>>() }
    val fetchAtMs = remember { mutableStateMapOf<Int, Long>() }
    var loading by remember { mutableStateOf(categories.isNotEmpty()) }
    var tick by remember { mutableStateOf(0) }

    val currentCategory = categories.getOrNull(selected) ?: categories.firstOrNull()

    suspend fun fetchCategory(catId: Int) {
        runCatching { client.pagedDevices(info.storeId, house.id, catId) }
            .onSuccess { list ->
                devices[catId] = list
                fetchAtMs[catId] = System.currentTimeMillis()
                store.saveSnapshot(info.storeId, house.id, list)
            }
            .onFailure { showSnackbar(it.message ?: "网络请求失败") }
    }

    // 首次进入：缓存快照先上屏，再逐分类拉全
    LaunchedEffect(house.id) {
        store.loadSnapshot(info.storeId, house.id)?.let { (cached, at) ->
            if (devices.isEmpty() && cached.isNotEmpty()) {
                cached.groupBy { it.categoryId }.forEach { (k, v) ->
                    devices[k] = v
                    fetchAtMs[k] = at
                }
                loading = false
            }
        }
        categories.forEach { cat -> fetchCategory(cat.id) }
        loading = false
    }

    // 15s 轮询：只重拉当前选中分类
    LaunchedEffect(house.id, selected) {
        while (isActive) {
            delay(15_000)
            currentCategory?.let { fetchCategory(it.id) }
        }
    }

    // 本地倒计时驱动（每秒重组；无运行中设备时无可见变化）
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            tick++
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text(house.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "数据更新于 " + (currentCategory?.let { fetchAtMs[it.id] }?.let { formatClock(it) } ?: "—"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 分类切换：只有 1 类时隐藏（单分类门店）
        if (categories.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories.size) { i ->
                    val cat = categories[i]
                    val list = devices[cat.id].orEmpty()
                    val idle = list.count { it.status == DeviceStatus.IDLE }
                    FilterChip(
                        selected = i == selected,
                        onClick = { selected = i },
                        label = { Text("${cat.name} $idle/${list.size}") },
                    )
                }
            }
        }

        val list = currentCategory?.let { devices[it.id] }.orEmpty()
        val idleCount = list.count { it.status == DeviceStatus.IDLE && it.online }

        if (loading && list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list.isEmpty()) {
            Text(
                "本楼暂无${currentCategory?.name ?: "设备"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                "空闲 $idleCount / 共 ${list.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { d ->
                    DeviceCard(d, nowMs = fetchAtMs[d.categoryId] ?: 0L, tick = tick) {
                        val opened = LaundryJump.openInMiniProgram(context, d.actionCode)
                        if (!opened) showSnackbar("跳转失败，链接已复制，可在微信内打开")
                    }
                }
            }
        }
    }
}

/** 设备卡片：名称 / 状态 / 剩余时间 / 去开洗。 */
@Composable
private fun DeviceCard(d: LaundryDevice, nowMs: Long, tick: Int, onWash: () -> Unit) {
    // 本地倒计时：抓取时的剩余秒 − 已流逝秒（tick 仅触发重组）
    val elapsedSec = if (nowMs > 0) ((System.currentTimeMillis() - nowMs) / 1000).toInt() else 0
    val remain = (d.remainSeconds - elapsedSec).coerceAtLeast(0)
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                StatusDot(d.status, d.online)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !d.online -> "离线"
                    d.status == DeviceStatus.IDLE -> "空闲"
                    d.status == DeviceStatus.RUNNING -> "剩余 ${formatRemain(remain)}"
                    d.status == DeviceStatus.PROTECTING -> "保护中"
                    else -> "未知"
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !d.online -> MaterialTheme.colorScheme.onSurfaceVariant
                    d.status == DeviceStatus.IDLE -> MaterialTheme.colorScheme.primary
                    d.status == DeviceStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                    d.status == DeviceStatus.PROTECTING -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onWash,
                enabled = d.online && d.actionCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) { Text("去开洗") }
        }
    }
}

@Composable
private fun StatusDot(status: DeviceStatus, online: Boolean) {
    val color = when {
        !online -> MaterialTheme.colorScheme.outline
        status == DeviceStatus.IDLE -> MaterialTheme.colorScheme.primary
        status == DeviceStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        status == DeviceStatus.PROTECTING -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
}

private fun formatRemain(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

private fun formatClock(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
