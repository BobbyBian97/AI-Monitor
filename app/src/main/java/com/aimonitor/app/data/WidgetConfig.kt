package com.aimonitor.app.data

import android.content.Context
import org.json.JSONArray

/**
 * 桌面小部件每实例的账户筛选配置 (明文 prefs, 不含敏感信息)。
 * 无条目 = 显示全部账户 (旧版本升级上来的实例自然兼容);
 * 空选择保存时按「显示全部」处理, 避免出现空组件。
 */
object WidgetConfig {

    private const val PREFS = "widget_config"
    private const val KEY_PREFIX = "sel_"

    /** 该实例勾选的账户 id 集; null 表示未配置过 = 显示全部 */
    fun load(ctx: Context, appWidgetId: Int): Set<Long>? {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + appWidgetId, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildSet { for (i in 0 until arr.length()) add(arr.getLong(i)) }
        }.getOrNull()
    }

    /** 保存选择; 空集视为「显示全部」(删 key) */
    fun save(ctx: Context, appWidgetId: Int, ids: Set<Long>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply {
                val key = KEY_PREFIX + appWidgetId
                if (ids.isEmpty()) remove(key)
                else putString(key, JSONArray(ids.toList()).toString())
            }
            .apply()
    }

    /** 组件实例被删除时清理, 防止 prefs 泄漏 */
    fun delete(ctx: Context, appWidgetId: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PREFIX + appWidgetId)
            .apply()
    }
}
