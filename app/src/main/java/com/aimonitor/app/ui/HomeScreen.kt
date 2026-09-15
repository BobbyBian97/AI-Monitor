package com.aimonitor.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.BalanceResult
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.HeroShape
import com.aimonitor.app.ui.theme.LocalSkin
import com.aimonitor.app.ui.theme.NumHeroStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onLogs: () -> Unit,
    onChangelog: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit
) {
    val accounts by vm.accounts.collectAsState()
    val results by vm.results.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val loading = refreshing || results.values.any { it is BalanceResult.Loading }

    // 拖动排序状态: 高度快照 (id→px) / 正在拖动的账户 / 累计位移
    val heights = remember { mutableStateMapOf<Long, Int>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val haptics = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 余量监控")
                        Text(
                            "${accounts.size} 个账户 · ${okCount(results)} 项正常",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { vm.refreshAll() },
                        enabled = !loading && accounts.isNotEmpty()
                    ) {
                        SpinningRefresh(loading)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 低频入口收纳进菜单
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("帮助") },
                            leadingIcon = { Icon(Icons.Filled.Help, contentDescription = null) },
                            onClick = { menuOpen = false; onHelp() }
                        )
                        DropdownMenuItem(
                            text = { Text("更新日志") },
                            leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                            onClick = { menuOpen = false; onChangelog() }
                        )
                        DropdownMenuItem(
                            text = { Text("调试日志") },
                            leadingIcon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                            onClick = { menuOpen = false; onLogs() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("添加账户") }
            )
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            EmptyState(Modifier.fillMaxSize().padding(padding))
        } else {
            PullRefreshBox(
                refreshing = refreshing,
                onRefresh = { vm.refreshAll() },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dims.screenH, end = Dims.screenH, top = 4.dp, bottom = Dims.listBottom
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dims.gapM)
                ) {
                    item { SummaryCard(accounts, results) }
                    items(accounts, key = { it.id }) { account ->
                        val isDragging = draggingId == account.id
                        Box(
                            Modifier
                                // 拖动中的卡片不参与位移动画 (跟随手指), 其余卡片换位平滑过渡
                                .then(if (isDragging) Modifier else Modifier.animateItemPlacement())
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                                .onSizeChanged { heights[account.id] = it.height }
                                .pointerInput(account.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingId = account.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = { draggingId = null; dragOffset = 0f },
                                        onDragCancel = { draggingId = null; dragOffset = 0f },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            dragOffset += change.positionChange().y
                                            val idx = accounts.indexOfFirst { it.id == account.id }
                                            if (idx < 0) return@detectDragGesturesAfterLongPress
                                            val own = (heights[account.id] ?: 0).toFloat()
                                            if (dragOffset > 0f) {
                                                val next = accounts.getOrNull(idx + 1)
                                                val nh = next?.let { (heights[it.id] ?: 0).toFloat() } ?: 0f
                                                if (next == null || nh <= 0f) {
                                                    dragOffset = dragOffset.coerceAtMost(own / 2f)
                                                } else if (dragOffset >= nh) {
                                                    vm.moveAccount(account.id, idx + 1)
                                                    dragOffset -= nh
                                                }
                                            } else if (dragOffset < 0f) {
                                                val prev = accounts.getOrNull(idx - 1)
                                                val ph = prev?.let { (heights[it.id] ?: 0).toFloat() } ?: 0f
                                                if (prev == null || ph <= 0f) {
                                                    dragOffset = dragOffset.coerceAtLeast(-own / 2f)
                                                } else if (-dragOffset >= ph) {
                                                    vm.moveAccount(account.id, idx - 1)
                                                    dragOffset += ph
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            AccountCard(
                                account = account,
                                result = results[account.id] ?: BalanceResult.Idle,
                                onEdit = { onEdit(account.id) },
                                onRefresh = { vm.refreshOne(account.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 轻量下拉刷新容器: 列表顶部继续下拉出现指示器, 超过阈值松手触发 [onRefresh]。
 */
@Composable
private fun PullRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 80.dp.toPx() }
    val restPx = with(density) { 48.dp.toPx() }   // 刷新时指示器停留位
    val offset = remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    // 刷新结束后收起指示器
    LaunchedEffect(refreshing) {
        if (!refreshing && offset.value > 0f) {
            animate(offset.value, 0f, animationSpec = tween(260)) { v, _ -> offset.value = v }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.Drag) return Offset.Zero
                val delta = available.y
                return if (delta > 0f) {
                    // 列表已在顶, 继续下拉 → 阻尼累积
                    offset.value = (offset.value + delta * 0.55f).coerceAtMost(thresholdPx * 1.4f)
                    available
                } else if (delta < 0f && offset.value > 0f) {
                    val prev = offset.value
                    offset.value = (prev + delta).coerceAtLeast(0f)
                    Offset(0f, prev - offset.value)
                } else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value > thresholdPx && !refreshing) {
                    onRefresh()
                    scope.launch {
                        animate(offset.value, restPx, animationSpec = tween(200)) { v, _ -> offset.value = v }
                    }
                } else if (offset.value > 0f && !refreshing) {
                    scope.launch {
                        animate(offset.value, 0f, animationSpec = tween(240)) { v, _ -> offset.value = v }
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .graphicsLayer {
                    translationY = offset.value - restPx
                    alpha = (offset.value / restPx).coerceIn(0f, 1f)
                }
        ) {
            if (offset.value > 1f || refreshing) {
                CircularProgressIndicator(
                    Modifier.size(26.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SpinningRefresh(loading: Boolean) {
    val transition = rememberInfiniteTransition()
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing))
    )
    Icon(
        Icons.Filled.Refresh,
        contentDescription = "刷新全部",
        modifier = Modifier.rotate(if (loading) angle else 0f)
    )
}

private fun okCount(results: Map<Long, BalanceResult>): Int =
    results.values.count {
        it is BalanceResult.Success || it is BalanceResult.Info || it is BalanceResult.Usage
    }

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    // 圆徽轻微呼吸, 引导视线到下方按钮
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse)
    )
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Brush.linearGradient(LocalSkin.current.gradient), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "余",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(22.dp))
        Text("还没有账户", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "点击下方「添加账户」开始监控余额",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryCard(accounts: List<Account>, results: Map<Long, BalanceResult>) {
    val totals = LinkedHashMap<String, Double>()
    results.values.forEach { r ->
        if (r is BalanceResult.Success) {
            totals[r.currency] = (totals[r.currency] ?: 0.0) + r.total
        }
    }
    if (totals.isEmpty()) return

    val covered = results.values.count { it is BalanceResult.Success }
    val lastUpdate = results.values
        .filterIsInstance<BalanceResult.Success>()
        .maxOfOrNull { it.fetchedAt }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, HeroShape)
            .background(Brush.linearGradient(LocalSkin.current.gradient), HeroShape)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // 顶行: 标签 + 覆盖胶囊
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "总余量",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "覆盖 $covered/${accounts.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.12f), BadgeShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        // 数字区: 每币种一列, 底对齐
        Row(verticalAlignment = Alignment.Bottom) {
            totals.entries.forEachIndexed { i, (currency, value) ->
                if (i > 0) {
                    Box(
                        Modifier
                            .padding(horizontal = Dims.gapL)
                            .width(1.dp)
                            .height(34.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    RollingNumber(value, NumHeroStyle, color = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        currency,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Spacer(Modifier.height(10.dp))
        // 底行: 更新时间 | 套餐累计投入 (按币种汇总)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (lastUpdate != null) {
                Text(
                    "更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastUpdate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.weight(1f))
            val planTotals = LinkedHashMap<String, Double>()
            accounts.forEach { a ->
                a.planStats()?.let { st ->
                    planTotals[a.currency] = (planTotals[a.currency] ?: 0.0) + st.spent
                }
            }
            if (planTotals.isNotEmpty()) {
                Text(
                    "套餐累计投入  " + planTotals.entries.joinToString("   ") {
                        "%.2f %s".format(it.value, it.key)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}
