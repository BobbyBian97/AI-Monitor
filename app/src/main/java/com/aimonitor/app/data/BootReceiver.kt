package com.aimonitor.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 开机完成 / 应用自更新完成后自动恢复后台监控:
 * 若「通知栏常驻展示」开关为开, 拉起前台服务并补发一次刷新动作,
 * 常驻通知无需手动打开 App 即可恢复。
 * 两个广播均在 Android 12+ 前台服务后台启动豁免列表内, 启动合法;
 * 仍以 try-catch 兜底部分 ROM 在开机阶段的额外限制。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val ctx = context.applicationContext
        try {
            // 必须先检查开关: start() 会因关闭而不启动,
            // 但直发的 ACTION_REFRESH intent 会无条件拉起服务
            if (!AppSettings.loadNotifEnabled(ctx)) {
                AppLog.i("APP", "$action: 监控开关关闭, 不恢复服务")
                return
            }
            // 与磁贴 onClick 相同的双调用: start() 启动服务走定时循环,
            // ACTION_REFRESH 补发立即刷新, 让通知尽快显示新数据
            MonitorService.start(ctx)
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_REFRESH)
            )
            AppLog.i("APP", "$action: 已恢复后台监控服务")
        } catch (e: Exception) {
            AppLog.e("APP", "$action: 恢复服务失败: ${e.message}")
        }
    }
}
