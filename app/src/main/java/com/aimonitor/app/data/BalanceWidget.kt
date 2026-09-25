package com.aimonitor.app.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.aimonitor.app.MainActivity
import com.aimonitor.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面小部件渲染: 总余量标题 + 逐账户明细(名称/数值/用量进度条) + 页脚。
 * 数据与通知栏同源 (accounts + results), 行映射/调色板复用 [BalanceNotifier];
 * 平时由 MonitorService.refreshOnce 与 MainViewModel.updateNotification 的推送点同步更新,
 * 系统重绑(重启/添加/拉伸)时由 [BalanceWidgetProvider] 读缓存自渲染。
 */
object BalanceWidget {

    private const val MAX_ROWS = 8

    private val rowIds = intArrayOf(
        R.id.wg_row_0, R.id.wg_row_1, R.id.wg_row_2, R.id.wg_row_3,
        R.id.wg_row_4, R.id.wg_row_5, R.id.wg_row_6, R.id.wg_row_7
    )
    private val nameIds = intArrayOf(
        R.id.wg_name_0, R.id.wg_name_1, R.id.wg_name_2, R.id.wg_name_3,
        R.id.wg_name_4, R.id.wg_name_5, R.id.wg_name_6, R.id.wg_name_7
    )
    private val valIds = intArrayOf(
        R.id.wg_val_0, R.id.wg_val_1, R.id.wg_val_2, R.id.wg_val_3,
        R.id.wg_val_4, R.id.wg_val_5, R.id.wg_val_6, R.id.wg_val_7
    )
    private val barIds = intArrayOf(
        R.id.wg_bar_0, R.id.wg_bar_1, R.id.wg_bar_2, R.id.wg_bar_3,
        R.id.wg_bar_4, R.id.wg_bar_5, R.id.wg_bar_6, R.id.wg_bar_7
    )

    /** 推送全部小部件更新 (无小部件时零开销) */
    fun push(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>) {
        val awm = AppWidgetManager.getInstance(ctx)
        val ids = awm.getAppWidgetIds(ComponentName(ctx, BalanceWidgetProvider::class.java))
        for (id in ids) {
            awm.updateAppWidget(id, render(ctx, accounts, results, rowsForHeight(awm, id)))
        }
    }

    /** 按小部件当前高度估算可容纳的明细行数 (默认 180dp ≈ 3 行, 纵向拉伸增加) */
    internal fun rowsForHeight(awm: AppWidgetManager, id: Int): Int {
        val dp = awm.getAppWidgetOptions(id)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        return ((dp - 96) / 26).coerceIn(2, MAX_ROWS)
    }

    /** 构建小部件视图 (结构仿通知栏展开视图; 配色显式指定, 亮暗随系统切换) */
    fun render(
        ctx: Context,
        accounts: List<Account>,
        results: Map<Long, BalanceResult>,
        maxRows: Int
    ): RemoteViews {
        val rows = BalanceNotifier.rowsOf(accounts, results)
        val totals = BalanceSummary.currencyTotals(results.values)
        val title = if (totals.isEmpty()) "AI 余量监控"
        else "总余量  " + totals.entries.joinToString("   ") {
            "${"%.2f".format(it.value)} ${it.key}"
        }
        val palette = BalanceNotifier.resolvePalette(ctx)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val footer = if (accounts.isEmpty()) "点击卡片添加账户"
        else "更新于 $time · ${accounts.size} 个账户" +
            (if (rows.size > maxRows) " · +${rows.size - maxRows} 项" else "")

        val rv = RemoteViews(ctx.packageName, R.layout.widget_balance)
        rv.setTextViewText(R.id.wg_title, title)
        rv.setTextColor(R.id.wg_title, palette.accent)
        val shown = rows.take(maxRows)
        shown.forEachIndexed { i, row ->
            rv.setViewVisibility(rowIds[i], View.VISIBLE)
            rv.setTextViewText(nameIds[i], row.name)
            rv.setTextViewText(valIds[i], row.value)
            rv.setTextColor(valIds[i], if (row.warn) palette.warn else palette.accent)
            if (row.percent != null) {
                rv.setProgressBar(barIds[i], 100, row.percent.coerceIn(0, 100), false)
                // 警示行进度条染警示色 (API 31+; 低版本保留 XML 默认色)
                if (row.warn && Build.VERSION.SDK_INT >= 31) {
                    rv.setColorStateList(
                        barIds[i], "setProgressTintList", ColorStateList.valueOf(palette.warn)
                    )
                }
                rv.setViewVisibility(barIds[i], View.VISIBLE)
            } else rv.setViewVisibility(barIds[i], View.GONE)
        }
        for (i in shown.size until MAX_ROWS) rv.setViewVisibility(rowIds[i], View.GONE)
        rv.setTextViewText(R.id.wg_footer, footer)
        // 整卡点击进 App; 「刷新」拉起前台服务立即重拉 (服务死亡时也能安全启动)
        rv.setOnClickPendingIntent(
            R.id.wg_root,
            PendingIntent.getActivity(
                ctx, 10, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        rv.setOnClickPendingIntent(
            R.id.wg_refresh,
            PendingIntent.getForegroundService(
                ctx, 11,
                Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        rv.setTextColor(R.id.wg_refresh, palette.accent)
        return rv
    }
}
