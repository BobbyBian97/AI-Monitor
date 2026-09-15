package com.aimonitor.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aimonitor.app.MainActivity
import com.aimonitor.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知栏常驻展示: 折叠为总余量摘要, 展开为自定义布局
 * (逐账户明细 + 套餐用量进度条 + 低额/超八成警示 + 更新时间)。
 */
object BalanceNotifier {
    private const val CHANNEL_ID = "balances"
    const val NOTIF_ID = 1001
    private const val MAX_ROWS = 8
    /** 折叠态速览行宽度预算 (半角字符数), 超出以 +N 折叠 */
    private const val COMPACT_WIDTH = 50
    private val ACCENT = 0xFF1565C0.toInt()
    private val WARN = 0xFFD93025.toInt()

    private val rowIds = intArrayOf(
        R.id.nf_row_0, R.id.nf_row_1, R.id.nf_row_2, R.id.nf_row_3,
        R.id.nf_row_4, R.id.nf_row_5, R.id.nf_row_6, R.id.nf_row_7
    )
    private val nameIds = intArrayOf(
        R.id.nf_name_0, R.id.nf_name_1, R.id.nf_name_2, R.id.nf_name_3,
        R.id.nf_name_4, R.id.nf_name_5, R.id.nf_name_6, R.id.nf_name_7
    )
    private val valIds = intArrayOf(
        R.id.nf_val_0, R.id.nf_val_1, R.id.nf_val_2, R.id.nf_val_3,
        R.id.nf_val_4, R.id.nf_val_5, R.id.nf_val_6, R.id.nf_val_7
    )
    private val barIds = intArrayOf(
        R.id.nf_bar_0, R.id.nf_bar_1, R.id.nf_bar_2, R.id.nf_bar_3,
        R.id.nf_bar_4, R.id.nf_bar_5, R.id.nf_bar_6, R.id.nf_bar_7
    )

    private class Row(
        val name: String,
        val value: String,
        val percent: Int? = null,
        val warn: Boolean = false,
        val compact: String = "—"
    )

