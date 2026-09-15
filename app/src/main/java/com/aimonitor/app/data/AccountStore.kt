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
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
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

    fun nextId(accounts: List<Account>): Long =
        (accounts.maxOfOrNull { it.id } ?: 0L) + 1L

    companion object {
        private const val KEY = "accounts_json"
    }
}
