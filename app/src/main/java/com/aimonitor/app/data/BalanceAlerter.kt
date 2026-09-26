package com.aimonitor.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aimonitor.app.R
import org.json.JSONObject

/**
 * 低余量预警: 余量跌破账户阈值时发独立高优先级通知 (区别于常驻速览的"变色"),
 * 恢复到阈值之上自动撤销通知并重新武装; 武装状态持久化, 服务重启/开机不重复弹。
 * 由两条刷新路径驱动: MonitorService.refreshOnce (后台) 与 MainViewModel.updateNotification (前台)。
 */
object BalanceAlerter {

    private const val CHANNEL_ID = "alerts"
    private const val ID_BASE = 2000
    private const val PREFS = "alert_state"
    private const val KEY_ARMED = "armed_map"

    /** 每账户独立通知 id (2000+账户id), 可单独划掉; 与常驻通知 1001 / 行点击 100+i 互不冲突 */
    fun alertId(accountId: Long) = (ID_BASE + accountId).toInt()

    /**
     * 每次刷新后调用: 逐账户核对余量与阈值, 维护武装状态与预警通知。
     * 非 Success 结果 (失败/刷新中/纯用量) 不改变武装状态, 避免瞬时失败反复弹。
     */
    fun evaluate(ctx: Context, accounts: List<Account>, results: Map<Long, BalanceResult>) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!AppSettings.loadNotifEnabled(ctx) || !AppSettings.loadLowBalanceAlertEnabled(ctx) ||
            accounts.isEmpty() || !nm.areNotificationsEnabled()
        ) {
            // 常驻/预警开关关闭或无账户: 撤销全部预警并重置状态 (重新武装, 再开启时重新评估)
            cancelAllAndReset(ctx)
            return
        }
        val armed = loadArmed(ctx)
        for (a in accounts) {
            val r = results[a.id] as? BalanceResult.Success ?: continue
            val low = r.total <= a.threshold // 与常驻通知 warn 判定同口径
            val wasArmed = armed[a.id] ?: true
            when {
                low && wasArmed -> {
                    nm.notify(alertId(a.id), buildAlert(ctx, a, r))
                    armed[a.id] = false
                }
                !low && !wasArmed -> {
                    nm.cancel(alertId(a.id))
                    armed[a.id] = true
                }
            }
        }
        // 清扫已删除账户的残留通知与状态 (先取快照再删, 保证 cancel 能执行)
        val alive = accounts.mapTo(HashSet()) { it.id }
        armed.keys.filterNot { alive.contains(it) }.forEach { nm.cancel(alertId(it)) }
        armed.keys.retainAll(alive)
        saveArmed(ctx, armed)
    }

    /** 撤销全部预警通知并清空武装状态 (设置开关关闭时调用) */
    fun cancelAllAndReset(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        loadArmed(ctx).keys.forEach { nm.cancel(alertId(it)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun buildAlert(ctx: Context, a: Account, r: BalanceResult.Success): Notification {
        ensureChannel(ctx)
        val warn = BalanceNotifier.resolvePalette(ctx).warn
        val text = "余量 %.2f %s, 已低于阈值 %.2f".format(r.total, r.currency, a.threshold)
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle("余量预警 · ${a.displayName}")
            .setContentText(text)
            .setColor(warn)
            .setAutoCancel(true)
            // 点击直达该账户编辑页 (与通知明细行点击同机制)
            .setContentIntent(
                BalanceNotifier.deepLinkPi(ctx, a.id, (2000 + a.id % 1000).toInt())
            )
            .build()
    }

    private fun ensureChannel(ctx: Context) {
        val m = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        m.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "余量预警", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "余量低于账户阈值时单独提醒"
                setShowBadge(false)
            }
        )
    }

    /** 武装状态: {accountId: armed}, 缺省视为已武装 */
    private fun loadArmed(ctx: Context): MutableMap<Long, Boolean> {
        val map = HashMap<Long, Boolean>()
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ARMED, null) ?: return map
        runCatching {
            val o = JSONObject(raw)
            for (k in o.keys()) k.toLongOrNull()?.let { map[it] = o.optBoolean(k, true) }
        }
        return map
    }

    private fun saveArmed(ctx: Context, map: Map<Long, Boolean>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.toString(), v) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ARMED, o.toString()).apply()
    }
}
