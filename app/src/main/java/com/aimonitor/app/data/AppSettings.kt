package com.aimonitor.app.data

import android.content.Context

/**
 * 全局应用设置 (明文 SharedPreferences, 不含敏感信息)。
 */
object AppSettings {

    /** 定时刷新间隔选项: 15秒 / 30秒 / 1分钟 / 5分钟 / 10分钟 */
    val intervalOptions = longArrayOf(15_000L, 30_000L, 60_000L, 300_000L, 600_000L)
    const val DEFAULT_INTERVAL = 60_000L

    private const val PREFS = "app_settings"
    private const val KEY_INTERVAL = "refresh_interval_ms"
    private const val KEY_SKIN = "skin_id"
    private const val KEY_BG = "bg_path"
    private const val KEY_CUSTOM_SKIN = "custom_skin"
    private const val KEY_NOTIF = "notif_enabled"
    private const val KEY_NOTIF_DETAIL = "notif_detail"
    private const val KEY_LOW_BALANCE_ALERT = "low_balance_alert"
    private const val KEY_UPDATE_URL = "update_manifest_url"

    /**
     * 应用内更新的清单地址, 由用户在设置页自行填写。
     * 不内置默认值, 因此安装包内不含任何服务器地址; 为空表示未启用更新检查。
     */
    fun loadUpdateUrl(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UPDATE_URL, null)?.trim().orEmpty()

    fun saveUpdateUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_UPDATE_URL, url.trim()).apply()
    }

    /** 通知栏常驻展示 (默认开启) */
    fun loadNotifEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIF, true)

    fun saveNotifEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIF, on).apply()
    }

    /** 通知栏折叠时也显示明细 (前两条, 警示优先); 关则折叠仅显示总余量摘要 */
    fun loadNotifDetail(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIF_DETAIL, true)

    fun saveNotifDetail(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIF_DETAIL, on).apply()
    }

    /** 低余量预警通知: 余量跌破阈值时单独高优先级提醒 (默认开启; 需常驻通知开启) */
    fun loadLowBalanceAlertEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOW_BALANCE_ALERT, true)

    fun saveLowBalanceAlertEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LOW_BALANCE_ALERT, on).apply()
    }

    fun loadRefreshInterval(ctx: Context): Long {
        val v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL, DEFAULT_INTERVAL)
        return if (intervalOptions.contains(v)) v else DEFAULT_INTERVAL
    }

    fun saveRefreshInterval(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_INTERVAL, ms).apply()
    }

    fun loadSkinId(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SKIN, null)
            ?: "blue"

    fun saveSkinId(ctx: Context, id: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SKIN, id).apply()
    }

    /** 自定义背景图路径 (已复制到应用私有目录), null 表示未设置 */
    fun loadBgPath(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BG, null)

    fun saveBgPath(ctx: Context, path: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (path == null) remove(KEY_BG) else putString(KEY_BG, path) }
            .apply()
    }

    /** 从图片提取的皮肤 (序列化色值, 见 ImageSkin), null 表示未生成过 */
    fun loadCustomSkin(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CUSTOM_SKIN, null)

    fun saveCustomSkin(ctx: Context, raw: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (raw == null) remove(KEY_CUSTOM_SKIN) else putString(KEY_CUSTOM_SKIN, raw) }
            .apply()
    }

    fun intervalLabel(ms: Long): String = when {
        ms < 60_000L -> "${ms / 1000} 秒"
        else -> "${ms / 60_000} 分钟"
    }
}
