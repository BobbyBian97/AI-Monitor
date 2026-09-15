package com.aimonitor.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aimonitor.app.data.Account
import com.aimonitor.app.data.BalanceResult
import com.aimonitor.app.ui.theme.BadgeShape
import com.aimonitor.app.ui.theme.CardShape
import com.aimonitor.app.ui.theme.Dims
import com.aimonitor.app.ui.theme.LocalStatusColors
import com.aimonitor.app.ui.theme.NumLargeStyle
import com.aimonitor.app.ui.theme.NumUsageStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountCard(
    account: Account,
    result: BalanceResult,
    onEdit: () -> Unit,
    onRefresh: () -> Unit
) {
    val visual = account.type.visual()
    val status = LocalStatusColors.current
    Card(
        onClick = onEdit,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        // Usage 窗口数 / 结果分支切换时高度平滑过渡
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(250))
    ) {
        Column(Modifier.padding(Dims.gapL)) {
            // ── 头部: 徽标 | 名称+状态点 / 供应商 | 刷新 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(Dims.avatar)
                        .clip(BadgeShape)
                        .background(visual.color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        visual.monogram,
                        color = visual.color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.width(Dims.gapM))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            account.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusDot(
                            statusColor(result),
                            pulse = result is BalanceResult.Loading
                        )
                    }
                    Text(
                        visual.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Dims.gapM))

            // ── 数据主体 (全宽, 不再被刷新按钮挤压) ──
            when (result) {
                is BalanceResult.Success -> {
                    val low = result.total < account.threshold
                    val amountColor by animateColorAsState(
                        targetValue = if (low) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        animationSpec = tween(300),
                        label = "amountColor"
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            result.currency,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        RollingNumber(result.total, NumLargeStyle, color = amountColor)
                    }
                    val details = buildList {
                        if (result.granted != null && result.toppedUp != null) {
                            add("额度 %.2f".format(result.granted + result.toppedUp))
                        }
                        result.granted?.let { add("赠金 %.2f".format(it)) }
                        result.toppedUp?.let { add("充值 %.2f".format(it)) }
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is BalanceResult.Info -> Text(
                    result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                is BalanceResult.Usage -> Column(
                    verticalArrangement = Arrangement.spacedBy(Dims.gapM)
                ) {
                    result.windows.forEach { w ->
                        val remaining = (100 - w.percent).coerceIn(0, 100)
                        val hasAbsolute = w.used != null && w.quota != null
                        val levelColor = when {
                            w.status != "ok" || remaining <= 20 -> MaterialTheme.colorScheme.error
                            remaining <= 40 -> status.warn
                            else -> status.ok
                        }
                        // 行 1: 窗口名 + 重置倒计时
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                w.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            w.resetsAt?.let {
                                Text(
                                    resetLabel(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        // 行 2: 剩余 %
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(NumUsageStyle) { append("$remaining%") }
                                },
                                color = levelColor
                            )
                            if (!hasAbsolute) {
                                Spacer(Modifier.width(Dims.gapS))
                                Text(
                                    "已用 ${w.percent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = status.warn,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                        // 行 3: 用量进度条
                        val fill by animateFloatAsState(
                            targetValue = w.percent.coerceIn(0, 100) / 100f,
                            animationSpec = tween(420),
                            label = "usageBar"
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(Dims.usageBar)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fill)
                                    .height(Dims.usageBar)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(levelColor)
                            )
                        }
                        // 行 4: 绝对额度 (余量/已用/总量 三色)
                        if (hasAbsolute) {
                            val used = w.used!!
                            val quota = w.quota!!
                            Text(
                                buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(color = levelColor, fontWeight = FontWeight.SemiBold)
                                    ) { append("余 ${fmtQuota(quota - used)}") }
                                    withStyle(
                                        SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    ) { append("  ·  ") }
                                    withStyle(SpanStyle(color = status.warn)) {
                                        append("已用 ${fmtQuota(used)}")
                                    }
                                    withStyle(
                                        SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    ) { append("  ·  ") }
                                    withStyle(
                                        SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ) { append("总量 ${fmtQuota(quota)}") }
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                is BalanceResult.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dims.gapS)
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("查询中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is BalanceResult.Error -> Column {
                    Text(
                        "查询失败",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                is BalanceResult.Idle -> Text(
                    "尚未刷新",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 底部: 通栏分隔线 + 元信息 (时间 | 订阅套餐) ──
            val timeText = when (result) {
                is BalanceResult.Success -> timeLabel(result.fetchedAt)
                is BalanceResult.Info -> timeLabel(result.fetchedAt)
                is BalanceResult.Usage -> timeLabel(result.fetchedAt)
                else -> null
            }
            account.planStats()?.let { st ->
                val renewSoon = st.nextRenewAt - System.currentTimeMillis() < 3 * 86_400_000L
                Spacer(Modifier.height(Dims.gapM))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (timeText != null) {
                        Text(
                            timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        buildAnnotatedString {
                            withStyle(
                                SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) { append("已订 ${st.cyclesPaid} 期 · 累计 ") }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFeatureSettings = "tnum"
                                )
                            ) { append("%s%.2f".format(account.currency, st.spent)) }
                            withStyle(
                                SpanStyle(
                                    color = if (renewSoon) status.warn
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                append(
                                    "  ·  " + SimpleDateFormat("MM-dd", Locale.getDefault())
                                        .format(Date(st.nextRenewAt)) + " 续费"
                                )
                            }
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } ?: timeText?.let {
                Spacer(Modifier.height(Dims.gapM))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun statusColor(result: BalanceResult): Color {
    val s = LocalStatusColors.current
    return when (result) {
        is BalanceResult.Success, is BalanceResult.Usage, is BalanceResult.Info -> s.ok
        is BalanceResult.Loading -> s.warn
        is BalanceResult.Error -> s.error
        is BalanceResult.Idle -> s.idle
    }
}

internal fun timeLabel(ts: Long): String {
    val diffSec = (System.currentTimeMillis() - ts) / 1000
    return when {
        diffSec < 60 -> "刚刚"
        diffSec < 3600 -> "${diffSec / 60} 分钟前"
        diffSec < 86400 -> "${diffSec / 3600} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

internal fun resetLabel(ts: Long): String {
    val diff = ts - System.currentTimeMillis()
    return when {
        diff <= 0 -> "已重置"
        diff < 3_600_000 -> "${diff / 60_000}分后重置"
        diff < 86_400_000 -> "${diff / 3_600_000}小时后重置"
        else -> SimpleDateFormat("MM-dd 重置", Locale.getDefault()).format(Date(ts))
    }
}

/** 额度数值: 整数不带小数点, 小数保留一位 */
internal fun fmtQuota(v: Double): String =
    if (v >= 100.0 || v == v.toInt().toDouble()) "%.0f".format(v) else "%.1f".format(v)
