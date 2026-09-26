package com.aimonitor.app.data

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 迷你小部件入口: 总余量一行速览 + 刷新按钮, 无配置页。
 * 数据更新与明细组件同源同推送点 (BalanceWidget.push);
 * 系统重绑(重启/添加)时读缓存自渲染。
 */
class MiniWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val handoff = goAsync()
        Thread {
            try {
                val ctx = context.applicationContext
                val accounts = AccountStore(ctx).load()
                val results = ResultCache(ctx).load()
                for (id in appWidgetIds) {
                    appWidgetManager.updateAppWidget(id, BalanceWidget.renderMini(ctx, accounts, results))
                }
            } finally {
                handoff.finish()
            }
        }.start()
    }
}
