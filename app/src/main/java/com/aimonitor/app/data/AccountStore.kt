package com.aimonitor.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * 账户持久化: EncryptedSharedPreferences + JSON 序列化
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "aimonitor_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): List<Account> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        // 逐条容错: 单条损坏只跳过该条, 避免解析失败清空全部后 save 覆盖掉所有账户
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { fromJson(arr.getJSONObject(i)) }
                .onFailure { AppLog.e("STORE", "账户数据第 $i 条损坏, 已跳过: ${it.message}") }
                .getOrNull()
        }
    }

    fun save(accounts: List<Account>) {
        val arr = JSONArray()
        accounts.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(a: Account) = JSONObject().apply {
        put("id", a.id)
        put("type", a.type.name)
        put("name", a.name)
        put("apiKey", a.apiKey)
        put("accessKey", a.accessKey)
        put("secretKey", a.secretKey)
        put("baseUrl", a.baseUrl)
        put("customPath", a.customPath)
        put("customCurrency", a.customCurrency)
        put("threshold", a.threshold)
        put("planPrice", a.planPrice)
        put("planCycleDays", a.planCycleDays)
        put("planStartAt", a.planStartAt)
    }

    private fun fromJson(o: JSONObject) = Account(
        id = o.optLong("id", System.currentTimeMillis()),
        type = ProviderType.from(o.optString("type")),
        name = o.optString("name", ""),
        apiKey = o.optString("apiKey", ""),
        accessKey = o.optString("accessKey", ""),
        secretKey = o.optString("secretKey", ""),
        baseUrl = o.optString("baseUrl", ""),
        customPath = o.optString("customPath", ""),
        customCurrency = o.optString("customCurrency", "¥"),
        threshold = o.optDouble("threshold", 10.0),
        planPrice = o.optDouble("planPrice", 0.0),
        planCycleDays = o.optInt("planCycleDays", 0),
        planStartAt = o.optLong("planStartAt", 0L)
    )

    companion object {
        private const val KEY = "accounts_json"
    }
}
