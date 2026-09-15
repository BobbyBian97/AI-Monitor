package com.aimonitor.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 查询结果持久化缓存 (普通 SharedPreferences, 只存余额/百分比/文案等非敏感数据)。
 * App 重启后立即恢复上次结果, 后台再自动刷新。
 */
class ResultCache(context: Context) {

    private val prefs = context.getSharedPreferences("result_cache", Context.MODE_PRIVATE)

    fun load(): Map<Long, BalanceResult> = runCatching {
        val root = JSONObject(prefs.getString(KEY, "{}"))
        val map = mutableMapOf<Long, BalanceResult>()
        root.keys().forEach { k ->
            val id = k.toLongOrNull() ?: return@forEach
            val o = root.optJSONObject(k) ?: return@forEach
            decode(o)?.let { map[id] = it }
        }
        map
    }.getOrDefault(emptyMap())

    fun save(results: Map<Long, BalanceResult>) {
        val root = JSONObject()
        results.forEach { (id, r) -> encode(r)?.let { root.put(id.toString(), it) } }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    /** Error/Loading/Idle 不持久化: 重启后由自动刷新重新获取 */
    private fun encode(r: BalanceResult): JSONObject? = when (r) {
        is BalanceResult.Success -> JSONObject().put("k", "s")
            .put("total", r.total)
            .put("currency", r.currency)
            .put("at", r.fetchedAt)
            .apply {
                r.granted?.let { put("granted", it) }
                r.toppedUp?.let { put("toppedUp", it) }
            }
        is BalanceResult.Usage -> JSONObject().put("k", "u")
            .put("at", r.fetchedAt)
            .put("windows", JSONArray().apply {
                r.windows.forEach { w ->
                    put(
                        JSONObject()
                            .put("label", w.label)
                            .put("percent", w.percent)
                            .put("status", w.status)
                            .apply {
                                w.resetsAt?.let { put("resetsAt", it) }
                                w.used?.let { put("used", it) }
                                w.quota?.let { put("quota", it) }
                            }
                    )
                }
            })
        is BalanceResult.Info -> JSONObject().put("k", "i")
            .put("msg", r.message)
            .put("at", r.fetchedAt)
        else -> null
    }

    private fun decode(o: JSONObject): BalanceResult? {
        return when (o.optString("k")) {
        "s" -> BalanceResult.Success(
            total = o.optDouble("total", 0.0),
            granted = if (o.has("granted")) o.optDouble("granted") else null,
            toppedUp = if (o.has("toppedUp")) o.optDouble("toppedUp") else null,
            currency = o.optString("currency", "¥"),
            fetchedAt = o.optLong("at", 0L)
        )
        "u" -> {
            val arr = o.optJSONArray("windows") ?: return null
            val windows = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    BalanceResult.UsageWindow(
                        label = it.optString("label"),
                        percent = it.optInt("percent", 0),
                        status = it.optString("status", "ok"),
                        resetsAt = if (it.has("resetsAt")) it.optLong("resetsAt") else null,
                        used = if (it.has("used")) it.optDouble("used") else null,
                        quota = if (it.has("quota")) it.optDouble("quota") else null
                    )
                }
            }
            if (windows.isEmpty()) null else BalanceResult.Usage(windows, o.optLong("at", 0L))
        }
        "i" -> BalanceResult.Info(o.optString("msg"), o.optLong("at", 0L))
        else -> null
        }
    }

    companion object {
        private const val KEY = "results_json"
    }
}
