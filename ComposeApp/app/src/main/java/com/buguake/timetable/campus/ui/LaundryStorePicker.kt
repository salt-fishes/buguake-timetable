package com.buguake.timetable.campus.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.buguake.timetable.campus.YunmeiNetworkException
import com.buguake.timetable.campus.laundry.LaundryStore
import com.buguake.timetable.campus.laundry.LaundryStoreItem
import com.buguake.timetable.campus.laundry.ShunshuiClient
import com.buguake.timetable.campus.laundry.distanceKm
import com.buguake.timetable.campus.laundry.distanceLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 洗衣房 · 门店选择页（功能的入口）。
 *
 * 双通道发现门店：附近门店（按坐标，主通道）+ 门店名搜索（兜底）。
 * 实测搜索只匹配门店名子串且结果不全，提示语必须引导输入完整校名；
 * 定位仅在你点「附近门店」时用一次，拒绝授权不阻断（退化为搜索通道）。
 */
@Composable
fun LaundryStorePicker(
    glass: Boolean = false,
    store: LaundryStore,
    client: ShunshuiClient,
    showSnackbar: (String) -> Unit,
    onSelect: (LaundryStoreItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf(store.lastKeyword) }
    var results by remember { mutableStateOf<List<LaundryStoreItem>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    // 仅内存：用户本次会话的定位坐标（不入库、不上传）
    var userLoc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locationUsable by remember { mutableStateOf(hasCoarseLocation(context)) }

    suspend fun searchNow(kw: String) {
        searching = true
        results = runCatching { client.searchStores(kw) }
            .onFailure { if (it is YunmeiNetworkException) showSnackbar(it.message ?: "网络请求失败") }
            .getOrDefault(emptyList())
        searching = false
        searched = true
        store.lastKeyword = kw
    }

    suspend fun fetchNearbyNow() {
        val loc = userLoc ?: return
        locating = true
        val list = runCatching { client.nearStores(loc.first, loc.second) }
            .onFailure { showSnackbar(it.message ?: "网络请求失败") }
            .getOrDefault(emptyList())
        if (list.isNotEmpty()) {
            results = list
            store.saveCandidates(list)
            searched = false
        } else {
            showSnackbar("附近没找到门店，试试直接搜索学校名")
        }
        locating = false
    }

    fun locateAndFetch() {
        scope.launch {
            if (userLoc == null) {
                userLoc = lastKnownLocation(context)
                if (userLoc == null) {
                    showSnackbar("拿不到定位，请直接搜索学校名")
                    return@launch
                }
            }
            fetchNearbyNow()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationUsable = granted && hasCoarseLocation(context)
        if (granted) locateAndFetch()
        // 拒绝后不重复弹窗：按钮退化为提示，搜索通道照常可用
    }

    // 搜索 400ms 防抖；空串清空结果
    LaunchedEffect(keyword) {
        val kw = keyword.trim()
        if (kw.isEmpty()) {
            results = emptyList(); searched = false; return@LaunchedEffect
        }
        delay(400)
        searchNow(kw)
    }

    // 打开时先展示上次「附近门店」的缓存候选（离线也可切换）
    LaunchedEffect(Unit) {
        if (keyword.isBlank()) {
            store.loadCandidates()?.let { (cached, _) -> results = cached }
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
                Text("选择门店", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("输入完整学校/门店名") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (locationUsable) locateAndFetch()
                        else locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !locating,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (locating) "定位中…" else "附近门店")
                }
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                if (locationUsable) "定位仅用于本次列出附近门店，不上传、不留存"
                else "未授权定位：请直接搜索学校名（输入完整名称）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            val recent = store.recentStores()
            if (recent.isNotEmpty()) {
                Text(
                    "最近选择",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recent.isNotEmpty()) {
                    items(recent, key = { "recent_${it.id}" }) { s ->
                        StoreCard(s, userLoc = null) { onSelect(s) }
                    }
                    item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) }
                }
                if (searched && results.isEmpty() && !searching) {
                    item {
                        Text(
                            "没搜到。试试更完整的名称（如 XX大学），或用「附近门店」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(results, key = { "r_${it.id}" }) { s ->
                    StoreCard(s, userLoc = userLoc) { onSelect(s) }
                }
            }
        }
    }
}

/** 门店卡片：名称 / 地址 / 标签（+ 附近模式下的距离）。 */
@Composable
private fun StoreCard(s: LaundryStoreItem, userLoc: Pair<Double, Double>?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                if (s.address.isNotBlank()) {
                    Text(
                        s.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                if (s.tags.isNotEmpty()) {
                    Text(
                        s.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            userLoc?.let { loc ->
                Text(
                    distanceLabel(distanceKm(loc.first, loc.second, s.lat, s.lng)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hasCoarseLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** 一次性取系统最近已知位置（不申请后台、不持续监听）；拿不到返回 null。 */
private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (!hasCoarseLocation(context)) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var best: Location? = null
    var bestAccuracy = Float.MAX_VALUE
    for (provider in lm.getProviders(true)) {
        val l = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
        if (l.accuracy < bestAccuracy) {
            best = l
            bestAccuracy = l.accuracy
        }
    }
    return best?.let { it.latitude to it.longitude }
}
