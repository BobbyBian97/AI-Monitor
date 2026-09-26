package com.aimonitor.app.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * 桌面小部件入口(明细版): 添加/系统重绑(如重启)/拉伸尺寸时回调。
 * 渲染数据来自加密账户存储 + 结果缓存, 并应用该实例的账户筛选配置 ([WidgetConfig]);
 * 加载在后台线程, onUpdate 经 goAsync 保证广播窗口不提前结束;
 * 平时数据新鲜度由 MonitorService 的定时刷新推送维持。
 */
class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val handoff = goAsync()
        Thread {
            try {
                renderAll(context, appWidgetManager, appWidgetIds)
            } finally {
                handoff.finish()
            }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        // 拉伸改变高度: 按新尺寸重算行数并重渲染
        Thread { renderAll(context, appWidgetManager, intArrayOf(appWidgetId)) }.start()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 实例被移除: 清理其筛选配置, 防止 prefs 泄漏
        val ctx = context.applicationContext
        appWidgetIds.forEach { WidgetConfig.delete(ctx, it) }
    }

    private fun renderAll(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val ctx = context.applicationContext
        val accounts = AccountStore(ctx).load()
        val results = ResultCache(ctx).load()
        for (id in ids) {
            awm.updateAppWidget(id, BalanceWidget.renderFor(awm, id, ctx, accounts, results))
        }
    }
}
