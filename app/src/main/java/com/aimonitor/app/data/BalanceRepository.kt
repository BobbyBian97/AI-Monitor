package com.aimonitor.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * 各供应商余额查询与解析
 */
object BalanceRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 并发上限: 全部账户同时刷新时限制在途请求数, 避免突发大量连接 */
    private val fetchGate = Semaphore(4)

    suspend fun fetch(account: Account): BalanceResult = withContext(Dispatchers.IO) {
        fetchGate.withPermit {
            val t0 = System.currentTimeMillis()
            try {
                val r = when (account.type) {
                    ProviderType.DEEPSEEK -> fetchDeepSeek(account)
                    ProviderType.SILICONFLOW -> fetchSiliconFlow(account)
                    ProviderType.MOONSHOT -> fetchMoonshot(account)
                    ProviderType.ZHIPU -> fetchZhipu(account)
                    ProviderType.VOLCENGINE -> fetchVolcano(account)
                    ProviderType.OPENCODE -> fetchOpencode(account)
                    ProviderType.OPENROUTER -> fetchOpenRouter(account)
                    ProviderType.ONE_API -> fetchOneApi(account)
                    ProviderType.CUSTOM -> fetchCustom(account)
                }
                AppLog.i(
                    "FETCH",
                    "${account.displayName} · ${account.type.displayName} → ${describe(r)} · ${System.currentTimeMillis() - t0}ms"
                )
                r
            } catch (e: Exception) {
                AppLog.e(
                    "FETCH",
                    "${account.displayName} · ${account.type.displayName} 异常 ${e.javaClass.simpleName}: ${e.message ?: "无信息"}"
                )
                BalanceResult.Error(friendlyError(e))
            }
        }
    }

    private fun describe(r: BalanceResult): String = when (r) {
        is BalanceResult.Success -> "Success ${r.currency}%.2f".format(r.total)
        is BalanceResult.Usage -> "Usage ${r.windows.size}窗口(${r.windows.joinToString { it.label }})"
        is BalanceResult.Info -> "Info ${r.message.take(40)}"
        is BalanceResult.Error -> "Error ${r.message.take(60)}"
        is BalanceResult.Loading -> "Loading"
        is BalanceResult.Idle -> "Idle"
    }

    /** GET 请求, Authorization 头为 Bearer <apiKey> */
    private fun get(url: String, apiKey: String): JSONObject =
        getAuthed(url, "Bearer $apiKey")

    /** GET 请求, Authorization 头原样使用 (如智谱不带 Bearer 前缀) */
    private fun getAuthed(url: String, authHeader: String): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(120)}")
            return JSONObject(body)
        }
    }

    // ---------- DeepSeek ----------
    private fun fetchDeepSeek(a: Account): BalanceResult {
        val json = get("${ProviderType.DEEPSEEK.defaultBaseUrl}/user/balance", a.apiKey)
        val infos = json.optJSONArray("balance_infos")
            ?: throw IOException("响应缺少 balance_infos: ${json.toString().take(150)}")
        if (infos.length() == 0) throw IOException("balance_infos 为空")
        val o = infos.getJSONObject(0)
        return BalanceResult.Success(
            total = o.optString("total_balance").toDoubleOrNull()
                ?: throw IOException("total_balance 解析失败"),
            granted = o.optString("granted_balance").toDoubleOrNull(),
            toppedUp = o.optString("topped_up_balance").toDoubleOrNull(),
            currency = if (o.optString("currency") == "USD") "$" else "¥"
        )
    }

    // ---------- 硅基流动 ----------
    private fun fetchSiliconFlow(a: Account): BalanceResult {
        val json = get("${ProviderType.SILICONFLOW.defaultBaseUrl}/v1/user/info", a.apiKey)
        val data = json.optJSONObject("data")
            ?: throw IOException("响应缺少 data: ${json.toString().take(150)}")
        val balance = data.optString("balance").toDoubleOrNull()
            ?: throw IOException("balance 解析失败")
        val total = data.optString("totalBalance").toDoubleOrNull()
        val charge = data.optString("chargeBalance").toDoubleOrNull()
        val granted = if (total != null && charge != null && total >= charge) total - charge else null
        return BalanceResult.Success(
            total = balance,
            granted = granted,
            toppedUp = charge,
            currency = "¥"
        )
    }

    // ---------- Moonshot ----------
    private fun fetchMoonshot(a: Account): BalanceResult {
        val json = get("${ProviderType.MOONSHOT.defaultBaseUrl}/v1/users/me/balance", a.apiKey)
        val data = json.optJSONObject("data")
            ?: throw IOException("响应缺少 data: ${json.toString().take(150)}")
        return BalanceResult.Success(
            total = data.optString("available_balance").toDoubleOrNull()
                ?: throw IOException("available_balance 解析失败"),
            granted = data.optString("free").toDoubleOrNull(),
            toppedUp = data.optString("cash_balance").toDoubleOrNull(),
            currency = "¥"
        )
    }

    /**
     * 智谱: 套餐配额查询与余额查询并行发出, 优先取套餐窗口数据,
     * 无套餐数据时用余额结果 (认证头均为 Authorization: <key>, 不带 Bearer)
     */
    private suspend fun fetchZhipu(a: Account): BalanceResult = coroutineScope {
        val quotaDeferred = async {
            try {
                val quotaBase = if (a.baseUrl.contains("z.ai", ignoreCase = true))
                    "https://api.z.ai" else "https://open.bigmodel.cn"
                val json = getAuthed("$quotaBase/api/monitor/usage/quota/limit", a.apiKey)
                val data = json.optJSONObject("data")
                if (data != null && json.optBoolean("success", true)) {
                    parseZhipuQuota(data)
                } else {
                    AppLog.i("ZHIPU", "quota 接口无 data (非套餐密钥?), 回退余额查询")
                    emptyList()
                }
            } catch (e: IOException) {
                // 非套餐密钥: 回退余额查询
                AppLog.i("ZHIPU", "quota 接口失败 ${e.javaClass.simpleName}: ${e.message?.take(70)} → 回退余额查询")
                emptyList()
            }
        }
        val balanceDeferred = async { fetchZhipuBalance(a) }
        val windows = quotaDeferred.await()
        if (windows.isNotEmpty()) {
            balanceDeferred.cancel()
            AppLog.i("ZHIPU", "quota 接口命中 → ${windows.size} 窗口(${windows.joinToString { it.label }})")
            BalanceResult.Usage(windows)
        } else {
            balanceDeferred.await()
        }
    }

    /** 智谱余额查询 (quota 接口无窗口数据时的回退路径) */
    private fun fetchZhipuBalance(a: Account): BalanceResult {
        val json = getAuthed(
            "${ProviderType.ZHIPU.defaultBaseUrl}/api/biz/account/query-customer-account-report",
            a.apiKey
        )
        val data = json.optJSONObject("data")
            ?: throw IOException("响应缺少 data: ${json.toString().take(150)}")
        val total = data.optString("availableBalance").toDoubleOrNull()
            ?: data.optString("balance").toDoubleOrNull()
            ?: throw IOException("余额字段解析失败")
        return BalanceResult.Success(
            total = total,
            granted = data.optString("giveAmount").toDoubleOrNull(),
            toppedUp = data.optString("rechargeAmount").toDoubleOrNull(),
            currency = "¥"
        )
    }

    /**
     * 智谱套餐配额: limits[] 内
     * - TOKENS_LIMIT unit=3 → 5小时, unit=6 → 周 (仅百分比); unit 缺失按重置时间兜底
     * - TIME_LIMIT → 月工具调用, 含绝对值 usage(总量)/currentValue(已用)/remaining
     */
    private fun parseZhipuQuota(data: JSONObject): List<BalanceResult.UsageWindow> {
        val limits = data.optJSONArray("limits") ?: return emptyList()
        var fiveHour: Triple<Long, Long?, BalanceResult.UsageWindow>? = null
        var weekly: Triple<Long, Long?, BalanceResult.UsageWindow>? = null
        var tools: BalanceResult.UsageWindow? = null
        val unclassified = mutableListOf<Triple<Long, Long?, BalanceResult.UsageWindow>>()

        fun entry(item: JSONObject): Triple<Long, Long?, BalanceResult.UsageWindow>? {
            val pct = item.optDouble("percentage", -1.0)
            if (pct < 0) return null
            val reset = item.optLong("nextResetTime", 0L).takeIf { it > 0 }
            val w = BalanceResult.UsageWindow(
                label = "", percent = pct.toInt().coerceIn(0, 100),
                status = "ok", resetsAt = reset
            )
            return Triple(reset ?: Long.MIN_VALUE, reset, w)
        }

        for (i in 0 until limits.length()) {
            val item = limits.optJSONObject(i) ?: continue
            val type = item.optString("type")
            if (type.equals("TIME_LIMIT", ignoreCase = true)) {
                val pct = item.optDouble("percentage", -1.0)
                if (pct < 0) continue
                tools = BalanceResult.UsageWindow(
                    label = "月工具",
                    percent = pct.toInt().coerceIn(0, 100),
                    status = "ok",
                    resetsAt = item.optLong("nextResetTime", 0L).takeIf { it > 0 },
                    used = item.optDouble("currentValue", Double.NaN).takeIf { !it.isNaN() },
                    quota = item.optDouble("usage", Double.NaN).takeIf { !it.isNaN() }
                )
            } else if (type.equals("TOKENS_LIMIT", true) || type.equals("CREDIT_LIMIT", true)) {
                val e = entry(item) ?: continue
                when (item.optInt("unit", -1)) {
                    3 -> if (fiveHour == null) fiveHour = e else unclassified += e
                    6 -> if (weekly == null) weekly = e else unclassified += e
                    else -> unclassified += e
                }
            }
        }
        // unit 缺失兜底: 无重置时间的优先归 5小时 (5小时桶 0% 时可能无 reset), 其余按重置时间升序
        unclassified.sortBy { it.first }
        for (e in unclassified) {
            if (fiveHour == null) fiveHour = e else if (weekly == null) weekly = e
        }

        return buildList {
            fiveHour?.let { e -> add(e.third.copy(label = "5小时")) }
            weekly?.let { e -> add(e.third.copy(label = "周")) }
            tools?.let { add(it) }
        }
    }

    // ---------- 火山方舟 ----------
    // Agent Plan / Coding Plan 用量走控制面 OpenAPI (open.volcengineapi.com),
    // 需火山账号 AccessKey + Secret (火山签名 V4); 推理 Bearer Key 无法查询用量。
    // 未填 AK/SK 时回退为推理密钥探测验证 (不消耗 token)。
    private fun fetchVolcano(a: Account): BalanceResult {
        if (a.accessKey.isNotBlank() && a.secretKey.isNotBlank()) return fetchVolcanoUsage(a)
        AppLog.i("VOLC", "未填 AccessKey/Secret, 走推理密钥探测 (key=${AppLog.fp(a.apiKey)})")
        return volcanoKeyProbe(a)
    }

    private fun volcanoKeyProbe(a: Account): BalanceResult {
        val probe = "{\"model\":\"__key_probe__\",\"max_tokens\":1," +
            "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"
        val req = Request.Builder()
            .url("${ProviderType.VOLCENGINE.defaultBaseUrl}/api/plan/v1/messages")
            .header("Authorization", "Bearer ${a.apiKey}")
            .post(probe.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            AppLog.i("VOLC", "ark 密钥探测 → HTTP ${resp.code}")
            return when {
                resp.code == 401 || resp.code == 403 ->
                    BalanceResult.Error("密钥无效: " + parseArkError(body))
                resp.code in 400..499 ->
                    // 到达模型校验层 (如 UnsupportedModel) 说明密钥有效
                    BalanceResult.Info("密钥有效 · 填 AccessKey 可查套餐用量")
                else -> BalanceResult.Error("HTTP ${resp.code}: ${body.take(120)}")
            }
        }
    }

    private sealed class VolcCall {
        data class Body(val json: JSONObject) : VolcCall()
        data class Auth(val message: String) : VolcCall()
        data class Soft(val message: String) : VolcCall()
    }

    private fun fetchVolcanoUsage(a: Account): BalanceResult {
        val region = volcanoRegion(a.baseUrl)
        AppLog.i(
            "VOLC",
            "套餐查询 GetAFPUsage region=$region ak=${AppLog.fp(a.accessKey)} sk=${AppLog.fp(a.secretKey)}"
        )
        val errors = mutableListOf<String>()

        // 1) Agent Plan: GetAFPUsage (回绝对额度 Quota/Used)
        when (val r = volcanoOpenApiCall(region, a.accessKey, a.secretKey, "GetAFPUsage")) {
            is VolcCall.Auth -> return BalanceResult.Error(r.message)
            is VolcCall.Soft -> errors += "GetAFPUsage: ${r.message}"
            is VolcCall.Body -> {
                val windows = parseAfpTiers(r.json.optJSONObject("Result") ?: r.json)
                if (windows.isNotEmpty()) return BalanceResult.Usage(windows)
            }
        }

        // 2) Coding Plan: GetCodingPlanUsage (回百分比)
        when (val r = volcanoOpenApiCall(region, a.accessKey, a.secretKey, "GetCodingPlanUsage")) {
            is VolcCall.Auth -> return BalanceResult.Error(r.message)
            is VolcCall.Soft -> errors += "GetCodingPlanUsage: ${r.message}"
            is VolcCall.Body -> {
                val windows = parseCodingPlanTiers(r.json.optJSONObject("Result") ?: r.json)
                if (windows.isNotEmpty()) return BalanceResult.Usage(windows)
            }
        }

        if (errors.isNotEmpty()) return BalanceResult.Error(errors.joinToString("; ").take(200))
        return BalanceResult.Info("AccessKey 有效 · 未检测到 Agent/Coding Plan 订阅")
    }

    private const val VOLC_OPENAPI_HOST = "open.volcengineapi.com"

    /** 从数据面 base_url 提取控制面 Region (ark.cn-beijing.volces.com → cn-beijing) */
    private fun volcanoRegion(baseUrl: String): String {
        val host = baseUrl.substringAfter("://", baseUrl).substringBefore('/')
        return host.split(".").firstOrNull { it.startsWith("cn-") || it.startsWith("ap-") }
            ?: "cn-beijing"
    }

    /**
     * 火山签名 V4 调用控制面 OpenAPI (AWS SigV4 变体):
     * 固定顺序 SignedHeaders、algorithm 无 AWS4 前缀、scope 结尾为 request
     */
    private fun volcanoOpenApiCall(
        region: String,
        accessKey: String,
        secretKey: String,
        action: String
    ): VolcCall {
        val query = "Action=$action&Region=$region&Version=2024-01-01"
        val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        val xDate = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val shortDate = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        val bodySha = sha256Hex(ByteArray(0))
        val signedHeaders = "host;x-date;x-content-sha256;content-type"
        val canonicalHeaders = "host:$VOLC_OPENAPI_HOST\nx-date:$xDate\n" +
            "x-content-sha256:$bodySha\ncontent-type:application/json; charset=utf-8\n"
        val canonicalRequest = "POST\n/\n$query\n$canonicalHeaders\n$signedHeaders\n$bodySha"
        val scope = "$shortDate/$region/ark/request"
        val stringToSign = "HMAC-SHA256\n$xDate\n$scope\n" +
            sha256Hex(canonicalRequest.toByteArray())
        var key = hmacSha256(secretKey.toByteArray(), shortDate.toByteArray())
        key = hmacSha256(key, region.toByteArray())
        key = hmacSha256(key, "ark".toByteArray())
        key = hmacSha256(key, "request".toByteArray())
        val signature = hmacSha256(key, stringToSign.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val authorization = "HMAC-SHA256 Credential=$accessKey/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        AppLog.i("VOLC", "签名 xDate=$xDate sig=${signature.take(16)}…(${signature.length})")

        val req = Request.Builder()
            .url("https://$VOLC_OPENAPI_HOST/?$query")
            .header("Authorization", authorization)
            .header("X-Date", xDate)
            .header("X-Content-Sha256", bodySha)
            .post("".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val json = runCatching { JSONObject(body) }.getOrNull()
            // RequestId 前 14 位是服务器时间(UTC+8), 用于检测手机时钟偏差
            json?.optJSONObject("ResponseMetadata")?.optString("RequestId")?.let { rid ->
                if (rid.length >= 14) runCatching {
                    val serverTime = java.time.LocalDateTime.parse(
                        rid.take(14),
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    ).atOffset(java.time.ZoneOffset.ofHours(8)).toInstant().toEpochMilli()
                    val diff = (serverTime - System.currentTimeMillis()) / 1000
                    if (kotlin.math.abs(diff) > 120)
                        AppLog.e("VOLC", "手机时钟偏差 ${diff}s (超过2分钟, 会破坏签名校验!)")
                    else
                        AppLog.i("VOLC", "时钟偏差 ${diff}s")
                }
            }
            // 业务错误常以 200 + ResponseMetadata.Error 返回
            if (json != null) volcResponseError(json)?.let { (code, msg) ->
                AppLog.e("VOLC", "$action → $code: ${msg.take(80)}")
                return if (!isVolcAuthCode(code)) VolcCall.Soft("API 错误 ($code): ${msg.take(120)}")
                else if (code.contains("Signature", true)) VolcCall.Auth(
                    "Secret 错误或不完整 ($code): 火山 Secret 以 == 结尾, 请检查是否复制完整"
                )
                else VolcCall.Auth("AccessKey 无效 ($code): ${msg.take(120)}")
            }
            AppLog.i("VOLC", "$action → HTTP ${resp.code} (${body.length}B)")
            return when {
                resp.code == 401 || resp.code == 403 -> VolcCall.Auth("AccessKey 无效 (HTTP ${resp.code})")
                !resp.isSuccessful -> VolcCall.Soft("HTTP ${resp.code}: ${body.take(120)}")
                json != null -> VolcCall.Body(json)
                else -> VolcCall.Soft("响应解析失败: ${body.take(120)}")
            }
        }
    }

    private fun volcResponseError(json: JSONObject): Pair<String, String>? {
        val err = json.optJSONObject("ResponseMetadata")?.optJSONObject("Error")
            ?: json.optJSONObject("Error")
            ?: return null
        val code = err.optString("Code")
        val msg = err.optString("Message")
        return if (code.isBlank() && msg.isBlank()) null else code to msg
    }

    private fun isVolcAuthCode(code: String): Boolean {
        val c = code.lowercase()
        return listOf(
            "auth", "signature", "accessdenied", "denied", "unauthorized",
            "forbidden", "credential", "token", "accesskey"
        ).any { c.contains(it) }
    }

    /** GetAFPUsage → 5小时/周/月窗口 (Quota<=0 视为未订阅, 跳过) */
    private fun parseAfpTiers(result: JSONObject): List<BalanceResult.UsageWindow> {
        val windows = mutableListOf<BalanceResult.UsageWindow>()
        for ((key, label) in listOf(
            "AFPFiveHour" to "5小时", "AFPWeekly" to "周", "AFPMonthly" to "月"
        )) {
            val win = result.optJSONObject(key) ?: continue
            val quota = win.optDouble("Quota", 0.0)
            if (quota <= 0.0) continue
            val used = win.optDouble("Used", 0.0)
            windows += BalanceResult.UsageWindow(
                label = label,
                percent = (used / quota * 100).toInt().coerceIn(0, 100),
                status = "ok",
                resetsAt = parseVolcResetTime(win),
                used = used,
                quota = quota
            )
        }
        return windows
    }

    /** GetCodingPlanUsage → 窗口数组 (Level/Percent/ResetTime, 防御式匹配) */
    private fun parseCodingPlanTiers(result: JSONObject): List<BalanceResult.UsageWindow> {
        val arr = result.optJSONArray("QuotaUsage")
            ?: result.optJSONArray("Usages")
            ?: result.optJSONArray("Details")
            ?: return emptyList()
        val labelMap = mapOf(
            "session" to "5小时", "5h" to "5小时", "fivehour" to "5小时",
            "five_hour" to "5小时", "rolling_5h" to "5小时",
            "weekly" to "周", "week" to "周", "7d" to "周",
            "monthly" to "月", "month" to "月"
        )
        val windows = mutableListOf<BalanceResult.UsageWindow>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val level = item.optString("Level").ifBlank { item.optString("Type") }
                .ifBlank { item.optString("Period") }.ifBlank { item.optString("Label") }
                .ifBlank { item.optString("Window") }.lowercase()
            val label = labelMap[level] ?: continue
            val pct = listOf("Percent", "UsedPercent", "UsagePercent")
                .firstNotNullOfOrNull { f ->
                    item.optDouble(f, Double.NaN).takeIf { v -> !v.isNaN() }
                } ?: 0.0
            windows += BalanceResult.UsageWindow(
                label = label,
                percent = pct.toInt().coerceIn(0, 100),
                status = "ok",
                resetsAt = parseVolcResetTime(item)
            )
        }
        return windows
    }

    /** ResetTime 兼容 秒/毫秒 数字与 ISO 字符串; -1 (窗口未激活) 视为无重置时间 */
    private fun parseVolcResetTime(o: JSONObject): Long? {
        val v = o.opt("ResetTime") ?: return null
        return when (v) {
            is Number -> v.toLong().let { n ->
                (if (n > 10_000_000_000L) n else n * 1000).takeIf { it > 0 }
            }
            is String -> v.toLongOrNull()?.let { n ->
                (if (n > 10_000_000_000L) n else n * 1000).takeIf { it > 0 }
            } ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            else -> null
        }
    }

    private fun sha256Hex(data: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun parseArkError(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").optString("message") }
            .getOrNull()?.take(120) ?: "认证失败"

    // ---------- OpenCode Zen (Go 套餐密钥可查用量百分比, 其余密钥仅验证有效性) ----------
    private fun fetchOpencode(a: Account): BalanceResult {
        try {
            val json = get("${ProviderType.OPENCODE.defaultBaseUrl}/zen/go/v1/usage", a.apiKey)
            val usage = json.optJSONObject("usage")
            if (usage != null) return parseUsageWindows(usage)
        } catch (e: IOException) {
            // 非 Go 套餐密钥: 回退为密钥有效性验证
            get("${ProviderType.OPENCODE.defaultBaseUrl}/zen/v1/models", a.apiKey)
            return BalanceResult.Info("密钥有效 · 套餐用量仅 Go 套餐密钥可查")
        }
        get("${ProviderType.OPENCODE.defaultBaseUrl}/zen/v1/models", a.apiKey)
        return BalanceResult.Info("密钥有效 · 响应未包含用量数据")
    }

    private fun parseUsageWindows(usage: JSONObject): BalanceResult.Usage {
        val labels = linkedMapOf("rolling" to "5小时", "weekly" to "周", "monthly" to "月")
        val windows = labels.entries.mapNotNull { (key, label) ->
            val o = usage.optJSONObject(key) ?: return@mapNotNull null
            val percent = o.optInt("percent", -1)
            if (percent < 0) return@mapNotNull null
            BalanceResult.UsageWindow(
                label = label,
                percent = percent,
                status = o.optString("status").ifBlank { "ok" },
                resetsAt = o.optString("resetsAt").takeIf { it.isNotBlank() }
                    ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            )
        }
        if (windows.isEmpty()) throw IOException("usage 解析失败: ${usage.toString().take(150)}")
        return BalanceResult.Usage(windows)
    }

    // ---------- OpenRouter ----------
    private fun fetchOpenRouter(a: Account): BalanceResult {
        val json = get("${ProviderType.OPENROUTER.defaultBaseUrl}/api/v1/credits", a.apiKey)
        val data = json.optJSONObject("data")
            ?: throw IOException("响应缺少 data: ${json.toString().take(150)}")
        val credits = data.optString("total_credits").toDoubleOrNull()
            ?: throw IOException("total_credits 解析失败")
        val usage = data.optString("total_usage").toDoubleOrNull() ?: 0.0
        return BalanceResult.Success(total = credits - usage, currency = "$")
    }

    // ---------- one-api / new-api 中转 ----------
    private suspend fun fetchOneApi(a: Account): BalanceResult = coroutineScope {
        val base = a.baseUrl.trimEnd('/')
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
        val end = today.plusDays(1).format(DateTimeFormatter.ISO_DATE)
        val subDeferred = async {
            get("$base/v1/dashboard/billing/subscription", a.apiKey)
        }
        val usageDeferred = async {
            get("$base/v1/dashboard/billing/usage?start_date=$start&end_date=$end", a.apiKey)
        }
        val sub = subDeferred.await()
        val usage = usageDeferred.await()
        val hardLimit = sub.optDouble("hard_limit_usd", Double.NaN)
        if (hardLimit.isNaN()) throw IOException("hard_limit_usd 解析失败: ${sub.toString().take(150)}")
        val totalUsage = usage.optDouble("total_usage", 0.0)
        BalanceResult.Success(total = hardLimit - totalUsage / 100.0, currency = "$")
    }

    // ---------- 自定义 ----------
    private fun fetchCustom(a: Account): BalanceResult {
        if (a.customPath.isBlank()) throw IOException("未配置取值路径")
        val json = get(a.baseUrl, a.apiKey)
        val raw = readPath(json, a.customPath)
            ?: throw IOException("路径「${a.customPath}」未命中字段")
        val value = raw.toDoubleOrNull()
            ?: throw IOException("字段值不是数字: ${raw.take(80)}")
        return BalanceResult.Success(total = value, currency = a.customCurrency.ifBlank { "¥" })
    }

    /**
     * 点分路径读取 JSON, 支持 [n] 数组下标, 如 balance_infos[0].total_balance
     */
    private fun readPath(root: JSONObject, path: String): String? {
        val segRegex = Regex("^([A-Za-z0-9_\\-]+)(?:\\[(\\d+)\\])?$")
        var node: Any = root
        for (seg in path.split('.').map { it.trim() }.filter { it.isNotEmpty() }) {
            val m = segRegex.find(seg) ?: return null
            var cur: Any = (node as? JSONObject)?.opt(m.groupValues[1]) ?: return null
            val idx = m.groupValues[2].toIntOrNull()
            if (idx != null) {
                val arr = cur as? JSONArray ?: return null
                if (arr.length() <= idx) return null
                cur = arr.get(idx)
            }
            node = cur
        }
        return when (node) {
            is String -> node
            else -> node.toString()
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is UnknownHostException -> "网络错误: 无法解析主机"
        is SocketTimeoutException -> "网络超时"
        else -> e.message?.take(200) ?: "未知错误"
    }
}
