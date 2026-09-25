package com.aimonitor.app.data

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下拉通知栏快速设置磁贴 (类 NFC/蓝牙开关):
 * 点击切换通知栏常驻监控开关 (开=启动后台服务并立即刷新, 关=停止并移除通知),
 * 副标题常显各币种总余量, 下拉面板即可速览。
 * 状态在面板展开/添加磁贴时经 [onStartListening]/[onTileAdded] 拉取,
 * 与设置页「通知栏常驻展示」开关天然保持一致, 无需反向接线。
 */
class BalanceTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        // 服务已死且开关为开时拉活 (start 自带 running 去重, 存活时零开销)
        try { MonitorService.start(applicationContext) } catch (_: Exception) {}
        refreshTile()
    }

    override fun onTileAdded() {
        refreshTile()
    }

    override fun onClick() {
        val ctx = applicationContext
        val on = !AppSettings.loadNotifEnabled(ctx)
        AppSettings.saveNotifEnabled(ctx, on)
        AppLog.i("APP", if (on) "磁贴开启监控" else "磁贴关闭监控")
        if (on) {
            // 磁贴点击属于前台上下文, 允许启动前台服务; 补发刷新动作立即拉取一次
            MonitorService.start(ctx)
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_REFRESH)
            )
        } else {
            MonitorService.stop(ctx)
            BalanceNotifier.cancel(ctx)
        }
        refreshTile()
    }

    /** 拉取开关状态与缓存汇总, 回主线程更新磁贴 (qsTile 仅主线程可访问) */
    private fun refreshTile() {
        val tile = qsTile ?: return
        scope.launch {
            val ctx = applicationContext
            val on = AppSettings.loadNotifEnabled(ctx)
            val totals = BalanceSummary.currencyTotals(ResultCache(ctx).load().values)
            val subtitle = if (totals.isEmpty()) "暂无数据"
            else totals.entries.joinToString(" · ") {
                "%.1f%s".format(it.value, BalanceNotifier.curSymbol(it.key))
            }
            withContext(Dispatchers.Main) {
                tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = subtitle
                tile.updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
