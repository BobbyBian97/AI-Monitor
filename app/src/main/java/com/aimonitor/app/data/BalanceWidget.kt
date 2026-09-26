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
 * 桌面小部件渲染: 明细组件 (总余量标题 + 逐账户明细 + 页脚) 与迷你组件 (总余量速览)。
 * 数据与通知栏同源 (accounts + results), 行映射/调色板复用 [BalanceNotifier];
 * 平时由 MonitorService.refreshOnce 与 MainViewModel.updateNotification 的推送点同步更新,
 * 系统重绑(重启/添加/拉伸)时由 Provider 读缓存自渲染。
 * 明细组件支持每实例账户筛选 (见 [WidgetConfig], 长按组件可重新配置)。
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

    /** 推送全部小部件更新: 明细 + 迷你 (无实例时零开销) */
    fun push(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>) {
        val awm = AppWidgetManager.getInstance(ctx)
        forEachWidget(awm, ctx, BalanceWidgetProvider::class.java) { id ->
            awm.updateAppWidget(id, renderFor(awm, id, ctx, accounts, results))
        }
        forEachWidget(awm, ctx, MiniWidgetProvider::class.java) { id ->
            awm.updateAppWidget(id, renderMini(ctx, accounts, results))
        }
    }

    private inline fun forEachWidget(
        awm: AppWidgetManager,
        ctx: Context,
        provider: Class<out android.appwidget.AppWidgetProvider>,
        block: (Int) -> Unit
    ) {
        val ids = awm.getAppWidgetIds(ComponentName(ctx, provider))
        for (id in ids) block(id)
    }

    /** 按小部件当前高度估算可容纳的明细行数 (默认 180dp ≈ 3 行, 纵向拉伸增加) */
    internal fun rowsForHeight(awm: AppWidgetManager, id: Int): Int {
        val dp = awm.getAppWidgetOptions(id)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        return ((dp - 96) / 26).coerceIn(2, MAX_ROWS)
    }

    /**
     * 渲染某个明细组件实例: 先按该实例的账户筛选配置过滤,
     * 选择的账户已全部被删时页脚给出提示 (标题仍显示全局汇总)。
     */
    fun renderFor(
        awm: AppWidgetManager,
        id: Int,
        ctx: Context,
        accounts: List<Account>,
        results: Map<Long, BalanceResult>
    ): RemoteViews {
        val selected = WidgetConfig.load(ctx, id)
        val visible = selected?.let { sel -> accounts.filter { it.id in sel } } ?: accounts
        val allGone = selected != null && selected.isNotEmpty() && visible.isEmpty()
        return render(
            ctx, visible, results, rowsForHeight(awm, id),
            footerOverride = if (allGone) "所选账户均已删除" else null
        )
    }

    /** 构建明细组件视图 (结构仿通知栏展开视图; 配色显式指定, 亮暗随系统切换) */
    fun render(
        ctx: Context,
        accounts: List<Account>,
        results: Map<Long, BalanceResult>,
        maxRows: Int,
        footerOverride: String? = null
    ): RemoteViews {
        val rows = BalanceNotifier.rowsOf(accounts, results)
            .sortedByDescending { it.warn } // 警示账户排前 (稳定排序, 组内保序)
        val totals = BalanceSummary.currencyTotals(results.values)
        val title = if (totals.isEmpty()) "AI 余量监控"
        else "总余量  " + totals.entries.joinToString("   ") {
            "${"%.2f".format(it.value)} ${it.key}"
        }
        val palette = BalanceNotifier.resolvePalette(ctx)
        // 真实数据抓取时间 (与通知栏页脚同口径), 重建小部件不再误显示为当前时间
        val latest = BalanceNotifier.latestFetchAt(results)
        val timePart = if (latest > 0L)
            "更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(latest)) +
                " · " + BalanceNotifier.relAge(System.currentTimeMillis(), latest)
        else "暂无数据"
        val footer = footerOverride ?: (
            if (accounts.isEmpty()) "点击卡片添加账户"
            else "$timePart · ${accounts.size} 个账户" +
                (if (rows.size > maxRows) " · +${rows.size - maxRows} 项" else "")
            )

        val rv = RemoteViews(ctx.packageName, R.layout.widget_balance)
        rv.setTextViewText(R.id.wg_title, title)
        // 任一可见账户警示时标题染警示色, 与折叠通知速览行同口径
        rv.setTextColor(R.id.wg_title, if (rows.any { it.warn }) palette.warn else palette.accent)
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
            // 明细行点击: 直达该账户编辑页 (请求码 300+i, 与通知行/迷你/按钮互不冲突)
            row.accountId?.let { accountId ->
                rv.setOnClickPendingIntent(rowIds[i], BalanceNotifier.deepLinkPi(ctx, accountId, 300 + i))
            }
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

    /** 迷你组件: 一行总余量速览 + 刷新按钮, 占屏小, 不设配置页 */
    fun renderMini(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>): RemoteViews {
        val totals = BalanceSummary.currencyTotals(results.values)
        val warn = BalanceNotifier.rowsOf(accounts, results).any { it.warn }
        val title = if (totals.isEmpty()) "AI 余量监控"
        else totals.entries.joinToString("  ·  ") {
            "%.2f %s".format(it.value, BalanceNotifier.curSymbol(it.key))
        }
        val palette = BalanceNotifier.resolvePalette(ctx)
        val latest = BalanceNotifier.latestFetchAt(results)
        val sub = if (accounts.isEmpty()) "点击添加账户"
        else "${accounts.size} 个账户 · " + (
            if (latest > 0L) BalanceNotifier.relAge(System.currentTimeMillis(), latest) else "暂无数据"
            )

        val rv = RemoteViews(ctx.packageName, R.layout.widget_mini)
        rv.setTextViewText(R.id.wg_m_title, title)
        rv.setTextColor(R.id.wg_m_title, if (warn) palette.warn else palette.accent)
        rv.setTextViewText(R.id.wg_m_sub, sub)
        rv.setOnClickPendingIntent(
            R.id.wg_m_root,
            PendingIntent.getActivity(
                ctx, 20, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        rv.setOnClickPendingIntent(
            R.id.wg_m_refresh,
            PendingIntent.getForegroundService(
                ctx, 21,
                Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        rv.setTextColor(R.id.wg_m_refresh, palette.accent)
        return rv
    }
}