    /** 刷新通知: 设置关闭/无权限/无账户时移除 */
    fun update(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!AppSettings.loadNotifEnabled(ctx) || !nm.areNotificationsEnabled() || accounts.isEmpty()) {
            nm.cancel(NOTIF_ID)
            return
        }
        nm.notify(NOTIF_ID, build(ctx, accounts, results)!!)
    }

    /** 构建常驻通知 (供前台服务 startForeground 使用); 设置关闭或无账户时返回 null */
    fun build(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>): Notification? {
        if (!AppSettings.loadNotifEnabled(ctx) || accounts.isEmpty()) return null
        ensureChannel(ctx)

        val totals = LinkedHashMap<String, Double>()
        val rows = accounts.map { a ->
            when (val r = results[a.id]) {
                is BalanceResult.Success -> {
                    totals[r.currency] = (totals[r.currency] ?: 0.0) + r.total
                    Row(
                        a.displayName,
                        "%.2f %s".format(r.total, r.currency),
                        warn = r.total <= a.threshold,
                        compact = "%.1f%s".format(r.total, curSymbol(r.currency))
                    )
                }
                is BalanceResult.Usage -> {
                    val txt = r.windows.joinToString(" · ") { "${it.label} ${it.percent}%" }
                    val maxP = r.windows.maxOfOrNull { it.percent }
                    Row(
                        a.displayName, txt.ifBlank { "无用量数据" }, maxP, (maxP ?: 0) >= 80,
                        compact = maxP?.let { "$it%" } ?: "无数据"
                    )
                }
                is BalanceResult.Info -> Row(a.displayName, r.message.take(20), compact = r.message.take(6))
                is BalanceResult.Error -> Row(a.displayName, "查询失败", warn = true, compact = "失败")
                is BalanceResult.Loading -> Row(a.displayName, "刷新中…", compact = "…")
                else -> Row(a.displayName, "尚未刷新")
            }
        }

        val title = if (totals.isEmpty()) "AI 余量监控"
        else "总余量  " + totals.entries.joinToString("   ") { "${"%.2f".format(it.value)} ${it.key}" }

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        // 套餐累计投入 (按币种)
        val planTotals = LinkedHashMap<String, Double>()
        accounts.forEach { a ->
            a.planStats()?.let { planTotals[a.currency] = (planTotals[a.currency] ?: 0.0) + it.spent }
        }
        val footerText = "更新于 $time · ${accounts.size} 个账户" +
            (if (rows.size > MAX_ROWS) " · 仅显示前 $MAX_ROWS 项" else "") +
            planTotals.entries.joinToString("") { " · 累计投入 ${"%.1f".format(it.value)} ${it.key}" }

        // 展开视图: 全部明细 + 进度条 + 页脚
        val big = buildViews(ctx, title, rows, footerText, showBars = true)

        // 折叠视图: 系统限制 ~48dp, 标题 + 单行账户速览 (警示优先)
        val compact = if (AppSettings.loadNotifDetail(ctx)) buildCompactViews(ctx, title, rows)
        else null

        // 折叠摘要
        val summary = buildString {
            append("${accounts.size} 个账户")
            totals.entries.forEach { append("   ${"%.1f".format(it.value)} ${it.key}") }
        }

        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(title)
            .setContentText(summary)
            .apply { compact?.let { setCustomContentView(it) } }
            .setCustomBigContentView(big)
            .setColor(ACCENT)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return n
    }

    /** 折叠态紧凑布局: 标题 + 单行速览, 尽量显示全部账户, 超宽部分以 +N 标注 */
    private fun buildCompactViews(ctx: Context, title: String, rows: List<Row>): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.notif_balance_compact)
        rv.setTextViewText(R.id.nf_c_title, title)
        val sorted = rows.sortedByDescending { it.warn }
        val parts = ArrayList<String>(sorted.size)
        var used = 0
        for (r in sorted) {
            val item = "${shortName(r.name)} ${r.compact}"
            val w = widthUnits(item)
            if (parts.isNotEmpty() && used + 3 + w > COMPACT_WIDTH) break
            parts += item
            used += (if (parts.size > 1) 3 else 0) + w
        }
        val rest = sorted.size - parts.size
        var line = parts.joinToString(" · ")
        if (rest > 0) line += "  +$rest"
        rv.setTextViewText(R.id.nf_c_line, line.ifBlank { "暂无数据" })
        rv.setTextColor(R.id.nf_c_line, if (sorted.any { it.warn }) WARN else ACCENT)
        return rv
    }

    /** 名称压缩到 4 个半角宽 (2 个汉字 / 4 个字母) */
    private fun shortName(n: String): String {
        var u = 0
        val sb = StringBuilder()
        for (c in n) {
            val cu = if (c.code < 0x2E80) 1 else 2
            if (u + cu > 4) break
            sb.append(c)
            u += cu
        }
        return sb.toString()
    }

    /** 估算显示宽度: 全角=2, 半角=1 (单位: 半角字符) */
    private fun widthUnits(s: String): Int =
        s.fold(0) { acc, c -> acc + if (c.code < 0x2E80) 1 else 2 }

    private fun curSymbol(c: String) = when (c) {
        "USD" -> "$"
        "CNY", "RMB" -> "¥"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> c
    }

    /** 填充自定义布局: [maxRows] 行内明细, [footerText] 为 null 时隐藏页脚 */
    private fun buildViews(
        ctx: Context,
        title: String,
        rows: List<Row>,
        footerText: String?,
        showBars: Boolean,
        maxRows: Int = MAX_ROWS
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.notif_balance)
        rv.setTextViewText(R.id.nf_header, title)
        val shown = rows.take(maxRows)
        shown.forEachIndexed { i, row ->
            rv.setViewVisibility(rowIds[i], View.VISIBLE)
            rv.setTextViewText(nameIds[i], row.name)
            rv.setTextViewText(valIds[i], row.value)
            rv.setTextColor(valIds[i], if (row.warn) WARN else ACCENT)
            if (showBars && row.percent != null) {
                rv.setProgressBar(barIds[i], 100, row.percent.coerceIn(0, 100), false)
                rv.setViewVisibility(barIds[i], View.VISIBLE)
            } else rv.setViewVisibility(barIds[i], View.GONE)
        }
        for (i in shown.size until MAX_ROWS) rv.setViewVisibility(rowIds[i], View.GONE)
        if (footerText != null) {
            rv.setViewVisibility(R.id.nf_footer_row, View.VISIBLE)
            rv.setTextViewText(R.id.nf_footer, footerText)
        } else rv.setViewVisibility(R.id.nf_footer_row, View.GONE)
        // 「刷新」按钮: 触发前台服务立即重新拉取并更新通知
        rv.setOnClickPendingIntent(R.id.nf_refresh, refreshPendingIntent(ctx))
        return rv
    }

    /** 「刷新」按钮点击: 通知前台服务立即重拉并刷新通知 (复用其原有刷新逻辑) */
    private fun refreshPendingIntent(ctx: Context): PendingIntent = PendingIntent.getService(
        ctx, 1,
        Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)

    private fun ensureChannel(ctx: Context) {
        val m = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "余量速览", NotificationManager.IMPORTANCE_LOW).apply {
                description = "通知栏常驻展示各账户余量"
                setShowBadge(false)
            }
        )
    }
}
